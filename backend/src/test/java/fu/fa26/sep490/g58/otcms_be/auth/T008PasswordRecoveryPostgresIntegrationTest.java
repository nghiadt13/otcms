package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.dto.ResetPasswordRequest;
import fu.fa26.sep490.g58.otcms_be.auth.entity.*;
import fu.fa26.sep490.g58.otcms_be.auth.port.EmailDeliveryPort;
import fu.fa26.sep490.g58.otcms_be.auth.repository.*;
import fu.fa26.sep490.g58.otcms_be.auth.service.AuthSessionService;
import fu.fa26.sep490.g58.otcms_be.auth.service.PasswordRecoveryService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "app.auth.session-security.jwt-secret=t008-postgres-secret-32-bytes-minimum")
@Testcontainers(disabledWithoutDocker = true)
class T008PasswordRecoveryPostgresIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired PasswordRecoveryService recovery;
    @Autowired AuthSessionService sessionService;
    @Autowired AccountRepository accounts;
    @Autowired AuthSessionRepository sessions;
    @Autowired PasswordResetChallengeRepository challenges;
    @Autowired PasswordEncoder encoder;
    @MockitoBean EmailDeliveryPort email;

    @AfterEach void cleanup() { challenges.deleteAll(); sessions.deleteAll(); accounts.deleteAll(); }

    @Test @Transactional void resetPersistsChallengeConsumesItAndRevokesAllSessions() {
        Account account = new Account();
        account.setNormalizedEmail("t008@example.test");
        account.setAccountType(AccountType.PATIENT);
        account.setStatus(AccountStatus.ACTIVE);
        account.setPasswordHash(encoder.encode("old-password-123"));
        account.setCreatedAt(Instant.now()); account.setUpdatedAt(Instant.now());
        account = accounts.saveAndFlush(account);
        sessionService.create(account.getId(), "127.0.0.1", "t008");
        sessionService.create(account.getId(), "127.0.0.1", "t008");

        recovery.forgot("t008@example.test", AccountType.PATIENT);
        var token = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(email).sendPasswordReset(eq("t008@example.test"), token.capture(), eq(account.getId()));
        PasswordResetChallenge challenge = challenges.findCurrentByAccountIdForUpdate(account.getId()).orElseThrow();
        assertNotEquals(token.getValue(), challenge.getTokenHash());
        assertEquals(0, challenge.getFailedAttempts());

        recovery.reset(new ResetPasswordRequest(token.getValue(), "new-password-123"), AccountType.PATIENT);
        Account updated = accounts.findById(account.getId()).orElseThrow();
        assertTrue(encoder.matches("new-password-123", updated.getPasswordHash()));
        assertNotNull(challenges.findById(challenge.getId()).orElseThrow().getConsumedAt());
        assertTrue(sessions.findAllByAccountIdAndRevokedAtIsNullOrderByCreatedAtAscIdAsc(account.getId()).isEmpty());
    }
}
