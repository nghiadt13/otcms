package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.exception.PatientAccountAdminException;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AccountJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.*;
import fu.fa26.sep490.g58.otcms_be.auth.repository.*;
import fu.fa26.sep490.g58.otcms_be.auth.service.AuthSessionService;
import fu.fa26.sep490.g58.otcms_be.auth.service.impl.PatientAccountAdminServiceImpl;
import fu.fa26.sep490.g58.otcms_be.common.security.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class Kan27PatientManagementAuditTest {
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final PatientProfileRepository profiles = mock(PatientProfileRepository.class);
    private final AuthSessionService sessions = mock(AuthSessionService.class);
    private final AccountManagementAuditRepository audits = mock(AccountManagementAuditRepository.class);
    private final PatientAccountAdminServiceImpl service = new PatientAccountAdminServiceImpl(accounts, profiles, sessions, audits);
    private final AuthenticatedAccount admin = new AuthenticatedAccount(UUID.randomUUID(), AccountRole.ADMIN, null);

    @Test void successfulPatientTransitionsWriteExactSafeEvidenceAndPreserveDto() {
        var patient = patient();
        String correlation = UUID.randomUUID().toString();
        when(accounts.findByIdForUpdate(patient.getId())).thenReturn(Optional.of(patient));
        assertEquals("INACTIVE", service.deactivate(admin, patient.getId(), correlation).status());
        assertEquals("ACTIVE", service.activate(admin, patient.getId(), correlation).status());
        for (String action : List.of("ACTIVATE", "DEACTIVATE")) verify(audits).saveAndFlush(argThat(a ->
                a.getAccountId().equals(patient.getId()) && a.getActorAccountId().equals(admin.accountId())
                        && a.getAction().equals(action) && a.getCorrelationId().equals(correlation) && a.getOccurredAt() != null));
        verify(sessions).revokeAll(patient.getId(), "ACCOUNT_DEACTIVATED");
        assertEquals(AccountType.PATIENT, patient.getAccountType());
    }

    @Test void noOpDeniedAndFailedMutationNeverWriteSuccessfulAudit() {
        var patient = patient();
        when(accounts.findByIdForUpdate(patient.getId())).thenReturn(Optional.of(patient));
        assertThrows(PatientAccountAdminException.class, () -> service.activate(admin, patient.getId(), "noop"));
        assertThrows(AccessDeniedException.class, () -> service.deactivate(null, patient.getId(), "denied"));
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("unavailable"))
                .when(accounts).saveAndFlush(any());
        assertThrows(org.springframework.dao.DataAccessException.class, () -> service.deactivate(admin, patient.getId(), "failed"));
        verifyNoInteractions(audits);
    }

    private AccountJpaEntity patient() {
        var account = new AccountJpaEntity();
        account.setId(UUID.randomUUID()); account.setAccountType(AccountType.PATIENT);
        account.setStatus(AccountStatus.ACTIVE); account.setCreatedAt(Instant.now());
        return account;
    }
}
