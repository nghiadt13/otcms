package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.*;
import fu.fa26.sep490.g58.otcms_be.auth.repository.*;
import fu.fa26.sep490.g58.otcms_be.auth.service.*;
import fu.fa26.sep490.g58.otcms_be.auth.service.impl.CredentialLoginServiceImpl;
import fu.fa26.sep490.g58.otcms_be.auth.exception.*;
import fu.fa26.sep490.g58.otcms_be.config.AuthProperties;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditEntry;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditLogWriter;
import fu.fa26.sep490.g58.otcms_be.common.audit.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class T005CredentialLoginServiceTest {
    @Test void patientCannotAuthenticateThroughStaffPortalAndUnknownEmailIsGeneric() {
        AccountRepository accounts = mock(AccountRepository.class);
        AuditLogWriter writer = mock(AuditLogWriter.class); AuditLogRepository audits = mock(AuditLogRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        AuthConcurrencyRepository concurrency = mock(AuthConcurrencyRepository.class);
        AccountJpaEntity patient = account(AccountType.PATIENT);
        when(accounts.findByNormalizedEmail("patient@example.test")).thenReturn(Optional.of(patient));
        when(audits.countBySourceAndActionAndOutcomeAndIdentifierHmacAndOccurredAtAfter(anyString(), anyString(), anyString(), anyString(), any())).thenReturn(0L);
        when(encoder.matches(anyString(), anyString())).thenReturn(true);
        CredentialLoginService service = new CredentialLoginServiceImpl(accounts, writer, audits, encoder, properties(), concurrency, java.time.Clock.systemUTC());

        assertThrows(CredentialLoginException.class,
                () -> service.login(" Patient@Example.Test ", "pw", AccountType.RECEPTIONIST, "192.0.2.10"));
        assertThrows(CredentialLoginException.class,
                () -> service.login("missing@example.test", "pw", AccountType.PATIENT, "192.0.2.11"));
        verify(writer, atLeast(2)).record(any(AuditEntry.class));
        verify(encoder, never()).matches(eq("pw"), isNull());
    }

    @Test void staffPortalAcceptsEveryExplicitStaffTypeAndRejectsPatient() {
        for (AccountType type : new AccountType[]{AccountType.RECEPTIONIST, AccountType.DOCTOR, AccountType.OWNER, AccountType.ADMIN}) {
            AccountRepository accounts = mock(AccountRepository.class);
            AuditLogWriter writer = mock(AuditLogWriter.class); AuditLogRepository audits = mock(AuditLogRepository.class);
            PasswordEncoder encoder = mock(PasswordEncoder.class);
            AccountJpaEntity account = account(type);
            when(accounts.findByNormalizedEmail("patient@example.test")).thenReturn(Optional.of(account));
            when(audits.countBySourceAndActionAndOutcomeAndIdentifierHmacAndOccurredAtAfter(anyString(), anyString(), anyString(), anyString(), any())).thenReturn(0L);
            when(encoder.matches("pw", "hash")).thenReturn(true);
            CredentialLoginService service = new CredentialLoginServiceImpl(accounts, writer, audits, encoder, properties(), mock(AuthConcurrencyRepository.class), java.time.Clock.systemUTC());

            assertEquals(type.name(), service.loginStaff("patient@example.test", "pw", "192.0.2.10").accountType());
        }
    }

    @Test void repeatedFailuresLockAccountAndPersistHmacAuditWithoutRawIp() {
        AccountRepository accounts = mock(AccountRepository.class);
        AuditLogWriter writer = mock(AuditLogWriter.class); AuditLogRepository audits = mock(AuditLogRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        AuthConcurrencyRepository concurrency = mock(AuthConcurrencyRepository.class);
        AccountJpaEntity patient = account(AccountType.PATIENT);
        when(accounts.findByNormalizedEmail("patient@example.test")).thenReturn(Optional.of(patient));
        when(audits.countBySourceAndActionAndOutcomeAndIdentifierHmacAndOccurredAtAfter(anyString(), anyString(), anyString(), anyString(), any())).thenReturn(0L);
        when(encoder.matches(anyString(), anyString())).thenReturn(false);
        CredentialLoginService service = new CredentialLoginServiceImpl(accounts, writer, audits, encoder, properties(), concurrency, java.time.Clock.systemUTC());

        for (int i = 0; i < 3; i++) assertThrows(CredentialLoginException.class,
                () -> service.login("patient@example.test", "wrong", AccountType.PATIENT, "192.0.2.12"));
        assertEquals(AccountStatus.TEMPORARILY_LOCKED, patient.getStatus());
        verify(writer, atLeast(3)).record(argThat(a -> a.clientIp() == null && a.identifierHmac() != null));
    }

    @Test void loginStaffWhenUsernameMatchesReturnsStaffSession() {
        AccountRepository accounts = mock(AccountRepository.class);
        AuditLogWriter writer = mock(AuditLogWriter.class); AuditLogRepository audits = mock(AuditLogRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        AccountJpaEntity staff = account(AccountType.RECEPTIONIST);
        when(accounts.findByUsername("staff.user")).thenReturn(Optional.of(staff));
        when(audits.countBySourceAndActionAndOutcomeAndIdentifierHmacAndOccurredAtAfter(anyString(), anyString(), anyString(), anyString(), any())).thenReturn(0L);
        when(encoder.matches("pw", "hash")).thenReturn(true);
        CredentialLoginService service = new CredentialLoginServiceImpl(accounts, writer, audits, encoder, properties(), mock(AuthConcurrencyRepository.class), java.time.Clock.systemUTC());

        var response = service.loginStaff("staff.user", "pw", "192.0.2.13");
        assertEquals(staff.getId(), response.accountId());
        assertEquals("RECEPTIONIST", response.accountType());
        verify(accounts, never()).findByNormalizedEmail(any());
    }

    @Test void loginAdministratorWhenOwnerUsernameMatchesReturnsSession() {
        AccountRepository accounts = mock(AccountRepository.class);
        AuditLogWriter writer = mock(AuditLogWriter.class); AuditLogRepository audits = mock(AuditLogRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        AccountJpaEntity owner = account(AccountType.OWNER);
        when(accounts.findByUsername("owner.user")).thenReturn(Optional.of(owner));
        when(audits.countBySourceAndActionAndOutcomeAndIdentifierHmacAndOccurredAtAfter(anyString(), anyString(), anyString(), anyString(), any())).thenReturn(0L);
        when(encoder.matches("pw", "hash")).thenReturn(true);
        CredentialLoginService service = new CredentialLoginServiceImpl(accounts, writer, audits, encoder, properties(), mock(AuthConcurrencyRepository.class), java.time.Clock.systemUTC());

        var response = service.loginAdministrator("owner.user", "pw", "192.0.2.14");
        assertEquals(owner.getId(), response.accountId());
        assertEquals("OWNER", response.accountType());
    }

    @Test void loginWhenTemporarilyLockedAndLockStillActiveRejectsWithoutCheckingPassword() {
        AccountRepository accounts = mock(AccountRepository.class);
        AuditLogWriter writer = mock(AuditLogWriter.class); AuditLogRepository audits = mock(AuditLogRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        java.time.Instant now = java.time.Instant.parse("2026-11-20T08:00:00Z");
        java.time.Clock clock = java.time.Clock.fixed(now, java.time.ZoneOffset.UTC);
        AccountJpaEntity patient = account(AccountType.PATIENT);
        patient.setStatus(AccountStatus.TEMPORARILY_LOCKED);
        patient.setLockedUntil(now.plusSeconds(300));
        when(accounts.findByNormalizedEmail("patient@example.test")).thenReturn(Optional.of(patient));
        when(audits.countBySourceAndActionAndOutcomeAndIdentifierHmacAndOccurredAtAfter(anyString(), anyString(), anyString(), anyString(), any())).thenReturn(0L);
        CredentialLoginService service = new CredentialLoginServiceImpl(accounts, writer, audits, encoder, properties(), mock(AuthConcurrencyRepository.class), clock);

        assertThrows(CredentialLoginException.class,
                () -> service.login("patient@example.test", "pw", AccountType.PATIENT, "192.0.2.15"));
        assertEquals(AccountStatus.TEMPORARILY_LOCKED, patient.getStatus());
        verify(encoder, never()).matches(any(), any());
    }

    @Test void loginWhenTemporarilyLockedAndLockExpiredUnlocksAccountAndSucceeds() {
        AccountRepository accounts = mock(AccountRepository.class);
        AuditLogWriter writer = mock(AuditLogWriter.class); AuditLogRepository audits = mock(AuditLogRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        java.time.Instant now = java.time.Instant.parse("2026-11-20T08:00:00Z");
        java.time.Clock clock = java.time.Clock.fixed(now, java.time.ZoneOffset.UTC);
        AccountJpaEntity patient = account(AccountType.PATIENT);
        patient.setStatus(AccountStatus.TEMPORARILY_LOCKED);
        patient.setFailedCredentialCount(3);
        patient.setLockedUntil(now.minusSeconds(1));
        when(accounts.findByNormalizedEmail("patient@example.test")).thenReturn(Optional.of(patient));
        when(audits.countBySourceAndActionAndOutcomeAndIdentifierHmacAndOccurredAtAfter(anyString(), anyString(), anyString(), anyString(), any())).thenReturn(0L);
        when(encoder.matches("pw", "hash")).thenReturn(true);
        CredentialLoginService service = new CredentialLoginServiceImpl(accounts, writer, audits, encoder, properties(), mock(AuthConcurrencyRepository.class), clock);

        var response = service.login("patient@example.test", "pw", AccountType.PATIENT, "192.0.2.16");
        assertEquals(patient.getId(), response.accountId());
        assertEquals(AccountStatus.ACTIVE, patient.getStatus());
        assertEquals(0, patient.getFailedCredentialCount());
        assertNull(patient.getLockedUntil());
        verify(accounts).save(patient);
    }

    @Test void loginStaffAndAdminHandleNullUsernameAndNullPasswordWithoutRateLimitError() {
        AccountRepository accounts = mock(AccountRepository.class);
        AuditLogWriter writer = mock(AuditLogWriter.class); AuditLogRepository audits = mock(AuditLogRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        AccountJpaEntity patient = account(AccountType.PATIENT);
        when(accounts.findByNormalizedEmail("patient@example.test")).thenReturn(Optional.of(patient));
        when(audits.countBySourceAndActionAndOutcomeAndIdentifierHmacAndOccurredAtAfter(anyString(), anyString(), anyString(), anyString(), any())).thenReturn(0L);
        CredentialLoginService service = new CredentialLoginServiceImpl(accounts, writer, audits, encoder, properties(), mock(AuthConcurrencyRepository.class), java.time.Clock.systemUTC());

        assertEquals("AUTHENTICATION_FAILED", assertThrows(CredentialLoginException.class,
                () -> service.loginStaff(null, "pw", "192.0.2.17")).getMessage());
        assertEquals("AUTHENTICATION_FAILED", assertThrows(CredentialLoginException.class,
                () -> service.loginAdministrator(null, "pw", "192.0.2.18")).getMessage());
        assertEquals("AUTHENTICATION_FAILED", assertThrows(CredentialLoginException.class,
                () -> service.login("patient@example.test", null, AccountType.PATIENT, "192.0.2.19")).getMessage());
        verify(accounts, never()).findByUsername(null);
        verify(encoder, never()).matches(isNull(), anyString());
    }

    private static AccountJpaEntity account(AccountType type) {
        AccountJpaEntity a = new AccountJpaEntity(); a.setId(UUID.randomUUID()); a.setNormalizedEmail("patient@example.test");
        a.setAccountType(type); a.setStatus(AccountStatus.ACTIVE); a.setPasswordHash("hash"); return a;
    }
    private static AuthProperties properties() {
        return new AuthProperties(new AuthProperties.RateLimit(10, 60), null, null, null,
                new AuthProperties.RateLimit(3, 60), "v1", "v1", "test-secret");
    }
}
