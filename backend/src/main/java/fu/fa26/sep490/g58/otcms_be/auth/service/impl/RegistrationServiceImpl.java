package fu.fa26.sep490.g58.otcms_be.auth.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.exception.RegistrationException;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.request.RegistrationRequest;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.response.RegistrationResponse;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AccountJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AuthenticationAuditJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.ConsentEvidenceJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.EmailVerificationChallengeJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.ConsentActorType;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AuthConcurrencyRepository;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AuthenticationAuditRepository;
import fu.fa26.sep490.g58.otcms_be.auth.repository.ConsentEvidenceRepository;
import fu.fa26.sep490.g58.otcms_be.auth.repository.EmailVerificationChallengeRepository;
import fu.fa26.sep490.g58.otcms_be.auth.service.EmailDeliveryService;
import fu.fa26.sep490.g58.otcms_be.auth.service.RegistrationService;
import fu.fa26.sep490.g58.otcms_be.config.AuthProperties;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class RegistrationServiceImpl implements RegistrationService {
    private static final String GENERIC_REGISTRATION_MESSAGE =
            "If registration can be completed, a verification code will be sent.";

    private final AccountRepository accounts;
    private final ConsentEvidenceRepository consents;
    private final EmailVerificationChallengeRepository challenges;
    private final AuthenticationAuditRepository audits;
    private final EmailDeliveryService emailDelivery;
    private final AuthProperties properties;
    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private final SecureRandom random = new SecureRandom();
    private final AuthConcurrencyRepository concurrency;

    public RegistrationServiceImpl(AccountRepository accounts, ConsentEvidenceRepository consents,
                                   EmailVerificationChallengeRepository challenges,
                                   AuthenticationAuditRepository audits, EmailDeliveryService emailDelivery,
                                   AuthProperties properties, AuthConcurrencyRepository concurrency) {
        this.accounts = accounts;
        this.consents = consents;
        this.challenges = challenges;
        this.audits = audits;
        this.emailDelivery = emailDelivery;
        this.properties = properties;
        this.concurrency = concurrency;
    }

    @Override
    @Transactional(noRollbackFor = RegistrationException.class)
    public RegistrationResponse register(RegistrationRequest request, String clientIp) {
        enforceRate(clientIp == null ? "unknown" : clientIp, properties.register());
        String email = normalizeEmail(request.email());
        concurrency.lock("registration-email:" + email);

        AccountJpaEntity existing = accounts.findByNormalizedEmail(email).orElse(null);
        if (existing != null) {
            if (existing.getAccountType() == AccountType.PATIENT
                    && existing.getStatus() == AccountStatus.PENDING_VERIFICATION) {
                resendPendingRegistration(existing);
                recordOutcome(existing, "DUPLICATE_PENDING_SUPPRESSED");
            } else {
                recordOutcome(existing, "DUPLICATE_ACTIVE_SUPPRESSED");
            }
            return genericResponse();
        }

        validate(request);
        Instant now = Instant.now();
        UUID accountId = UUID.randomUUID();
        String passwordHash = passwordEncoder.encode(request.password());
        int inserted = accounts.insertIfEmailAvailable(accountId, email, AccountType.PATIENT.name(),
                AccountStatus.PENDING_VERIFICATION.name(), passwordHash, request.fullName().trim(), now, now);
        if (inserted == 0) {
            accounts.findByNormalizedEmail(email).ifPresent(account -> recordOutcome(account, "DUPLICATE_RACE_SUPPRESSED"));
            return genericResponse();
        }
        AccountJpaEntity account = accounts.findById(accountId)
                .orElseThrow(() -> new IllegalStateException("Newly registered account was not persisted"));

        ConsentEvidenceJpaEntity consent = new ConsentEvidenceJpaEntity();
        consent.setAccount(account);
        consent.setActorType(ConsentActorType.PATIENT);
        consent.setTermsVersion(properties.termsVersion());
        consent.setPrivacyVersion(properties.privacyVersion());
        consent.setAcceptedAt(now);
        consents.save(consent);

        sendNewOtp(account, email, now, "REGISTRATION_EMAIL_DELIVERY_UNAVAILABLE");
        return genericResponse();
    }

    @Override
    @Transactional(noRollbackFor = RegistrationException.class)
    public void verifyEmail(String rawEmail, String otp) {
        AccountJpaEntity account = accounts.findByNormalizedEmail(normalizeEmail(rawEmail))
                .filter(found -> found.getAccountType() == AccountType.PATIENT
                        && found.getStatus() == AccountStatus.PENDING_VERIFICATION)
                .orElseThrow(() -> new RegistrationException("OTP_INVALID_OR_EXPIRED"));
        account = accounts.findByIdForUpdate(account.getId())
                .orElseThrow(() -> new RegistrationException("OTP_INVALID_OR_EXPIRED"));
        EmailVerificationChallengeJpaEntity challenge = challenges.findCurrentForUpdate(account.getId())
                .orElseThrow(() -> new RegistrationException("OTP_INVALID_OR_EXPIRED"));
        Instant now = Instant.now();
        if (challenge.getExpiresAt().isBefore(now) || challenge.getFailedAttempts() >= 5
                || !passwordEncoder.matches(otp, challenge.getOtpHash())) {
            if (challenge.getExpiresAt().isAfter(now) && challenge.getFailedAttempts() < 5)
                challenge.setFailedAttempts(challenge.getFailedAttempts() + 1);
            if (challenge.getFailedAttempts() >= 5) challenge.setSupersededAt(now);
            throw new RegistrationException("OTP_INVALID_OR_EXPIRED");
        }

        boolean consentExists = consents.existsByAccountIdAndActorTypeAndTermsVersionAndPrivacyVersion(
                account.getId(), ConsentActorType.PATIENT, properties.termsVersion(), properties.privacyVersion());
        if (!consentExists) throw new RegistrationException("CONSENT_REQUIRED");
        challenge.setConsumedAt(now);
        account.setStatus(AccountStatus.ACTIVE);
        account.setEmailVerifiedAt(now);
        account.setUpdatedAt(now);
        accounts.save(account);
        challenges.save(challenge);
    }

    @Override
    @Transactional(noRollbackFor = RegistrationException.class)
    public void resendOtp(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        AccountJpaEntity found = accounts.findByNormalizedEmail(email)
                .filter(account -> account.getAccountType() == AccountType.PATIENT
                        && account.getStatus() == AccountStatus.PENDING_VERIFICATION)
                .orElseThrow(() -> new RegistrationException("RATE_LIMIT_EXCEEDED"));
        AccountJpaEntity account = accounts.findByIdForUpdate(found.getId())
                .orElseThrow(() -> new RegistrationException("RATE_LIMIT_EXCEEDED"));
        if (!canResend(account, Instant.now())) throw new RegistrationException("RATE_LIMIT_EXCEEDED");
        issueReplacementOtp(account, email, Instant.now(), "RESEND_EMAIL_DELIVERY_UNAVAILABLE");
    }

    private void validate(RegistrationRequest request) {
        String password = request.password();
        int passwordLength = password.codePointCount(0, password.length());
        boolean hasLetter = password.codePoints().anyMatch(Character::isLetter);
        boolean hasDigit = password.codePoints().anyMatch(Character::isDigit);
        if (!request.patientConsent() || !password.equals(request.confirmPassword())
                || passwordLength < 8 || passwordLength > 64
                || password.getBytes(StandardCharsets.UTF_8).length > 72 || !hasLetter || !hasDigit)
            throw new RegistrationException("VALIDATION_FAILED");

        String name = request.fullName().trim();
        int nameLength = name.codePointCount(0, name.length());
        if (nameLength < 2 || nameLength > 100) throw new RegistrationException("VALIDATION_FAILED");
    }

    private void resendPendingRegistration(AccountJpaEntity account) {
        Instant now = Instant.now();
        String email = account.getNormalizedEmail();
        if (canResend(account, now))
            issueReplacementOtp(account, email, now, "REGISTRATION_EMAIL_DELIVERY_UNAVAILABLE");
        else recordOutcome(account, "DUPLICATE_PENDING_RESEND_RATE_LIMITED");
    }

    private boolean canResend(AccountJpaEntity account, Instant now) {
        return challenges.countByAccountIdAndCreatedAtAfter(account.getId(),
                now.minus(Duration.ofSeconds(properties.resend().windowSeconds()))) < properties.resend().requests();
    }

    private void issueReplacementOtp(AccountJpaEntity account, String email, Instant now, String failureOutcome) {
        challenges.supersedeCurrent(account.getId(), now);
        sendNewOtp(account, email, now, failureOutcome);
    }

    private void sendNewOtp(AccountJpaEntity account, String email, Instant now, String failureOutcome) {
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        EmailVerificationChallengeJpaEntity challenge = new EmailVerificationChallengeJpaEntity();
        challenge.setAccount(account);
        challenge.setOtpHash(passwordEncoder.encode(code));
        challenge.setExpiresAt(now.plus(Duration.ofMinutes(10)));
        challenge.setCreatedAt(now);
        challenge.setFailedAttempts(0);
        challenges.save(challenge);
        try {
            emailDelivery.sendVerificationCode(email, code, account.getId());
        } catch (RuntimeException ex) {
            recordDeliveryOutcome(account, failureOutcome);
            throw new RegistrationException("EMAIL_DELIVERY_UNAVAILABLE");
        }
    }

    private RegistrationResponse genericResponse() {
        return new RegistrationResponse(GENERIC_REGISTRATION_MESSAGE);
    }

    private void enforceRate(String key, AuthProperties.RateLimit limit) {
        concurrency.lock("register:" + key);
        Instant since = Instant.now().minusSeconds(limit.windowSeconds());
        String identifier = hmacIdentifier(key);
        if (audits.countByMethodAndOutcomeAndIdentifierHmacAndOccurredAtAfter(
                "REGISTRATION", "RATE_LIMIT_ATTEMPT", identifier, since) >= limit.requests())
            throw new RegistrationException("RATE_LIMIT_EXCEEDED");
        AuthenticationAuditJpaEntity audit = new AuthenticationAuditJpaEntity();
        audit.setOccurredAt(Instant.now());
        audit.setMethod("REGISTRATION");
        audit.setOutcome("RATE_LIMIT_ATTEMPT");
        audit.setIdentifierHmac(identifier);
        audit.setCorrelationId("registration-rate-limit");
        audit.setClientIp(null);
        audit.setDurationMs(0);
        audits.save(audit);
    }

    private String hmacIdentifier(String value) {
        String secret = properties.rateLimitHmacSecret();
        if (secret == null || secret.isBlank()) throw new RegistrationException("RATE_LIMIT_UNAVAILABLE");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new RegistrationException("RATE_LIMIT_UNAVAILABLE");
        }
    }

    private void recordOutcome(AccountJpaEntity account, String outcome) {
        AuthenticationAuditJpaEntity audit = new AuthenticationAuditJpaEntity();
        audit.setOccurredAt(Instant.now());
        audit.setMethod("REGISTRATION");
        audit.setOutcome(outcome);
        audit.setAccount(account);
        audit.setCorrelationId(UUID.randomUUID().toString());
        audit.setClientIp(null);
        audit.setDurationMs(0);
        audits.save(audit);
    }

    private void recordDeliveryOutcome(AccountJpaEntity account, String outcome) {
        AuthenticationAuditJpaEntity audit = new AuthenticationAuditJpaEntity();
        audit.setOccurredAt(Instant.now());
        audit.setMethod("EMAIL_VERIFICATION");
        audit.setOutcome(outcome);
        audit.setAccount(account);
        audit.setCorrelationId("registration-otp");
        audit.setDurationMs(0);
        audits.save(audit);
    }

    private String normalizeEmail(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }
}
