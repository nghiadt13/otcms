package fu.fa26.sep490.g58.otcms_be.auth.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.exception.SessionException;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.response.SessionTokenResponse;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AccountJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AuthSessionJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AuthConcurrencyRepository;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AuthSessionRepository;
import fu.fa26.sep490.g58.otcms_be.auth.security.JwtTokenService;
import fu.fa26.sep490.g58.otcms_be.auth.service.AuthSessionService;
import fu.fa26.sep490.g58.otcms_be.config.SessionSecurityProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthSessionServiceImpl implements AuthSessionService {
    private final AuthSessionRepository sessions;
    private final AccountRepository accounts;
    private final JwtTokenService jwt;
    private final SessionSecurityProperties properties;
    private final AuthConcurrencyRepository concurrency;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public AuthSessionServiceImpl(AuthSessionRepository sessions, AccountRepository accounts, JwtTokenService jwt,
                                  SessionSecurityProperties properties, AuthConcurrencyRepository concurrency) {
        this(sessions, accounts, jwt, properties, concurrency, Clock.systemUTC());
    }

    AuthSessionServiceImpl(AuthSessionRepository sessions, AccountRepository accounts, JwtTokenService jwt,
                           SessionSecurityProperties properties, AuthConcurrencyRepository concurrency, Clock clock) {
        this.sessions = sessions;
        this.accounts = accounts;
        this.jwt = jwt;
        this.properties = properties;
        this.concurrency = concurrency;
        this.clock = clock;
    }

    @Override
    @Transactional
    public IssuedSession create(UUID accountId, String clientIp, String userAgent) {
        AccountJpaEntity account = accounts.findById(accountId)
                .orElseThrow(() -> new SessionException("AUTHENTICATION_FAILED"));
        return createForAccount(account, clientIp, userAgent);
    }

    private IssuedSession createForAccount(AccountJpaEntity account, String clientIp, String userAgent) {
        lockAccount(account.getId());
        Instant now = Instant.now(clock);
        var active = sessions.findAllByAccountIdAndRevokedAtIsNullOrderByCreatedAtAscIdAsc(account.getId());
        for (int i = 0; i <= active.size() - 5; i++) {
            revoke(active.get(i), now, "SESSION_LIMIT");
        }

        String refreshToken = randomToken();
        AuthSessionJpaEntity session = new AuthSessionJpaEntity();
        session.setAccount(account);
        session.setTokenFamilyId(UUID.randomUUID());
        session.setRefreshTokenHash(hash(refreshToken));
        session.setCreatedAt(now);
        session.setExpiresAt(now.plusSeconds(properties.refreshTokenSeconds()));
        session.setClientIp(clientIp);
        session.setUserAgent(truncate(userAgent, 512));
        sessions.saveAndFlush(session);
        return issued(session, refreshToken);
    }

    @Override
    @Transactional(noRollbackFor = SessionException.class)
    public IssuedSession rotate(String refreshToken) {
        Instant now = Instant.now(clock);
        String hashedToken = hashRequired(refreshToken);
        AuthSessionJpaEntity previous = sessions.findByRefreshTokenHashForUpdate(hashedToken)
                .orElseThrow(() -> new SessionException("AUTHENTICATION_FAILED"));
        if (previous.getRevokedAt() != null) {
            revokeFamily(previous.getTokenFamilyId(), now, "TOKEN_REUSE");
            throw new SessionException("AUTHENTICATION_FAILED");
        }
        if (!previous.getExpiresAt().isAfter(now)
                || previous.getAccount().getStatus() != AccountStatus.ACTIVE) {
            revoke(previous, now, "INVALID_REFRESH");
            throw new SessionException("AUTHENTICATION_FAILED");
        }

        revoke(previous, now, "ROTATED");
        String nextRefreshToken = randomToken();
        AuthSessionJpaEntity next = new AuthSessionJpaEntity();
        next.setAccount(previous.getAccount());
        next.setTokenFamilyId(previous.getTokenFamilyId());
        next.setRefreshTokenHash(hash(nextRefreshToken));
        next.setCreatedAt(now);
        next.setExpiresAt(now.plusSeconds(properties.refreshTokenSeconds()));
        next.setLastRotatedAt(now);
        next.setClientIp(previous.getClientIp());
        next.setUserAgent(previous.getUserAgent());
        sessions.saveAndFlush(next);
        return issued(next, nextRefreshToken);
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        sessions.findByRefreshTokenHashForUpdate(hash(refreshToken))
                .ifPresent(session -> revoke(session, Instant.now(clock), "LOGOUT"));
    }

    @Override
    @Transactional
    public void revokeAll(UUID accountId) {
        sessions.findAllByAccountIdAndRevokedAtIsNullOrderByCreatedAtAscIdAsc(accountId)
                .forEach(session -> revoke(session, Instant.now(clock), "PASSWORD_CHANGED"));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccountType> accountTypeForRefresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return Optional.empty();
        }
        return sessions.findByRefreshTokenHash(hash(refreshToken))
                .map(session -> session.getAccount().getAccountType());
    }

    private IssuedSession issued(AuthSessionJpaEntity session, String refreshToken) {
        AccountJpaEntity account = session.getAccount();
        SessionTokenResponse body = new SessionTokenResponse(
                jwt.issue(account.getId(), account.getAccountType(), session.getId()),
                "Bearer",
                properties.accessTokenSeconds(),
                account.getId(),
                account.getAccountType().name());
        return new IssuedSession(body, refreshToken);
    }

    private void lockAccount(UUID accountId) {
        concurrency.lock("auth-session:" + accountId);
    }

    private void revokeFamily(UUID familyId, Instant now, String reason) {
        for (AuthSessionJpaEntity session : sessions.findAllByTokenFamilyIdAndRevokedAtIsNull(
                familyId, PageRequest.of(0, 1000))) {
            revoke(session, now, reason);
        }
    }

    private void revoke(AuthSessionJpaEntity session, Instant now, String reason) {
        if (session.getRevokedAt() == null) {
            session.setRevokedAt(now);
            session.setRevokeReason(reason);
            sessions.save(session);
        }
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hashRequired(String token) {
        if (token == null || token.isBlank()) {
            throw new SessionException("AUTHENTICATION_FAILED");
        }
        return hash(token);
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String truncate(String value, int maxLength) {
        return value == null ? null : value.substring(0, Math.min(value.length(), maxLength));
    }
}
