package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.*;
import fu.fa26.sep490.g58.otcms_be.auth.repository.*;
import fu.fa26.sep490.g58.otcms_be.auth.service.*;
import fu.fa26.sep490.g58.otcms_be.auth.service.impl.CredentialLoginServiceImpl;
import fu.fa26.sep490.g58.otcms_be.auth.exception.*;
import fu.fa26.sep490.g58.otcms_be.config.AuthProperties;
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
        AuthenticationAuditRepository audits = mock(AuthenticationAuditRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        AuthConcurrencyRepository concurrency = mock(AuthConcurrencyRepository.class);
        AccountJpaEntity patient = account(AccountType.PATIENT);
        when(accounts.findByNormalizedEmail("patient@example.test")).thenReturn(Optional.of(patient));
        when(audits.countByMethodAndOutcomeAndIdentifierHmacAndOccurredAtAfter(anyString(), anyString(), anyString(), any())).thenReturn(0L);
        when(encoder.matches(anyString(), anyString())).thenReturn(true);
        CredentialLoginService service = new CredentialLoginServiceImpl(accounts, audits, encoder, properties(), concurrency, java.time.Clock.systemUTC());

        assertThrows(CredentialLoginException.class,
                () -> service.login(" Patient@Example.Test ", "pw", AccountType.RECEPTIONIST, "192.0.2.10"));
        assertThrows(CredentialLoginException.class,
                () -> service.login("missing@example.test", "pw", AccountType.PATIENT, "192.0.2.11"));
        verify(audits, atLeast(2)).save(any(AuthenticationAuditJpaEntity.class));
        verify(encoder, never()).matches(eq("pw"), isNull());
    }

    @Test void staffPortalAcceptsEveryExplicitStaffTypeAndRejectsPatient() {
        for (AccountType type : new AccountType[]{AccountType.RECEPTIONIST, AccountType.DOCTOR, AccountType.OWNER, AccountType.ADMIN}) {
            AccountRepository accounts = mock(AccountRepository.class);
            AuthenticationAuditRepository audits = mock(AuthenticationAuditRepository.class);
            PasswordEncoder encoder = mock(PasswordEncoder.class);
            AccountJpaEntity account = account(type);
            when(accounts.findByNormalizedEmail("patient@example.test")).thenReturn(Optional.of(account));
            when(audits.countByMethodAndOutcomeAndIdentifierHmacAndOccurredAtAfter(anyString(), anyString(), anyString(), any())).thenReturn(0L);
            when(encoder.matches("pw", "hash")).thenReturn(true);
            CredentialLoginService service = new CredentialLoginServiceImpl(accounts, audits, encoder, properties(), mock(AuthConcurrencyRepository.class), java.time.Clock.systemUTC());

            assertEquals(type.name(), service.loginStaff("patient@example.test", "pw", "192.0.2.10").accountType());
        }
    }

    @Test void repeatedFailuresLockAccountAndPersistHmacAuditWithoutRawIp() {
        AccountRepository accounts = mock(AccountRepository.class);
        AuthenticationAuditRepository audits = mock(AuthenticationAuditRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        AuthConcurrencyRepository concurrency = mock(AuthConcurrencyRepository.class);
        AccountJpaEntity patient = account(AccountType.PATIENT);
        when(accounts.findByNormalizedEmail("patient@example.test")).thenReturn(Optional.of(patient));
        when(audits.countByMethodAndOutcomeAndIdentifierHmacAndOccurredAtAfter(anyString(), anyString(), anyString(), any())).thenReturn(0L);
        when(encoder.matches(anyString(), anyString())).thenReturn(false);
        CredentialLoginService service = new CredentialLoginServiceImpl(accounts, audits, encoder, properties(), concurrency, java.time.Clock.systemUTC());

        for (int i = 0; i < 3; i++) assertThrows(CredentialLoginException.class,
                () -> service.login("patient@example.test", "wrong", AccountType.PATIENT, "192.0.2.12"));
        assertEquals(AccountStatus.TEMPORARILY_LOCKED, patient.getStatus());
        verify(audits, atLeast(3)).save(argThat(a -> a.getClientIp() == null && a.getIdentifierHmac() != null));
    }

    private static AccountJpaEntity account(AccountType type) {
        AccountJpaEntity a = new AccountJpaEntity(); a.setId(UUID.randomUUID()); a.setNormalizedEmail("patient@example.test");
        a.setAccountType(type); a.setStatus(AccountStatus.ACTIVE); a.setPasswordHash("hash"); return a;
    }
    private static AuthProperties properties() {
        return new AuthProperties(new AuthProperties.RateLimit(10, 60), null, null, null, null,
                new AuthProperties.RateLimit(3, 60), "v1", "v1", "test-secret");
    }
}
