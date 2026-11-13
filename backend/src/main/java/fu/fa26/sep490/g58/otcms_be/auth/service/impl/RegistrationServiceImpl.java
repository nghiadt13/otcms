package fu.fa26.sep490.g58.otcms_be.auth.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.exception.RegistrationException;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.request.RegistrationRequest;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.response.RegistrationResponse;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AccountJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.VerificationCodeJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.VerificationPurpose;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.ConsentActorType;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AuthConcurrencyRepository;
import fu.fa26.sep490.g58.otcms_be.auth.repository.VerificationCodeRepository;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditEntry;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditLogWriter;
import fu.fa26.sep490.g58.otcms_be.common.audit.repository.AuditLogRepository;
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
import fu.fa26.sep490.g58.otcms_be.auth.service.EmailOtpService;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class RegistrationServiceImpl implements RegistrationService {
    private static final String GENERIC_REGISTRATION_MESSAGE =
            "If registration can be completed, a verification code will be sent.";

    private final AccountRepository accounts;
    private final VerificationCodeRepository challenges;
    private final AuditLogWriter auditWriter;
    private final AuditLogRepository auditLogs;
    private final EmailDeliveryService emailDelivery;
    private final AuthProperties properties;
    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private final EmailOtpService otp;
    private final AuthConcurrencyRepository concurrency;

    public RegistrationServiceImpl(AccountRepository accounts, VerificationCodeRepository challenges,
                                   AuditLogWriter auditWriter, AuditLogRepository auditLogs,
                                   EmailDeliveryService emailDelivery,
                                   AuthProperties properties, AuthConcurrencyRepository concurrency) {
        this(accounts, challenges, auditWriter, auditLogs, emailDelivery, properties, concurrency,
                new EmailOtpServiceImpl());
    }

    @Autowired
    public RegistrationServiceImpl(AccountRepository accounts, VerificationCodeRepository challenges,
            AuditLogWriter auditWriter, AuditLogRepository auditLogs,
            EmailDeliveryService emailDelivery, AuthProperties properties, AuthConcurrencyRepository concurrency,
            EmailOtpService otp) {
        this.otp = otp;
        this.accounts = accounts;
        this.challenges = challenges;
        this.auditWriter = auditWriter;
        this.auditLogs = auditLogs;
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

        account.setConsentActorType(ConsentActorType.PATIENT);
        account.setTermsVersion(properties.termsVersion());
        account.setPrivacyVersion(properties.privacyVersion());
        account.setConsentAcceptedAt(now);
        auditWriter.record(AuditEntry.builder("account_consents", "CONSENT", "ACCEPT_CONSENT", "ACCOUNT")
                .entityId(account.getId()).visibleInActivityLog(false).occurredAt(now)
                .metadata(Map.of("termsVersion", properties.termsVersion(),
                        "privacyVersion", properties.privacyVersion(),
                        "actorType", ConsentActorType.PATIENT.name()))
                .build());

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
        VerificationCodeJpaEntity challenge = challenges
                .findCurrentForUpdate(account.getId(), VerificationPurpose.EMAIL_VERIFY)
                .orElseThrow(() -> new RegistrationException("OTP_INVALID_OR_EXPIRED"));
        Instant now = Instant.now();
        if (challenge.getExpiresAt().isBefore(now) || challenge.getFailedAttempts() >= 5
                || !this.otp.matches(otp, challenge.getCodeHash())) {
            if (challenge.getExpiresAt().isAfter(now) && challenge.getFailedAttempts() < 5)
                challenge.setFailedAttempts(challenge.getFailedAttempts() + 1);
            if (challenge.getFailedAttempts() >= 5) challenge.setSupersededAt(now);
            throw new RegistrationException("OTP_INVALID_OR_EXPIRED");
        }

        boolean consentOk = account.getConsentActorType() == ConsentActorType.PATIENT
                && properties.termsVersion().equals(account.getTermsVersion())
                && properties.privacyVersion().equals(account.getPrivacyVersion());
        if (!consentOk) throw new RegistrationException("CONSENT_REQUIRED");
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
        if (!PasswordPolicy.isValid(password) || !request.patientConsent()
                || !password.equals(request.confirmPassword()))
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
        return challenges.countByAccountIdAndPurposeAndCreatedAtAfter(account.getId(), VerificationPurpose.EMAIL_VERIFY,
                now.minus(Duration.ofSeconds(properties.resend().windowSeconds()))) < properties.resend().requests();
    }

    private void issueReplacementOtp(AccountJpaEntity account, String email, Instant now, String failureOutcome) {
        challenges.supersedeCurrent(account.getId(), VerificationPurpose.EMAIL_VERIFY, now);
        sendNewOtp(account, email, now, failureOutcome);
    }

    private void sendNewOtp(AccountJpaEntity account, String email, Instant now, String failureOutcome) {
        String code = otp.generate();
        VerificationCodeJpaEntity challenge = new VerificationCodeJpaEntity();
        challenge.setAccountId(account.getId());
        challenge.setPurpose(VerificationPurpose.EMAIL_VERIFY);
        challenge.setCodeHash(otp.hash(code));
        challenge.setExpiresAt(otp.expiresAt(now));
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
        if (auditLogs.countBySourceAndActionAndOutcomeAndIdentifierHmacAndOccurredAtAfter(
                "auth_audits", "REGISTRATION", "RATE_LIMIT_ATTEMPT", identifier, since) >= limit.requests())
            throw new RegistrationException("RATE_LIMIT_EXCEEDED");
        audit("REGISTRATION", "RATE_LIMIT_ATTEMPT", null, identifier);
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
        audit("REGISTRATION", outcome, account.getId(), null);
    }

    private void recordDeliveryOutcome(AccountJpaEntity account, String outcome) {
        audit("EMAIL_VERIFICATION", outcome, account.getId(), null);
    }

    /** Auth evidence is never shown in the activity log; written in the caller's transaction. */
    private void audit(String action, String outcome, UUID accountId, String identifierHmac) {
        auditWriter.record(AuditEntry.builder("auth_audits", "AUTH", action, "AUTH")
                .entityId(accountId).visibleInActivityLog(false).identifierHmac(identifierHmac)
                .outcome(outcome).metadata(Map.of("durationMs", 0)).build());
    }

    private String normalizeEmail(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }
}
