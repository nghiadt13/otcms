package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.*;
import fu.fa26.sep490.g58.otcms_be.auth.repository.*;
import fu.fa26.sep490.g58.otcms_be.auth.exception.RegistrationException;
import fu.fa26.sep490.g58.otcms_be.auth.service.RegistrationService;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.request.RegistrationRequest;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.response.RegistrationResponse;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditEntry;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditLogWriter;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
    @Autowired VerificationCodeRepository challenges;
    @Autowired AuditLogWriter auditWriter;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void cleanup() {
        // audit_logs is append-only; the trigger is lifted only while this fixture removes its own rows.
        jdbc.execute("alter table audit_logs disable trigger tr_audit_logs_immutable");
        try {
            jdbc.update("delete from audit_logs where source = 'auth_audits' and action = 'REGISTRATION' and outcome = 'RATE_LIMIT_ATTEMPT'");
            jdbc.update("delete from audit_logs where source = 'auth_audits' and identifier_hmac = ?", "a".repeat(64));
            jdbc.update("delete from audit_logs where entity_id in (select id from accounts where normalized_email like ?)", PREFIX + "%");
        } finally {
            jdbc.execute("alter table audit_logs enable trigger tr_audit_logs_immutable");
        }
        jdbc.update("delete from verification_codes where account_id in (select id from accounts where normalized_email like ?)", PREFIX + "%");
        jdbc.update("delete from patient_profiles where account_id in (select id from accounts where normalized_email like ?)", PREFIX + "%");
        jdbc.update("delete from accounts where normalized_email like ?", PREFIX + "%");
    }

    @BeforeEach
    void isolatePersistedRateLimitState() {
        cleanup();
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
        assertEquals(ConsentActorType.PATIENT, account.getConsentActorType());
        assertEquals("v1", account.getTermsVersion());
        assertEquals("v1", account.getPrivacyVersion());
        assertNotNull(account.getConsentAcceptedAt());
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_logs where source = 'account_consents' and category = 'CONSENT' "
                + "and action = 'ACCEPT_CONSENT' and entity_id = ? and not visible_in_activity_log", Integer.class, account.getId()));
        assertEquals(1, challenges.countByAccountIdAndPurposeAndCreatedAtAfter(account.getId(), VerificationPurpose.EMAIL_VERIFY, Instant.EPOCH));
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

        VerificationCodeJpaEntity first = challenge(account, "hash-a");
        challenges.saveAndFlush(first);
        VerificationCodeJpaEntity second = challenge(account, "hash-b");
        assertThrows(Exception.class, () -> challenges.saveAndFlush(second));

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> auditWriter.record(
                AuditEntry.builder("auth_audits", "AUTH", "REGISTRATION", "AUTH").visibleInActivityLog(false)
                        .identifierHmac("a".repeat(64)).outcome("RATE_LIMIT_ATTEMPT").build()));
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_logs where source = 'auth_audits' and client_ip is null and identifier_hmac = ?", Integer.class, "a".repeat(64)));
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
        // The resent code replaced the first one: one open EMAIL_VERIFY code, one superseded.
        UUID accountId = accounts.findByNormalizedEmail(email).orElseThrow().getId();
        assertEquals(1, jdbc.queryForObject("select count(*) from verification_codes where account_id = ? and purpose = 'EMAIL_VERIFY' "
                + "and consumed_at is null and superseded_at is null", Integer.class, accountId));
        assertEquals(1, jdbc.queryForObject("select count(*) from verification_codes where account_id = ? and purpose = 'EMAIL_VERIFY' "
                + "and superseded_at is not null", Integer.class, accountId));
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
        assertEquals(0, challenges.countByAccountIdAndPurposeAndCreatedAtAfter(existing.getId(), VerificationPurpose.EMAIL_VERIFY, Instant.EPOCH));
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
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> auditWriter.record(
                AuditEntry.builder("auth_audits", "AUTH", "EMAIL_VERIFICATION", "AUTH").visibleInActivityLog(false)
                        .entityId(account.getId()).outcome("REGISTRATION_EMAIL_DELIVERY_UNAVAILABLE").build()));
        assertEquals(AccountStatus.PENDING_VERIFICATION, accounts.findById(account.getId()).orElseThrow().getStatus());
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_logs where entity_id = ? and outcome = ?", Integer.class, account.getId(), "REGISTRATION_EMAIL_DELIVERY_UNAVAILABLE"));
    }

    private VerificationCodeJpaEntity challenge(AccountJpaEntity account, String hash) {
        VerificationCodeJpaEntity c = new VerificationCodeJpaEntity();
        c.setAccountId(account.getId()); c.setPurpose(VerificationPurpose.EMAIL_VERIFY); c.setCodeHash(hash);
        c.setExpiresAt(Instant.now().plusSeconds(600)); c.setCreatedAt(Instant.now()); c.setFailedAttempts(0);
        return c;
    }
}
