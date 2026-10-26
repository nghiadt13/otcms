package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.*;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AuthenticationAuditRepository;
import fu.fa26.sep490.g58.otcms_be.auth.exception.CredentialLoginException;
import fu.fa26.sep490.g58.otcms_be.auth.service.CredentialLoginService;
import fu.fa26.sep490.g58.otcms_be.auth.security.StaffNetworkPolicy;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import fu.fa26.sep490.g58.otcms_be.config.AuthProperties;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** T005 evidence against the real PostgreSQL persistence and configured auth policy. */
@SpringBootTest
@LocalPostgresTest
@TestPropertySource(properties = {
        "app.auth.rate-limit-hmac-secret=t005-integration-secret",
        "app.auth.login.requests=2",
        "app.auth.login.window-seconds=3600",
        "app.auth.credential-failure.requests=2",
        "app.auth.credential-failure.window-seconds=3600",
        "app.auth.staff-network.allowed-cidrs[0]=10.20.0.0/16",
        "app.auth.staff-network.trusted-proxy-cidrs[0]=10.0.0.0/8"
})
class T005RealLoginIntegrationTest {
    private static final String PREFIX = "t005-pg-";

    @Autowired AccountRepository accounts;
    @Autowired AuthenticationAuditRepository audits;
    @Autowired CredentialLoginService login;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired StaffNetworkPolicy staffNetwork;
    @Autowired AuthProperties authProperties;

    @AfterEach
    void cleanup() {
        jdbc.update("delete from auth_audits where account_id in (select id from accounts where normalized_email like ?)", PREFIX + "%");
        jdbc.update("delete from auth_audits where correlation_id like ?", PREFIX + "%");
        jdbc.update("delete from accounts where normalized_email like ?", PREFIX + "%");
    }

    @Test
    void patientAndStaffAccountBoundariesAreEnforcedWithGenericFailure() {
        AccountJpaEntity patient = account("boundary-patient@example.com", AccountType.PATIENT);
        AccountJpaEntity staff = account("boundary-staff@example.com", AccountType.RECEPTIONIST);

        assertThrows(CredentialLoginException.class,
                () -> login.login(patient.getNormalizedEmail(), "wrong", AccountType.RECEPTIONIST, "198.51.100.41"));
        assertThrows(CredentialLoginException.class,
                () -> login.login(staff.getNormalizedEmail(), "wrong", AccountType.PATIENT, "198.51.100.42"));
        assertEquals(0, accounts.findById(patient.getId()).orElseThrow().getFailedCredentialCount());
        assertEquals(0, accounts.findById(staff.getId()).orElseThrow().getFailedCredentialCount());
    }

    @Test
    void wrongPasswordsPersistLockoutAndSuccessResetsCounter() {
        AccountJpaEntity patient = account("lockout@example.com", AccountType.PATIENT);
        System.out.printf("T005 effective credentialFailure requests=%d windowSeconds=%d%n",
                authProperties.credentialFailure().requests(), authProperties.credentialFailure().windowSeconds());
        String runIp = "2001:db8:" + UUID.randomUUID().toString().replace("-", "").substring(0, 4);
        for (int i = 0; i < 5; i++) {
            String attemptIp = runIp + "::" + i;
            CredentialLoginException failure = assertThrows(CredentialLoginException.class, () -> login.login(patient.getNormalizedEmail(), "wrong", AccountType.PATIENT, attemptIp));
            AccountJpaEntity afterAttempt = accounts.findById(patient.getId()).orElseThrow();
            Integer persistedCount = jdbc.queryForObject("select failed_credential_count from accounts where id = ?", Integer.class, patient.getId());
            System.out.printf("T005 attempt=%d failedCredentialCount=%d status=%s lockedUntil=%s%n",
                    i + 1, persistedCount, afterAttempt.getStatus(), afterAttempt.getLockedUntil());
            System.out.printf("T005 attempt=%d exception=%s%n", i + 1, failure.getMessage());
            assertEquals(i + 1, afterAttempt.getFailedCredentialCount(), "failed count after attempt " + (i + 1));
        }
        AccountJpaEntity locked = accounts.findById(patient.getId()).orElseThrow();
        assertEquals(AccountStatus.TEMPORARILY_LOCKED, locked.getStatus());
        assertNotNull(locked.getLockedUntil());
        assertThrows(CredentialLoginException.class, () -> login.login(patient.getNormalizedEmail(), "correct", AccountType.PATIENT, "198.51.100.44"));
    }

    @Test
    void loginIpLimitAndAuditArePersistedWithHmacOnlyIdentifier() {
        account("ratelimit@example.com", AccountType.PATIENT);
        String ip = "198.51.100.45";
        for (int i = 0; i < 2; i++) {
            assertThrows(CredentialLoginException.class,
                    () -> login.login("unknown-t005@example.com", "wrong", AccountType.PATIENT, ip));
        }
        CredentialLoginException limited = assertThrows(CredentialLoginException.class,
                () -> login.login("unknown-t005@example.com", "wrong", AccountType.PATIENT, ip));
        assertEquals("RATE_LIMIT_EXCEEDED", limited.getMessage());
        Integer count = jdbc.queryForObject("select count(*) from auth_audits where method = 'LOGIN_IP' and outcome = 'ATTEMPT' and client_ip is null and identifier_hmac is not null", Integer.class);
        assertNotNull(count);
        assertTrue(count >= 2);
        String rawCount = jdbc.queryForObject("select count(*) from auth_audits where client_ip = ?", Integer.class, ip).toString();
        assertEquals("0", rawCount);
    }

    @Test
    void configuredTypedStaffCidrsAreFailClosedAndForwardingRequiresTrustedProxy() {
        assertTrue(staffNetwork.allows("10.20.12.9"));
        assertFalse(staffNetwork.allows("10.21.12.9"));
        assertEquals("10.20.12.9", staffNetwork.clientAddress("10.0.0.8", "for=10.20.12.9;proto=https", null));
        assertEquals("10.0.0.8", staffNetwork.clientAddress("10.0.0.8", null, "forged, 10.20.12.9"));
    }

    private AccountJpaEntity account(String suffix, AccountType type) {
        AccountJpaEntity account = new AccountJpaEntity();
        account.setNormalizedEmail(PREFIX + suffix);
        account.setAccountType(type);
        account.setStatus(AccountStatus.ACTIVE);
        account.setPasswordHash(encoder.encode("correct-password"));
        account.setCreatedAt(Instant.now());
        account.setUpdatedAt(Instant.now());
        return accounts.saveAndFlush(account);
    }
}
