package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.model.dto.request.ResetPasswordRequest;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.request.ChangePasswordRequest;
import fu.fa26.sep490.g58.otcms_be.auth.exception.PasswordRecoveryException;
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
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    private UUID fixtureAccountId;

    @AfterEach void cleanup() {
        if (fixtureAccountId == null) return;
        jdbc.update("delete from password_resets where account_id=?", fixtureAccountId);
        jdbc.update("delete from sessions where account_id=?", fixtureAccountId);
        jdbc.update("delete from accounts where id=?", fixtureAccountId);
    }

    @Test void changingPasswordInvalidatesPreviouslyIssuedResetLink() {
        AccountJpaEntity account = new AccountJpaEntity();
        account.setNormalizedEmail("t008-change-" + UUID.randomUUID() + "@example.test");
        account.setAccountType(AccountType.ADMIN);
        account.setStatus(AccountStatus.ACTIVE);
        account.setPasswordHash(encoder.encode("old-password-123"));
        account.setCreatedAt(Instant.now());
        account.setUpdatedAt(Instant.now());
        account = accounts.saveAndFlush(account);
        fixtureAccountId = account.getId();

        recovery.forgot(account.getNormalizedEmail(), AccountType.RECEPTIONIST);
        var token = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(email).sendStaffPasswordReset(eq(account.getNormalizedEmail()), token.capture(), eq(account.getId()));
        verify(email, never()).sendPasswordReset(anyString(), anyString(), any());
        String tokenHash = fu.fa26.sep490.g58.otcms_be.auth.service.impl.PasswordRecoveryServiceImpl.hash(token.getValue());
        assertNull(challenges.findByTokenHash(tokenHash).orElseThrow().getSupersededAt());

        recovery.change(account.getId(), new ChangePasswordRequest("old-password-123", "changed-password-123"));
        assertNotNull(challenges.findByTokenHash(tokenHash).orElseThrow().getSupersededAt());
        var error = assertThrows(PasswordRecoveryException.class,
                () -> recovery.reset(new ResetPasswordRequest(token.getValue(), "reset-password-123"), AccountType.RECEPTIONIST));
        assertEquals("PASSWORD_RESET_TOKEN_INVALID", error.getMessage());
        assertTrue(encoder.matches("changed-password-123", accounts.findById(account.getId()).orElseThrow().getPasswordHash()));
    }

    @Test @Transactional void resetPersistsChallengeConsumesItAndRevokesAllSessions() {
        AccountJpaEntity account = new AccountJpaEntity();
        account.setNormalizedEmail("t008@example.test");
        account.setAccountType(AccountType.PATIENT);
        account.setStatus(AccountStatus.ACTIVE);
        account.setPasswordHash(encoder.encode("old-password-123"));
        account.setCreatedAt(Instant.now()); account.setUpdatedAt(Instant.now());
        account = accounts.saveAndFlush(account);
        fixtureAccountId = account.getId();
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
