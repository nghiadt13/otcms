package fu.fa26.sep490.g58.otcms_be.auth.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.exception.PasswordRecoveryException;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.request.ChangePasswordRequest;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.request.ResetPasswordRequest;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AccountJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.PasswordResetChallengeJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.service.EmailDeliveryService;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import fu.fa26.sep490.g58.otcms_be.auth.repository.PasswordResetChallengeRepository;
import fu.fa26.sep490.g58.otcms_be.auth.service.AuthSessionService;
import fu.fa26.sep490.g58.otcms_be.auth.service.PasswordRecoveryService;
import fu.fa26.sep490.g58.otcms_be.config.AuthProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class PasswordRecoveryServiceImpl implements PasswordRecoveryService {
    private final AccountRepository accounts;
    private final PasswordResetChallengeRepository challenges;
    private final EmailDeliveryService email;
    private final PasswordEncoder encoder;
    private final AuthSessionService sessions;
    private final AuthProperties properties;
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    @Autowired
    public PasswordRecoveryServiceImpl(AccountRepository accounts, PasswordResetChallengeRepository challenges,
                                       EmailDeliveryService email, PasswordEncoder encoder,
                                       AuthSessionService sessions, AuthProperties properties) {
        this(accounts, challenges, email, encoder, sessions, properties, Clock.systemUTC());
    }

    public PasswordRecoveryServiceImpl(AccountRepository accounts, PasswordResetChallengeRepository challenges,
                                       EmailDeliveryService email, PasswordEncoder encoder,
                                       AuthSessionService sessions, AuthProperties properties, Clock clock) {
        this.accounts = accounts;
        this.challenges = challenges;
        this.email = email;
        this.encoder = encoder;
        this.sessions = sessions;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public String forgot(String identifier, AccountType portal) {
        String normalized = normalize(identifier);
        AccountJpaEntity account = accounts.findByNormalizedEmail(normalized).orElse(null);
        if (account == null || !matchesPortal(account.getAccountType(), portal)
                || (account.getStatus() != AccountStatus.ACTIVE
                && account.getStatus() != AccountStatus.TEMPORARILY_LOCKED)
                || account.getPasswordHash() == null) {
            return GENERIC_MESSAGE;
        }

        challenges.findCurrentByAccountIdForUpdate(account.getId()).ifPresent(existing -> {
            existing.setSupersededAt(Instant.now(clock));
            challenges.save(existing);
        });
        String resetToken = token();
        PasswordResetChallengeJpaEntity challenge = new PasswordResetChallengeJpaEntity();
        challenge.setAccount(account);
        challenge.setTokenHash(hash(resetToken));
        challenge.setFailedAttempts(0);
        challenge.setCreatedAt(Instant.now(clock));
        challenge.setExpiresAt(Instant.now(clock).plusSeconds(900));
        challenges.saveAndFlush(challenge);
        try {
            email.sendPasswordReset(normalized, resetToken, account.getId());
        } catch (RuntimeException ignored) {
            // Keep the outward response generic if delivery fails.
        }
        return GENERIC_MESSAGE;
    }

    @Override
    @Transactional
    public void reset(ResetPasswordRequest request, AccountType portal) {
        String tokenHash = hash(request.token());
        // Match reissue's account -> challenge order and read supersession after acquiring the account lock.
        UUID accountId = challenges.findAccountIdByTokenHash(tokenHash)
                .orElseThrow(() -> new PasswordRecoveryException("PASSWORD_RESET_TOKEN_INVALID"));
        AccountJpaEntity account = accounts.findByIdForUpdate(accountId)
                .orElseThrow(() -> new PasswordRecoveryException("PASSWORD_RESET_TOKEN_INVALID"));
        PasswordResetChallengeJpaEntity challenge = challenges.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new PasswordRecoveryException("PASSWORD_RESET_TOKEN_INVALID"));
        Instant now = Instant.now(clock);
        if (challenge.getConsumedAt() != null || challenge.getSupersededAt() != null
                || challenge.getExpiresAt().isBefore(now) || challenge.getFailedAttempts() >= 5
                || !matchesPortal(account.getAccountType(), portal) || account.getPasswordHash() == null
                || (account.getStatus() != AccountStatus.ACTIVE
                && account.getStatus() != AccountStatus.TEMPORARILY_LOCKED)) {
            challenge.setFailedAttempts(challenge.getFailedAttempts() + 1);
            challenges.save(challenge);
            throw new PasswordRecoveryException("PASSWORD_RESET_TOKEN_INVALID");
        }
        if (encoder.matches(request.newPassword(), account.getPasswordHash())) {
            throw new PasswordRecoveryException("PASSWORD_REUSE_NOT_ALLOWED");
        }

        challenge.setConsumedAt(now);
        account.setPasswordHash(encoder.encode(request.newPassword()));
        account.setFailedCredentialCount(0);
        account.setLockedUntil(null);
        account.setStatus(AccountStatus.ACTIVE);
        account.setUpdatedAt(now);
        challenges.save(challenge);
        accounts.save(account);
        sessions.revokeAll(account.getId());
    }

    @Override
    @Transactional
    public void change(UUID accountId, ChangePasswordRequest request) {
        if (!PasswordPolicy.isValid(request.newPassword())) {
            throw new PasswordRecoveryException("VALIDATION_FAILED");
        }
        AccountJpaEntity account = accounts.findByIdForUpdate(accountId)
                .orElseThrow(() -> new PasswordRecoveryException("AUTHENTICATION_FAILED"));
        if (account.getStatus() != AccountStatus.ACTIVE || account.getPasswordHash() == null
                || !encoder.matches(request.currentPassword(), account.getPasswordHash())) {
            throw new PasswordRecoveryException("CURRENT_PASSWORD_INVALID");
        }
        if (encoder.matches(request.newPassword(), account.getPasswordHash())) {
            throw new PasswordRecoveryException("PASSWORD_REUSE_NOT_ALLOWED");
        }

        account.setPasswordHash(encoder.encode(request.newPassword()));
        account.setUpdatedAt(Instant.now(clock));
        account.setMustChangePassword(false);
        accounts.save(account);
        sessions.revokeAll(account.getId());
    }

    private String token() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private boolean matchesPortal(AccountType accountType, AccountType portal) {
        return portal.isStaff() ? accountType.isStaff() : accountType == portal;
    }

    public static String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
