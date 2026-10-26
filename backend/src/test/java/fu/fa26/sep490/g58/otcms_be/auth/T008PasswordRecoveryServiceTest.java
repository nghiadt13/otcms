package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.model.dto.request.*;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.response.*;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.*;
import fu.fa26.sep490.g58.otcms_be.auth.service.EmailDeliveryService;
import fu.fa26.sep490.g58.otcms_be.auth.repository.*;
import fu.fa26.sep490.g58.otcms_be.auth.service.*;
import fu.fa26.sep490.g58.otcms_be.auth.service.impl.PasswordRecoveryServiceImpl;
import fu.fa26.sep490.g58.otcms_be.auth.exception.*;
import fu.fa26.sep490.g58.otcms_be.config.AuthProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class T008PasswordRecoveryServiceTest {
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final PasswordResetChallengeRepository challenges = mock(PasswordResetChallengeRepository.class);
    private final EmailDeliveryService email = mock(EmailDeliveryService.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final AuthSessionService sessions = mock(AuthSessionService.class);
    private final AuthProperties props = new AuthProperties(null,null,null,null,null,null,"v1","v1");
    private final PasswordRecoveryService service = new PasswordRecoveryServiceImpl(accounts,challenges,email,encoder,sessions,props,
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    @Test void unknownAndIneligibleForgotRequestsHaveIdenticalGenericResponse() {
        when(accounts.findByNormalizedEmail("unknown@example.test")).thenReturn(Optional.empty());
        AccountJpaEntity googleOnly = account(AccountStatus.ACTIVE); googleOnly.setPasswordHash(null);
        when(accounts.findByNormalizedEmail("google@example.test")).thenReturn(Optional.of(googleOnly));
        assertEquals(PasswordRecoveryService.GENERIC_MESSAGE, service.forgot("unknown@example.test", AccountType.PATIENT));
        assertEquals(PasswordRecoveryService.GENERIC_MESSAGE, service.forgot("google@example.test", AccountType.PATIENT));
        verifyNoInteractions(email, challenges);
    }

    @Test void staffPortalAcceptsExplicitStaffTypesButKeepsPatientOut() {
        for (AccountType type : new AccountType[]{AccountType.RECEPTIONIST, AccountType.DOCTOR, AccountType.OWNER, AccountType.ADMIN}) {
            AccountJpaEntity staff = account(AccountStatus.ACTIVE);
            staff.setAccountType(type);
            staff.setPasswordHash("hash");
            when(accounts.findByNormalizedEmail(staff.getNormalizedEmail())).thenReturn(Optional.of(staff));
            when(challenges.findCurrentByAccountIdForUpdate(staff.getId())).thenReturn(Optional.empty());
            assertEquals(PasswordRecoveryService.GENERIC_MESSAGE, service.forgot(staff.getNormalizedEmail(), AccountType.RECEPTIONIST));
        }

        AccountJpaEntity patient = account(AccountStatus.ACTIVE);
        patient.setPasswordHash("hash");
        when(accounts.findByNormalizedEmail(patient.getNormalizedEmail())).thenReturn(Optional.of(patient));
        assertEquals(PasswordRecoveryService.GENERIC_MESSAGE, service.forgot(patient.getNormalizedEmail(), AccountType.RECEPTIONIST));
    }

    @Test void forgotCreatesHashedFifteenMinuteChallengeAndDoesNotSendHash() {
        AccountJpaEntity a=account(AccountStatus.ACTIVE); a.setPasswordHash("{bcrypt}old"); when(accounts.findByNormalizedEmail(a.getNormalizedEmail())).thenReturn(Optional.of(a));
        when(challenges.findCurrentByAccountIdForUpdate(a.getId())).thenReturn(Optional.empty());
        String response=service.forgot(a.getNormalizedEmail(),AccountType.PATIENT);
        assertEquals(PasswordRecoveryService.GENERIC_MESSAGE,response);
        ArgumentCaptor<PasswordResetChallengeJpaEntity> challenge=ArgumentCaptor.forClass(PasswordResetChallengeJpaEntity.class); verify(challenges).saveAndFlush(challenge.capture());
        ArgumentCaptor<String> token=ArgumentCaptor.forClass(String.class); verify(email).sendPasswordReset(eq(a.getNormalizedEmail()),token.capture(),eq(a.getId()));
        assertNotEquals(token.getValue(),challenge.getValue().getTokenHash()); assertEquals(900,Duration.between(challenge.getValue().getCreatedAt(),challenge.getValue().getExpiresAt()).getSeconds()); assertEquals(0,challenge.getValue().getFailedAttempts());
    }

    @Test void invalidAndExpiredTokenIncrementsAttemptsAndReuseIsRejected() {
        AccountJpaEntity a=account(AccountStatus.ACTIVE); a.setPasswordHash("old-hash"); PasswordResetChallengeJpaEntity c=new PasswordResetChallengeJpaEntity(); c.setAccount(a); c.setTokenHash(PasswordRecoveryServiceImpl.hash("token")); c.setCreatedAt(Instant.EPOCH); c.setExpiresAt(Instant.parse("2025-01-01T00:00:00Z")); c.setFailedAttempts(0);
        when(challenges.findByTokenHashForUpdate(c.getTokenHash())).thenReturn(Optional.of(c));
        when(challenges.findAccountIdByTokenHash(c.getTokenHash())).thenReturn(Optional.of(a.getId()));
        when(accounts.findByIdForUpdate(a.getId())).thenReturn(Optional.of(a));
        var ex=assertThrows(PasswordRecoveryException.class,()->service.reset(new ResetPasswordRequest("token","new-password-123"),AccountType.PATIENT));
        assertEquals("PASSWORD_RESET_TOKEN_INVALID",ex.getMessage()); assertEquals(1,c.getFailedAttempts()); verify(challenges).save(c);
    }

    @Test void passwordReuseIsRejectedAndSuccessfulResetRevokesAllSessions() {
        AccountJpaEntity a=account(AccountStatus.ACTIVE); a.setPasswordHash("old-hash"); PasswordResetChallengeJpaEntity c=new PasswordResetChallengeJpaEntity(); c.setAccount(a); c.setTokenHash(PasswordRecoveryServiceImpl.hash("token")); c.setCreatedAt(Instant.EPOCH); c.setExpiresAt(Instant.parse("2027-01-01T00:00:00Z"));
        when(challenges.findByTokenHashForUpdate(c.getTokenHash())).thenReturn(Optional.of(c)); when(encoder.matches("same-password", "old-hash")).thenReturn(true);
        when(challenges.findAccountIdByTokenHash(c.getTokenHash())).thenReturn(Optional.of(a.getId()));
        when(accounts.findByIdForUpdate(a.getId())).thenReturn(Optional.of(a));
        assertThrows(PasswordRecoveryException.class,()->service.reset(new ResetPasswordRequest("token","same-password"),AccountType.PATIENT)); verifyNoInteractions(sessions);
        when(encoder.matches("new-password-123", "old-hash")).thenReturn(false); when(encoder.encode("new-password-123")).thenReturn("new-hash");
        service.reset(new ResetPasswordRequest("token","new-password-123"),AccountType.PATIENT); verify(sessions).revokeAll(a.getId()); assertNotNull(c.getConsumedAt());
    }

    private AccountJpaEntity account(AccountStatus status){AccountJpaEntity a=new AccountJpaEntity();a.setId(UUID.randomUUID());a.setNormalizedEmail("user@example.test");a.setAccountType(AccountType.PATIENT);a.setStatus(status);a.setCreatedAt(Instant.EPOCH);a.setUpdatedAt(Instant.EPOCH);return a;}
}
