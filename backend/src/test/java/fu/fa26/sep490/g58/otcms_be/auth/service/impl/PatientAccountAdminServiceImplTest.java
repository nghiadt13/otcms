package fu.fa26.sep490.g58.otcms_be.auth.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fu.fa26.sep490.g58.otcms_be.auth.exception.PatientAccountAdminException;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.PatientAccountAdminRow;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AccountJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.PatientProfileJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import fu.fa26.sep490.g58.otcms_be.auth.repository.PatientProfileRepository;
import fu.fa26.sep490.g58.otcms_be.auth.service.AuthSessionService;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditEntry;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditLogWriter;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

class PatientAccountAdminServiceImplTest {
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final PatientProfileRepository profiles = mock(PatientProfileRepository.class);
    private final AuthSessionService sessions = mock(AuthSessionService.class);
    private final AuditLogWriter audits = mock(AuditLogWriter.class);
    private final PatientAccountAdminServiceImpl service =
            new PatientAccountAdminServiceImpl(accounts, profiles, sessions, audits);
    private final UUID accountId = UUID.randomUUID();
    private final AuthenticatedAccount admin =
            new AuthenticatedAccount(UUID.randomUUID(), AccountRole.ADMIN, UUID.randomUUID());

    private AccountJpaEntity patient(AccountStatus status) {
        AccountJpaEntity account = new AccountJpaEntity();
        account.setId(accountId);
        account.setAccountType(AccountType.PATIENT);
        account.setStatus(status);
        account.setNormalizedEmail("patient@example.test");
        return account;
    }

    private static Page<PatientAccountAdminRow> emptyPage() {
        return new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
    }

    @Test
    void adminOnlyOperationsRejectMissingOrNonAdminActor() {
        var doctor = new AuthenticatedAccount(UUID.randomUUID(), AccountRole.DOCTOR, UUID.randomUUID());

        assertThrows(AccessDeniedException.class, () -> service.list(null, null, null, 0, 10));
        assertThrows(AccessDeniedException.class, () -> service.detail(doctor, accountId));
        assertThrows(AccessDeniedException.class, () -> service.activate(null, accountId, "corr-1"));
        assertThrows(AccessDeniedException.class, () -> service.deactivate(doctor, accountId, "corr-1"));
        verifyNoInteractions(accounts, profiles, sessions, audits);
    }

    @Test
    void listRejectsOutOfRangePagingOrOverlongQueryBeforeSearching() {
        assertEquals("VALIDATION_FAILED", assertThrows(PatientAccountAdminException.class,
                () -> service.list(admin, null, null, -1, 10)).getMessage());
        assertEquals("VALIDATION_FAILED", assertThrows(PatientAccountAdminException.class,
                () -> service.list(admin, null, null, 0, 0)).getMessage());
        assertEquals("VALIDATION_FAILED", assertThrows(PatientAccountAdminException.class,
                () -> service.list(admin, null, null, 0, 101)).getMessage());
        assertEquals("VALIDATION_FAILED", assertThrows(PatientAccountAdminException.class,
                () -> service.list(admin, "q".repeat(101), null, 0, 10)).getMessage());
        verifyNoInteractions(accounts);
    }

    @Test
    void listRejectsUnknownStatusFilter() {
        assertEquals("VALIDATION_FAILED", assertThrows(PatientAccountAdminException.class,
                () -> service.list(admin, null, "BANNED", 0, 10)).getMessage());
        verifyNoInteractions(accounts);
    }

    @Test
    void listNormalisesStatusCaseAndEscapesLikeWildcardsInQuery() {
        when(accounts.searchPatientAccounts(anyString(), any(), any())).thenReturn(emptyPage());

        service.list(admin, "  A%B_C!  ", "active", 0, 10);

        verify(accounts).searchPatientAccounts(eq("%a!%b!_c!!%"), eq(true), any(Pageable.class));
    }

    @Test
    void listWithoutQueryOrStatusMatchesEverythingWithoutActivityFilter() {
        when(accounts.searchPatientAccounts(anyString(), any(), any())).thenReturn(emptyPage());

        service.list(admin, null, "", 0, 10);

        verify(accounts).searchPatientAccounts(eq("%%"), isNull(), any(Pageable.class));
    }

    @Test
    void listMapsRowsToPatientItemsWithDisplayStatusAndAllowedActions() {
        Instant created = Instant.parse("2026-11-01T00:00:00Z");
        when(accounts.searchPatientAccounts(anyString(), any(), any())).thenReturn(new PageImpl<>(List.of(
                new PatientAccountAdminRow(UUID.randomUUID(), "a@example.test", "A", AccountStatus.ACTIVE, created),
                new PatientAccountAdminRow(UUID.randomUUID(), "d@example.test", "D", AccountStatus.DISABLED, created),
                new PatientAccountAdminRow(UUID.randomUUID(), "r@example.test", "R", AccountStatus.ARCHIVED, created)),
                PageRequest.of(0, 10), 3));

        var result = service.list(admin, null, null, 0, 10);

        assertEquals(3, result.totalItems());
        assertEquals(1, result.totalPages());
        assertEquals("PATIENT", result.items().get(0).accountType());
        assertEquals("ACTIVE", result.items().get(0).status());
        assertEquals(List.of("deactivate"), result.items().get(0).allowedActions());
        assertEquals("INACTIVE", result.items().get(1).status());
        assertEquals(List.of("activate"), result.items().get(1).allowedActions());
        assertEquals("INACTIVE", result.items().get(2).status());
        assertEquals(List.of(), result.items().get(2).allowedActions());
        assertNull(result.items().get(0).staffProfile());
    }

    @Test
    void detailRejectsMissingIdOrNonPatientAccount() {
        assertEquals("PATIENT_ACCOUNT_NOT_FOUND", assertThrows(PatientAccountAdminException.class,
                () -> service.detail(admin, null)).getMessage());
        when(accounts.findByIdAndAccountType(accountId, AccountType.PATIENT)).thenReturn(Optional.empty());
        assertEquals("PATIENT_ACCOUNT_NOT_FOUND", assertThrows(PatientAccountAdminException.class,
                () -> service.detail(admin, accountId)).getMessage());
    }

    @Test
    void detailUsesPatientProfileNameWhenProfileExists() {
        PatientProfileJpaEntity profile = mock(PatientProfileJpaEntity.class);
        when(profile.getFullName()).thenReturn("Profile Name");
        when(accounts.findByIdAndAccountType(accountId, AccountType.PATIENT)).thenReturn(Optional.of(patient(AccountStatus.ACTIVE)));
        when(profiles.findByAccountId(accountId)).thenReturn(Optional.of(profile));

        var response = service.detail(admin, accountId);

        assertEquals("Profile Name", response.fullName());
        assertEquals("ACTIVE", response.status());
        assertEquals(List.of("deactivate"), response.allowedActions());
    }

    @Test
    void detailFallsBackToAccountDisplayNameWhenNoProfileExists() {
        AccountJpaEntity account = patient(AccountStatus.DISABLED);
        account.setDisplayName("Display Name");
        when(accounts.findByIdAndAccountType(accountId, AccountType.PATIENT)).thenReturn(Optional.of(account));
        when(profiles.findByAccountId(accountId)).thenReturn(Optional.empty());

        var response = service.detail(admin, accountId);

        assertEquals("Display Name", response.fullName());
        assertEquals("patient@example.test", response.email());
        assertEquals("INACTIVE", response.status());
        assertEquals(List.of("activate"), response.allowedActions());
    }

    @Test
    void activateReenablesDisabledPatientClearsLockAndRecordsAudit() {
        AccountJpaEntity account = patient(AccountStatus.DISABLED);
        account.setFailedCredentialCount(5);
        account.setLockedUntil(Instant.parse("2026-12-01T00:00:00Z"));
        when(accounts.findByIdForUpdate(accountId)).thenReturn(Optional.of(account));

        var response = service.activate(admin, accountId, "corr-1");

        assertEquals(AccountStatus.ACTIVE, account.getStatus());
        assertEquals(0, account.getFailedCredentialCount());
        assertNull(account.getLockedUntil());
        assertEquals("ACTIVE", response.status());
        verify(accounts).saveAndFlush(account);
        ArgumentCaptor<AuditEntry> audit = ArgumentCaptor.forClass(AuditEntry.class);
        verify(audits).record(audit.capture());
        assertEquals("ACTIVATE", audit.getValue().action());
        assertEquals(accountId, audit.getValue().entityId());
    }

    @Test
    void activateRejectsAlreadyActiveMissingOrNonPatientAccount() {
        when(accounts.findByIdForUpdate(accountId)).thenReturn(Optional.of(patient(AccountStatus.ACTIVE)));
        assertEquals("ACCOUNT_STATUS_UNCHANGED", assertThrows(PatientAccountAdminException.class,
                () -> service.activate(admin, accountId, "corr-1")).getMessage());

        when(accounts.findByIdForUpdate(accountId)).thenReturn(Optional.empty());
        assertEquals("PATIENT_ACCOUNT_NOT_FOUND", assertThrows(PatientAccountAdminException.class,
                () -> service.activate(admin, accountId, "corr-1")).getMessage());

        AccountJpaEntity staff = patient(AccountStatus.DISABLED);
        staff.setAccountType(AccountType.RECEPTIONIST);
        when(accounts.findByIdForUpdate(accountId)).thenReturn(Optional.of(staff));
        assertEquals("PATIENT_ACCOUNT_NOT_FOUND", assertThrows(PatientAccountAdminException.class,
                () -> service.activate(admin, accountId, "corr-1")).getMessage());
        verify(accounts, never()).saveAndFlush(any(AccountJpaEntity.class));
        verifyNoInteractions(audits);
    }

    @Test
    void deactivateDisablesActivePatientRevokesSessionsAndRecordsAudit() {
        AccountJpaEntity account = patient(AccountStatus.ACTIVE);
        when(accounts.findByIdForUpdate(accountId)).thenReturn(Optional.of(account));

        var response = service.deactivate(admin, accountId, "corr-2");

        assertEquals(AccountStatus.DISABLED, account.getStatus());
        assertEquals("INACTIVE", response.status());
        verify(accounts).saveAndFlush(account);
        verify(sessions).revokeAll(accountId, "ACCOUNT_DEACTIVATED");
        ArgumentCaptor<AuditEntry> audit = ArgumentCaptor.forClass(AuditEntry.class);
        verify(audits).record(audit.capture());
        assertEquals("DEACTIVATE", audit.getValue().action());
    }

    @Test
    void deactivateRejectsAccountThatIsNotActive() {
        when(accounts.findByIdForUpdate(accountId)).thenReturn(Optional.of(patient(AccountStatus.PENDING_VERIFICATION)));

        assertEquals("ACCOUNT_STATUS_UNCHANGED", assertThrows(PatientAccountAdminException.class,
                () -> service.deactivate(admin, accountId, "corr-2")).getMessage());
        verify(sessions, never()).revokeAll(any(), any());
        verifyNoInteractions(audits);
    }
}
