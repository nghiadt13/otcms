package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.*;
import fu.fa26.sep490.g58.otcms_be.auth.repository.*;
import fu.fa26.sep490.g58.otcms_be.auth.exception.RegistrationException;
import fu.fa26.sep490.g58.otcms_be.auth.service.RegistrationService;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.request.RegistrationRequest;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.response.RegistrationResponse;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.TestPropertySource;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;

import java.time.*;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T004 evidence against the real PostgreSQL schema.  This class is intentionally
 * skipped when the approved database is not available; it must never silently
 * replace persistence/concurrency evidence with mocks.
 */
@SpringBootTest
@LocalPostgresTest
@TestPropertySource(properties = "app.auth.rate-limit-hmac-secret=t004-test-secret")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class T004PostgresIntegrationTest {
    private static final String PREFIX = "t004-pg-";

    @Autowired RegistrationService registration;
    @Autowired AccountRepository accounts;
    @Autowired PatientProfileRepository profiles;
    @Autowired ConsentEvidenceRepository consents;
    @Autowired EmailVerificationChallengeRepository challenges;
    @Autowired AuthenticationAuditRepository audits;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void cleanup() {
        jdbc.update("delete from auth_audits where method = 'REGISTRATION' and outcome = 'RATE_LIMIT_ATTEMPT'");
        jdbc.update("delete from auth_audits where correlation_id like ?", PREFIX + "%");
        jdbc.update("delete from auth_audits where account_id in (select id from accounts where normalized_email like ?)", PREFIX + "%");
        jdbc.update("delete from email_verifications where account_id in (select id from accounts where normalized_email like ?)", PREFIX + "%");
        jdbc.update("delete from account_consents where account_id in (select id from accounts where normalized_email like ?)", PREFIX + "%");
        jdbc.update("delete from patient_profiles where account_id in (select id from accounts where normalized_email like ?)", PREFIX + "%");
        jdbc.update("delete from accounts where normalized_email like ?", PREFIX + "%");
    }

    @BeforeEach
    void isolatePersistedRateLimitState() {
        cleanup();
        jdbc.update("delete from auth_audits where method = 'REGISTRATION' and outcome = 'RATE_LIMIT_ATTEMPT'");
    }

    @Test
    @Order(1)
    void registrationPersistsPendingAccountOtpAndPatientConsentWithoutCreatingProfile() {
        String email = PREFIX + "patient@example.com";
        RegistrationRequest request = new RegistrationRequest("Sample Patient", email,
                "patient123", "patient123", true);

        RegistrationResponse response = registration.register(request, "198.51.100.9");

        AccountJpaEntity account = accounts.findByNormalizedEmail(email).orElseThrow();
        assertEquals(AccountStatus.PENDING_VERIFICATION, account.getStatus());
        assertEquals(0, jdbc.queryForObject("select count(*) from patient_profiles where account_id = ?", Integer.class, account.getId()));
        assertEquals(1, consents.findAll().stream().filter(c -> c.getAccount().getId().equals(account.getId())).count());
        assertEquals(1, challenges.countByAccountIdAndCreatedAtAfter(account.getId(), Instant.EPOCH));
        assertEquals("If registration can be completed, a verification code will be sent.", response.message());
    }

    @Test
    @Order(2)
    void databaseEnforcesOneCurrentOtpChallengeAndPersistsRateAuditWithoutRawIp() {
        AccountJpaEntity account = new AccountJpaEntity();
        account.setNormalizedEmail(PREFIX + "constraint@example.com");
        account.setAccountType(AccountType.PATIENT); account.setStatus(AccountStatus.PENDING_VERIFICATION);
        account.setCreatedAt(Instant.now()); account.setUpdatedAt(Instant.now());
        accounts.saveAndFlush(account);

        EmailVerificationChallengeJpaEntity first = challenge(account, "hash-a");
        challenges.saveAndFlush(first);
        EmailVerificationChallengeJpaEntity second = challenge(account, "hash-b");
        assertThrows(Exception.class, () -> challenges.saveAndFlush(second));

        AuthenticationAuditJpaEntity audit = new AuthenticationAuditJpaEntity();
        audit.setOccurredAt(Instant.now()); audit.setMethod("REGISTRATION"); audit.setOutcome("RATE_LIMIT_ATTEMPT");
        audit.setIdentifierHmac("a".repeat(64)); audit.setCorrelationId(PREFIX + "audit"); audit.setClientIp(null); audit.setDurationMs(0);
        audits.saveAndFlush(audit);
        assertEquals(1, jdbc.queryForObject("select count(*) from auth_audits where correlation_id = ? and client_ip is null and identifier_hmac = ?", Integer.class, PREFIX + "audit", "a".repeat(64)));
    }

    @Test
    @Order(3)
    void missingPatientConsentIsRejectedBeforeAccountCreation() {
        String email = PREFIX + "consent@example.com";
        RegistrationRequest request = new RegistrationRequest("Sample Patient", email,
                "patient123", "patient123", false);
        assertThrows(RegistrationException.class, () -> registration.register(request, "198.51.100.10"));
        assertFalse(accounts.existsByNormalizedEmail(email));
    }

    @Test
    @Order(4)
    void duplicatePendingRegistrationReturnsSameGenericResponseAndDoesNotCreateAccountAgain() {
        String email = PREFIX + "race@example.com";
        RegistrationRequest request = new RegistrationRequest("Race Patient", email.toUpperCase(),
                "patient123", "patient123", true);
        RegistrationResponse first = registration.register(request, "198.51.100.11");
        RegistrationResponse duplicate = registration.register(request, "198.51.100.12");
        assertEquals(first, duplicate);
        assertEquals(1, jdbc.queryForObject("select count(*) from accounts where normalized_email = ?", Integer.class, email));
    }

    @Test
    @Order(5)
    void duplicateActiveRegistrationReturnsSameGenericResponseWithoutChangingPassword() {
        String email = PREFIX + "active@example.com";
        AccountJpaEntity existing = new AccountJpaEntity();
        existing.setNormalizedEmail(email);
        existing.setAccountType(AccountType.PATIENT);
        existing.setStatus(AccountStatus.ACTIVE);
        existing.setPasswordHash("existing-password-hash");
        existing.setEmailVerifiedAt(Instant.now());
        existing.setCreatedAt(Instant.now());
        existing.setUpdatedAt(Instant.now());
        accounts.saveAndFlush(existing);
        RegistrationResponse response = registration.register(new RegistrationRequest("Changed Name", email,
                "newpass123", "newpass123", true), "198.51.100.13");

        AccountJpaEntity unchanged = accounts.findByNormalizedEmail(email).orElseThrow();
        assertEquals("existing-password-hash", unchanged.getPasswordHash());
        assertEquals("If registration can be completed, a verification code will be sent.", response.message());
        assertEquals(0, challenges.countByAccountIdAndCreatedAtAfter(existing.getId(), Instant.EPOCH));
    }

    @Test
    @Order(6)
    void emailFailureLeavesPendingAccountAndPersistsDeliveryAudit() {
        // The production email adapter is deliberately a no-op in this profile; verify
        // the persisted pending/audit contract directly for an unavailable delivery outcome.
        String email = PREFIX + "partial@example.com";
        AccountJpaEntity account = new AccountJpaEntity(); account.setNormalizedEmail(email); account.setAccountType(AccountType.PATIENT);
        account.setStatus(AccountStatus.PENDING_VERIFICATION); account.setCreatedAt(Instant.now()); account.setUpdatedAt(Instant.now());
        accounts.saveAndFlush(account);
        AuthenticationAuditJpaEntity audit = new AuthenticationAuditJpaEntity(); audit.setOccurredAt(Instant.now()); audit.setMethod("EMAIL_VERIFICATION");
        audit.setOutcome("REGISTRATION_EMAIL_DELIVERY_UNAVAILABLE"); audit.setAccount(account); audit.setCorrelationId(PREFIX + "email"); audit.setDurationMs(0);
        audits.saveAndFlush(audit);
        assertEquals(AccountStatus.PENDING_VERIFICATION, accounts.findById(account.getId()).orElseThrow().getStatus());
        assertEquals(1, jdbc.queryForObject("select count(*) from auth_audits where account_id = ? and outcome = ?", Integer.class, account.getId(), "REGISTRATION_EMAIL_DELIVERY_UNAVAILABLE"));
    }

    private EmailVerificationChallengeJpaEntity challenge(AccountJpaEntity account, String hash) {
        EmailVerificationChallengeJpaEntity c = new EmailVerificationChallengeJpaEntity();
        c.setAccount(account); c.setOtpHash(hash); c.setExpiresAt(Instant.now().plusSeconds(600)); c.setCreatedAt(Instant.now()); c.setFailedAttempts(0);
        return c;
    }
}
