package fu.fa26.sep490.g58.otcms_be.auth.service;

import fu.fa26.sep490.g58.otcms_be.auth.dto.SessionTokenResponse;
import fu.fa26.sep490.g58.otcms_be.auth.entity.*;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AuthSessionRepository;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import fu.fa26.sep490.g58.otcms_be.auth.security.JwtTokenService;
import fu.fa26.sep490.g58.otcms_be.config.SessionSecurityProperties;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.util.Optional;

@Service
public class AuthSessionService {
    private final AuthSessionRepository sessions;
    private final AccountRepository accounts;
    private final JwtTokenService jwt;
    private final SessionSecurityProperties properties;
    private final EntityManager entityManager;
    private final Clock clock;
    private final SecureRandom random=new SecureRandom();

    @Autowired public AuthSessionService(AuthSessionRepository sessions,AccountRepository accounts,JwtTokenService jwt,SessionSecurityProperties properties,EntityManager entityManager){this(sessions,accounts,jwt,properties,entityManager,Clock.systemUTC());}
    AuthSessionService(AuthSessionRepository sessions,AccountRepository accounts,JwtTokenService jwt,SessionSecurityProperties properties,EntityManager entityManager,Clock clock){this.sessions=sessions;this.accounts=accounts;this.jwt=jwt;this.properties=properties;this.entityManager=entityManager;this.clock=clock;}

    @Transactional
    public IssuedSession create(UUID accountId,String clientIp,String userAgent){return create(accounts.findById(accountId).orElseThrow(()->new SessionException("AUTHENTICATION_FAILED")),clientIp,userAgent);}

    @Transactional
    public IssuedSession create(Account account,String clientIp,String userAgent){
        lockAccount(account.getId());
        Instant now=Instant.now(clock);
        var active=sessions.findAllByAccountIdAndRevokedAtIsNullOrderByCreatedAtAscIdAsc(account.getId());
        for(int i=0;i<=active.size()-5;i++)revoke(active.get(i),now,"SESSION_LIMIT");
        String refresh=randomToken(); AuthSession session=new AuthSession();session.setAccount(account);session.setTokenFamilyId(UUID.randomUUID());session.setRefreshTokenHash(hash(refresh));session.setCreatedAt(now);session.setExpiresAt(now.plusSeconds(properties.refreshTokenSeconds()));session.setClientIp(clientIp);session.setUserAgent(truncate(userAgent,512));sessions.saveAndFlush(session);
        return issued(session,refresh);
    }

    @Transactional(noRollbackFor=SessionException.class)
    public IssuedSession rotate(String refreshToken){
        Instant now=Instant.now(clock);String hashed=hashRequired(refreshToken);AuthSession old=sessions.findByRefreshTokenHashForUpdate(hashed).orElseThrow(()->new SessionException("AUTHENTICATION_FAILED"));
        if(old.getRevokedAt()!=null){revokeFamily(old.getTokenFamilyId(),now,"TOKEN_REUSE");throw new SessionException("AUTHENTICATION_FAILED");}
        if(!old.getExpiresAt().isAfter(now)||old.getAccount().getStatus()!=AccountStatus.ACTIVE){revoke(old,now,"INVALID_REFRESH");throw new SessionException("AUTHENTICATION_FAILED");}
        revoke(old,now,"ROTATED");String refresh=randomToken();AuthSession next=new AuthSession();next.setAccount(old.getAccount());next.setTokenFamilyId(old.getTokenFamilyId());next.setRefreshTokenHash(hash(refresh));next.setCreatedAt(now);next.setExpiresAt(now.plusSeconds(properties.refreshTokenSeconds()));next.setLastRotatedAt(now);next.setClientIp(old.getClientIp());next.setUserAgent(old.getUserAgent());sessions.saveAndFlush(next);return issued(next,refresh);
    }

    @Transactional
    public void logout(String refreshToken){if(refreshToken==null||refreshToken.isBlank())return;sessions.findByRefreshTokenHashForUpdate(hash(refreshToken)).ifPresent(s->revoke(s,Instant.now(clock),"LOGOUT"));}

    @Transactional
    public void revokeAll(UUID accountId) {
        sessions.findAllByAccountIdAndRevokedAtIsNullOrderByCreatedAtAscIdAsc(accountId)
                .forEach(s -> revoke(s, Instant.now(clock), "PASSWORD_CHANGED"));
    }

    @Transactional(readOnly = true)
    public Optional<AccountType> accountTypeForRefresh(String refreshToken){
        if(refreshToken==null||refreshToken.isBlank()) return Optional.empty();
        return sessions.findByRefreshTokenHash(hash(refreshToken)).map(s->s.getAccount().getAccountType());
    }

    private IssuedSession issued(AuthSession session,String refresh){Account a=session.getAccount();return new IssuedSession(new SessionTokenResponse(jwt.issue(a.getId(),a.getAccountType(),session.getId()),"Bearer",properties.accessTokenSeconds(),a.getId(),a.getAccountType().name()),refresh);}
    private void lockAccount(UUID id){entityManager.createNativeQuery("select pg_advisory_xact_lock(hashtext(:key))").setParameter("key","auth-session:"+id).getSingleResult();}
    private void revokeFamily(UUID family,Instant now,String reason){for(AuthSession session:sessions.findAllByTokenFamilyIdAndRevokedAtIsNull(family,PageRequest.of(0,1000)))revoke(session,now,reason);}
    private void revoke(AuthSession session,Instant now,String reason){if(session.getRevokedAt()==null){session.setRevokedAt(now);session.setRevokeReason(reason);sessions.save(session);}}
    private String randomToken(){byte[] bytes=new byte[32];random.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    private static String hashRequired(String token){if(token==null||token.isBlank())throw new SessionException("AUTHENTICATION_FAILED");return hash(token);}
    private static String hash(String token){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException(ex);}}
    private static String truncate(String value,int max){return value==null?null:value.substring(0,Math.min(value.length(),max));}
    public record IssuedSession(SessionTokenResponse body,String refreshToken){}
}
