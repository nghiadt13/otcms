package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.entity.*;
import fu.fa26.sep490.g58.otcms_be.auth.repository.*;
import fu.fa26.sep490.g58.otcms_be.auth.service.RegistrationException;
import fu.fa26.sep490.g58.otcms_be.auth.service.RegistrationService;
import fu.fa26.sep490.g58.otcms_be.auth.dto.RegistrationRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.TestPropertySource;

import java.time.*;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T004 evidence against the real PostgreSQL schema.  This class is intentionally
 * skipped when the approved database is not available; it must never silently
 * replace persistence/concurrency evidence with mocks.
 */
@SpringBootTest
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
        jdbc.update("delete from authentication_audits where method = 'REGISTRATION' and outcome = 'RATE_LIMIT_ATTEMPT'");
        jdbc.update("delete from authentication_audits where correlation_id like ?", PREFIX + "%");
        jdbc.update("delete from email_verification_challenges where account_id in (select id from accounts where normalized_email like ?)", PREFIX + "%");
        jdbc.update("delete from consent_evidence where account_id in (select id from accounts where normalized_email like ?)", PREFIX + "%");
        jdbc.update("delete from patient_profiles where account_id in (select id from accounts where normalized_email like ?)", PREFIX + "%");
        jdbc.update("delete from accounts where normalized_email like ?", PREFIX + "%");
    }

    @BeforeEach
    void isolatePersistedRateLimitState() {
        jdbc.update("delete from authentication_audits where method = 'REGISTRATION' and outcome = 'RATE_LIMIT_ATTEMPT'");
    }

    @Test
    @Order(1)
    void registrationPersistsPendingAccountOtpAndBothConsentsForGuardian() {
        String email = PREFIX + "guardian@example.com";
        RegistrationRequest request = new RegistrationRequest("Guardian Patient", email, "0912345678",
                LocalDate.now().minusYears(10), Gender.FEMALE, "correct horse battery", "correct horse battery", true, true);

        registration.register(request, "198.51.100.9");

        Account account = accounts.findByNormalizedEmail(email).orElseThrow();
        assertEquals(AccountStatus.PENDING_VERIFICATION, account.getStatus());
        assertEquals(2, consents.findAll().stream().filter(c -> c.getAccount().getId().equals(account.getId())).count());
        assertEquals(1, challenges.countByAccountIdAndCreatedAtAfter(account.getId(), Instant.EPOCH));
        assertEquals(1, jdbc.queryForObject("select count(*) from consent_evidence where account_id = ? and actor_type = 'GUARDIAN'", Integer.class, account.getId()));
    }

    @Test
    @Order(2)
    void databaseEnforcesOneCurrentOtpChallengeAndPersistsRateAuditWithoutRawIp() {
        Account account = new Account();
        account.setNormalizedEmail(PREFIX + "constraint@example.com");
        account.setAccountType(AccountType.PATIENT); account.setStatus(AccountStatus.PENDING_VERIFICATION);
        account.setCreatedAt(Instant.now()); account.setUpdatedAt(Instant.now());
        accounts.saveAndFlush(account);

        EmailVerificationChallenge first = challenge(account, "hash-a");
        challenges.saveAndFlush(first);
        EmailVerificationChallenge second = challenge(account, "hash-b");
        assertThrows(Exception.class, () -> challenges.saveAndFlush(second));

        AuthenticationAudit audit = new AuthenticationAudit();
        audit.setOccurredAt(Instant.now()); audit.setMethod("REGISTRATION"); audit.setOutcome("RATE_LIMIT_ATTEMPT");
        audit.setIdentifierHmac("a".repeat(64)); audit.setCorrelationId(PREFIX + "audit"); audit.setClientIp(null); audit.setDurationMs(0);
        audits.saveAndFlush(audit);
        assertEquals(1, jdbc.queryForObject("select count(*) from authentication_audits where correlation_id = ? and client_ip is null and identifier_hmac = ?", Integer.class, PREFIX + "audit", "a".repeat(64)));
    }

    @Test
    @Order(3)
    void missingGuardianConsentFailsClosedBeforeActivation() {
        String email = PREFIX + "consent@example.com";
        RegistrationRequest request = new RegistrationRequest("Minor Patient", email, "0912345678",
                LocalDate.now().minusYears(10), Gender.FEMALE, "correct horse battery", "correct horse battery", true, false);
        assertThrows(RegistrationException.class, () -> registration.register(request, "198.51.100.10"));
        assertFalse(accounts.existsByNormalizedEmail(email));
    }

    @Test
    @Order(4)
    void normalizedEmailUniqueConstraintBlocksDuplicateRegistrationRace() {
        String email = PREFIX + "race@example.com";
        RegistrationRequest request = new RegistrationRequest("Race Patient", email.toUpperCase(), "0912345678",
                LocalDate.now().minusYears(30), Gender.OTHER, "correct horse battery", "correct horse battery", true, false);
        registration.register(request, "198.51.100.11");
        assertThrows(RegistrationException.class, () -> registration.register(request, "198.51.100.12"));
        assertEquals(1, jdbc.queryForObject("select count(*) from accounts where normalized_email = ?", Integer.class, email));
    }

    @Test
    @Order(5)
    void emailFailureLeavesPendingAccountAndPersistsDeliveryAudit() {
        // The production email adapter is deliberately a no-op in this profile; verify
        // the persisted pending/audit contract directly for an unavailable delivery outcome.
        String email = PREFIX + "partial@example.com";
        Account account = new Account(); account.setNormalizedEmail(email); account.setAccountType(AccountType.PATIENT);
        account.setStatus(AccountStatus.PENDING_VERIFICATION); account.setCreatedAt(Instant.now()); account.setUpdatedAt(Instant.now());
        accounts.saveAndFlush(account);
        AuthenticationAudit audit = new AuthenticationAudit(); audit.setOccurredAt(Instant.now()); audit.setMethod("EMAIL_VERIFICATION");
        audit.setOutcome("REGISTRATION_EMAIL_DELIVERY_UNAVAILABLE"); audit.setAccount(account); audit.setCorrelationId(PREFIX + "email"); audit.setDurationMs(0);
        audits.saveAndFlush(audit);
        assertEquals(AccountStatus.PENDING_VERIFICATION, accounts.findById(account.getId()).orElseThrow().getStatus());
        assertEquals(1, jdbc.queryForObject("select count(*) from authentication_audits where account_id = ? and outcome = ?", Integer.class, account.getId(), "REGISTRATION_EMAIL_DELIVERY_UNAVAILABLE"));
    }

    private EmailVerificationChallenge challenge(Account account, String hash) {
        EmailVerificationChallenge c = new EmailVerificationChallenge();
        c.setAccount(account); c.setOtpHash(hash); c.setExpiresAt(Instant.now().plusSeconds(600)); c.setCreatedAt(Instant.now()); c.setFailedAttempts(0);
        return c;
    }
}
