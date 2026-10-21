package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.model.dto.request.ResetPasswordRequest;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.*;
import fu.fa26.sep490.g58.otcms_be.auth.service.EmailDeliveryService;
import fu.fa26.sep490.g58.otcms_be.auth.repository.*;
import fu.fa26.sep490.g58.otcms_be.auth.service.AuthSessionService;
import fu.fa26.sep490.g58.otcms_be.auth.service.PasswordRecoveryService;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "app.auth.session-security.jwt-secret=t008-postgres-secret-32-bytes-minimum")
@LocalPostgresTest
class T008PasswordRecoveryPostgresIntegrationTest {
    @Autowired PasswordRecoveryService recovery;
    @Autowired AuthSessionService sessionService;
    @Autowired AccountRepository accounts;
    @Autowired AuthSessionRepository sessions;
    @Autowired PasswordResetChallengeRepository challenges;
    @Autowired PasswordEncoder encoder;
    @MockitoBean EmailDeliveryService email;

    @AfterEach void cleanup() { challenges.deleteAll(); sessions.deleteAll(); accounts.deleteAll(); }

    @Test @Transactional void resetPersistsChallengeConsumesItAndRevokesAllSessions() {
        AccountJpaEntity account = new AccountJpaEntity();
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
        PasswordResetChallengeJpaEntity challenge = challenges.findCurrentByAccountIdForUpdate(account.getId()).orElseThrow();
        assertNotEquals(token.getValue(), challenge.getTokenHash());
        assertEquals(0, challenge.getFailedAttempts());

        recovery.reset(new ResetPasswordRequest(token.getValue(), "new-password-123"), AccountType.PATIENT);
        AccountJpaEntity updated = accounts.findById(account.getId()).orElseThrow();
        assertTrue(encoder.matches("new-password-123", updated.getPasswordHash()));
        assertNotNull(challenges.findById(challenge.getId()).orElseThrow().getConsumedAt());
        assertTrue(sessions.findAllByAccountIdAndRevokedAtIsNullOrderByCreatedAtAscIdAsc(account.getId()).isEmpty());
    }
}
