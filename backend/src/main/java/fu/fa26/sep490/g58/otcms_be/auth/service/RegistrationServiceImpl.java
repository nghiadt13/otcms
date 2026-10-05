package fu.fa26.sep490.g58.otcms_be.auth.service;

import fu.fa26.sep490.g58.otcms_be.auth.dto.RegistrationRequest;
import fu.fa26.sep490.g58.otcms_be.auth.dto.RegistrationResponse;
import fu.fa26.sep490.g58.otcms_be.auth.entity.*;
import fu.fa26.sep490.g58.otcms_be.auth.port.EmailDeliveryPort;
import fu.fa26.sep490.g58.otcms_be.auth.repository.*;
import fu.fa26.sep490.g58.otcms_be.config.AuthProperties;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.HexFormat;
import java.time.*;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import fu.fa26.sep490.g58.otcms_be.auth.entity.AuthenticationAudit;

@Service
public class RegistrationServiceImpl implements RegistrationService {
    private final AccountRepository accounts;
    private final PatientProfileRepository profiles;
    private final ConsentEvidenceRepository consents;
    private final EmailVerificationChallengeRepository challenges;
    private final AuthenticationAuditRepository audits;
    private final EmailDeliveryPort emailDelivery;
    private final AuthProperties properties;
    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private final SecureRandom random = new SecureRandom();
    private final EntityManager entityManager;

    public RegistrationServiceImpl(AccountRepository accounts, PatientProfileRepository profiles,
                                   ConsentEvidenceRepository consents, EmailVerificationChallengeRepository challenges,
                                   AuthenticationAuditRepository audits,
                                   EmailDeliveryPort emailDelivery, AuthProperties properties, EntityManager entityManager) {
        this.accounts = accounts;
        this.profiles = profiles;
        this.consents = consents;
        this.challenges = challenges;
        this.audits = audits;
        this.emailDelivery = emailDelivery;
        this.properties = properties;
        this.entityManager = entityManager;
    }

    /**
     * @ears .sdd/features/feat-001-auth-login/SPEC.md#REQ-AUTH-001
     */
    @Override
    @Transactional(noRollbackFor = RegistrationException.class)
    public RegistrationResponse register(RegistrationRequest request, String clientIp) {
        enforceRate(clientIp == null ? "unknown" : clientIp, properties.register());
        String email = request.email().trim().toLowerCase(java.util.Locale.ROOT);
        if (accounts.existsByNormalizedEmail(email)) throw new RegistrationException("EMAIL_ALREADY_REGISTERED");
        String password = request.password();
        if (!password.equals(request.confirmPassword()) || password.length() < 12 || password.length() > 64
                || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new RegistrationException("VALIDATION_FAILED");
        String name = request.fullName().trim();
        if (name.codePointCount(0, name.length()) < 2 || name.codePointCount(0, name.length()) > 100
                || request.dateOfBirth().isAfter(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))))
            throw new RegistrationException("VALIDATION_FAILED");
        String phone = normalizePhone(request.phone());
        if (phone == null) throw new RegistrationException("VALIDATION_FAILED");
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        int age = Period.between(request.dateOfBirth(), today).getYears();
        boolean guardian = age < 18 || age > 70;
        if (!request.patientConsent() || (guardian && !request.guardianConsent()))
            throw new RegistrationException("CONSENT_REQUIRED");
        Instant now = Instant.now();
        Account account = new Account();
        account.setNormalizedEmail(email);
        account.setAccountType(AccountType.PATIENT);
        account.setStatus(AccountStatus.PENDING_VERIFICATION);
        account.setPasswordHash(passwordEncoder.encode(password));
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        account.setFailedCredentialCount(0);
        try {
            accounts.saveAndFlush(account);
        } catch (DataIntegrityViolationException ex) {
            throw new RegistrationException("EMAIL_ALREADY_REGISTERED");
        }
        PatientProfile profile = new PatientProfile();
        // @MapsId derives the profile primary key from the associated account.
        // Leave the id unset so Spring Data uses persist (not merge) for this new
        // derived-identity entity; setting it manually makes the entity appear
        // detached and Hibernate attempts to merge a profile with a null id.
        profile.setAccount(account);
        profile.setFullName(name);
        profile.setPhoneCanonical(phone);
        profile.setPhoneOwnerType(guardian ? PhoneOwnerType.GUARDIAN : PhoneOwnerType.PATIENT);
        profile.setDateOfBirth(request.dateOfBirth());
        profile.setGender(request.gender());
        profiles.save(profile);
        ConsentEvidence patientConsent = new ConsentEvidence();
        patientConsent.setAccount(account);
        patientConsent.setActorType(ConsentActorType.PATIENT);
        patientConsent.setTermsVersion(properties.termsVersion());
        patientConsent.setPrivacyVersion(properties.privacyVersion());
        patientConsent.setAcceptedAt(now);
        consents.save(patientConsent);
        if (guardian) {
            ConsentEvidence guardianConsent = new ConsentEvidence();
            guardianConsent.setAccount(account);
            guardianConsent.setActorType(ConsentActorType.GUARDIAN);
            guardianConsent.setTermsVersion(properties.termsVersion());
            guardianConsent.setPrivacyVersion(properties.privacyVersion());
            guardianConsent.setAcceptedAt(now);
            consents.save(guardianConsent);
        }
        String code = String.format("%06d", random.nextInt(1_000_000));
        EmailVerificationChallenge challenge = new EmailVerificationChallenge();
        challenge.setAccount(account);
        challenge.setOtpHash(passwordEncoder.encode(code));
        challenge.setExpiresAt(now.plus(Duration.ofMinutes(10)));
        challenge.setCreatedAt(now);
        challenge.setFailedAttempts(0);
        challenges.save(challenge);
        try {
            emailDelivery.sendVerificationCode(email, code, account.getId());
        } catch (RuntimeException ex) {
            recordDeliveryOutcome(account, "REGISTRATION_EMAIL_DELIVERY_UNAVAILABLE");
            throw new RegistrationException("EMAIL_DELIVERY_UNAVAILABLE");
        }
        return new RegistrationResponse(account.getId(), account.getStatus().name(), "Verification code sent");
    }

    @Override
    @Transactional(noRollbackFor = RegistrationException.class)
    public void verifyEmail(String rawEmail, String otp) {
        Account account = accounts.findByNormalizedEmail(normalizeEmail(rawEmail)).orElseThrow(() -> new RegistrationException("OTP_INVALID_OR_EXPIRED"));
        account = accounts.findByIdForUpdate(account.getId()).orElseThrow(() -> new RegistrationException("OTP_INVALID_OR_EXPIRED"));
        EmailVerificationChallenge challenge = challenges.findCurrentForUpdate(account.getId()).orElseThrow(() -> new RegistrationException("OTP_INVALID_OR_EXPIRED"));
        Instant now = Instant.now();
        if (challenge.getExpiresAt().isBefore(now) || challenge.getFailedAttempts() >= 5 || !passwordEncoder.matches(otp, challenge.getOtpHash())) {
            if (challenge.getExpiresAt().isAfter(now) && challenge.getFailedAttempts() < 5)
                challenge.setFailedAttempts(challenge.getFailedAttempts() + 1);
            if (challenge.getFailedAttempts() >= 5) challenge.setSupersededAt(now);
            throw new RegistrationException("OTP_INVALID_OR_EXPIRED");
        }
        boolean guardianRequired = profiles.findById(account.getId()).map(p -> p.getPhoneOwnerType() == PhoneOwnerType.GUARDIAN).orElse(true);
        boolean consentOk = consents.existsByAccountIdAndActorTypeAndTermsVersionAndPrivacyVersion(account.getId(), ConsentActorType.PATIENT, properties.termsVersion(), properties.privacyVersion())
                && (!guardianRequired || consents.existsByAccountIdAndActorTypeAndTermsVersionAndPrivacyVersion(account.getId(), ConsentActorType.GUARDIAN, properties.termsVersion(), properties.privacyVersion()));
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
        Account found = accounts.findByNormalizedEmail(email).orElseThrow(() -> new RegistrationException("RATE_LIMIT_EXCEEDED"));
        Account account = accounts.findByIdForUpdate(found.getId()).orElseThrow(() -> new RegistrationException("RATE_LIMIT_EXCEEDED"));
        if (account.getStatus() != AccountStatus.PENDING_VERIFICATION)
            throw new RegistrationException("RATE_LIMIT_EXCEEDED");
        Instant now = Instant.now();
        if (challenges.countByAccountIdAndCreatedAtAfter(account.getId(), now.minus(Duration.ofSeconds(properties.resend().windowSeconds()))) >= properties.resend().requests())
            throw new RegistrationException("RATE_LIMIT_EXCEEDED");
        challenges.supersedeCurrent(account.getId(), now);
        String code = String.format("%06d", random.nextInt(1_000_000));
        EmailVerificationChallenge challenge = new EmailVerificationChallenge();
        challenge.setAccount(account);
        challenge.setOtpHash(passwordEncoder.encode(code));
        challenge.setExpiresAt(now.plus(Duration.ofMinutes(10)));
        challenge.setCreatedAt(now);
        challenge.setFailedAttempts(0);
        challenges.save(challenge);
        try {
            emailDelivery.sendVerificationCode(email, code, account.getId());
        } catch (RuntimeException ex) {
            recordDeliveryOutcome(account, "RESEND_EMAIL_DELIVERY_UNAVAILABLE");
            throw new RegistrationException("EMAIL_DELIVERY_UNAVAILABLE");
        }
    }

    private String normalizeEmail(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private void enforceRate(String key, AuthProperties.RateLimit limit) {
        entityManager.createNativeQuery("select pg_advisory_xact_lock(hashtext(:key))").setParameter("key", "register:" + key).getSingleResult();
        Instant since = Instant.now().minusSeconds(limit.windowSeconds());
        String identifier = hmacIdentifier(key);
        String outcome = "RATE_LIMIT_ATTEMPT";
        if (audits.countByMethodAndOutcomeAndIdentifierHmacAndOccurredAtAfter("REGISTRATION", outcome, identifier, since) >= limit.requests())
            throw new RegistrationException("RATE_LIMIT_EXCEEDED");
        AuthenticationAudit audit = new AuthenticationAudit();
        audit.setOccurredAt(Instant.now());
        audit.setMethod("REGISTRATION");
        audit.setOutcome(outcome);
        audit.setIdentifierHmac(identifier);
        audit.setCorrelationId("registration-rate-limit");
        audit.setClientIp(null);
        audit.setDurationMs(0);
        audits.save(audit);
    }

    private String hmacIdentifier(String value) {
        String secret = properties.rateLimitHmacSecret();
        if (secret == null || secret.isBlank())
            throw new RegistrationException("RATE_LIMIT_UNAVAILABLE");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new RegistrationException("RATE_LIMIT_UNAVAILABLE");
        }
    }

    private void recordDeliveryOutcome(Account account, String outcome) {
        AuthenticationAudit audit = new AuthenticationAudit();
        audit.setOccurredAt(Instant.now());
        audit.setMethod("EMAIL_VERIFICATION");
        audit.setOutcome(outcome);
        audit.setAccount(account);
        audit.setCorrelationId("registration-otp");
        audit.setDurationMs(0);
        audits.save(audit);
    }

    private String normalizePhone(String raw) {
        String value = raw.trim().replaceAll("[\\s().-]", "");
        if (value.startsWith("00")) value = "+" + value.substring(2);
        if (value.startsWith("0")) value = "+84" + value.substring(1);
        if (!value.startsWith("+")) value = "+" + value;
        String digits = value.substring(1);
        return digits.matches("[0-9]{8,15}") ? "+" + digits : null;
    }
}
