package fu.fa26.sep490.g58.otcms_be.auth.service;

import fu.fa26.sep490.g58.otcms_be.auth.dto.*;
import fu.fa26.sep490.g58.otcms_be.auth.entity.*;
import fu.fa26.sep490.g58.otcms_be.auth.port.EmailDeliveryPort;
import fu.fa26.sep490.g58.otcms_be.auth.repository.*;
import fu.fa26.sep490.g58.otcms_be.config.AuthProperties;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service
public class PasswordRecoveryService {
    public static final String GENERIC_MESSAGE = "If the account is eligible, password reset instructions will be sent.";
    private final AccountRepository accounts; private final PasswordResetChallengeRepository challenges;
    private final EmailDeliveryPort email; private final PasswordEncoder encoder; private final AuthSessionService sessions;
    private final AuthProperties props; private final SecureRandom random = new SecureRandom(); private final Clock clock;
    @Autowired public PasswordRecoveryService(AccountRepository accounts, PasswordResetChallengeRepository challenges, EmailDeliveryPort email,
                                   PasswordEncoder encoder, AuthSessionService sessions, AuthProperties props) {
        this(accounts,challenges,email,encoder,sessions,props,Clock.systemUTC());
    }
    public PasswordRecoveryService(AccountRepository a, PasswordResetChallengeRepository c, EmailDeliveryPort e, PasswordEncoder p,
                            AuthSessionService s, AuthProperties props, Clock clock) { accounts=a; challenges=c; email=e; encoder=p; sessions=s; this.props=props; this.clock=clock; }

    @Transactional
    public String forgot(String identifier, AccountType portal) {
        String normalized = normalize(identifier); Account a = accounts.findByNormalizedEmail(normalized).orElse(null);
        if (a == null || a.getAccountType()!=portal || (a.getStatus()!=AccountStatus.ACTIVE && a.getStatus()!=AccountStatus.TEMPORARILY_LOCKED) || a.getPasswordHash()==null) return GENERIC_MESSAGE;
        challenges.findCurrentByAccountIdForUpdate(a.getId()).ifPresent(old -> { old.setSupersededAt(Instant.now(clock)); challenges.save(old); });
        String token = token(); PasswordResetChallenge c = new PasswordResetChallenge(); c.setAccount(a); c.setTokenHash(hash(token)); c.setFailedAttempts(0); c.setCreatedAt(Instant.now(clock)); c.setExpiresAt(Instant.now(clock).plusSeconds(900)); challenges.saveAndFlush(c);
        try { email.sendPasswordReset(normalized, token, a.getId()); } catch (RuntimeException ignored) { }
        return GENERIC_MESSAGE;
    }

    @Transactional
    public void reset(ResetPasswordRequest request, AccountType portal) {
        PasswordResetChallenge c = challenges.findByTokenHashForUpdate(hash(request.token())).orElseThrow(() -> new PasswordRecoveryException("PASSWORD_RESET_TOKEN_INVALID"));
        Instant now=Instant.now(clock); Account a=c.getAccount();
        if(c.getConsumedAt()!=null||c.getSupersededAt()!=null||c.getExpiresAt().isBefore(now)||c.getFailedAttempts()>=5||a.getAccountType()!=portal||a.getPasswordHash()==null){c.setFailedAttempts(c.getFailedAttempts()+1);challenges.save(c);throw new PasswordRecoveryException("PASSWORD_RESET_TOKEN_INVALID");}
        if(encoder.matches(request.newPassword(),a.getPasswordHash())) throw new PasswordRecoveryException("PASSWORD_REUSE_NOT_ALLOWED");
        c.setConsumedAt(now); a.setPasswordHash(encoder.encode(request.newPassword())); a.setFailedCredentialCount(0); a.setLockedUntil(null); a.setStatus(AccountStatus.ACTIVE); a.setUpdatedAt(now); challenges.save(c); accounts.save(a); sessions.revokeAll(a.getId());
    }

    @Transactional
    public void change(UUID accountId, ChangePasswordRequest request) {
        Account a=accounts.findByIdForUpdate(accountId).orElseThrow(() -> new PasswordRecoveryException("AUTHENTICATION_FAILED"));
        if(a.getStatus()!=AccountStatus.ACTIVE||a.getPasswordHash()==null||!encoder.matches(request.currentPassword(),a.getPasswordHash())) throw new PasswordRecoveryException("CURRENT_PASSWORD_INVALID");
        if(encoder.matches(request.newPassword(),a.getPasswordHash())) throw new PasswordRecoveryException("PASSWORD_REUSE_NOT_ALLOWED");
        a.setPasswordHash(encoder.encode(request.newPassword())); a.setUpdatedAt(Instant.now(clock)); accounts.save(a); sessions.revokeAll(a.getId());
    }
    private String token(){byte[] b=new byte[32];random.nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
    public static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private static String normalize(String value){return value==null?"":value.trim().toLowerCase(Locale.ROOT);}
}
