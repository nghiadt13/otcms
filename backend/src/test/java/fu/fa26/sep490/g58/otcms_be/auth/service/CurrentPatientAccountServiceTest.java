package fu.fa26.sep490.g58.otcms_be.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fu.fa26.sep490.g58.otcms_be.auth.exception.CurrentPatientAccountException;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AccountJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory.ProfileRecord;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CurrentPatientAccountServiceTest {
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final PatientProfileDirectory patientProfiles = mock(PatientProfileDirectory.class);
    private final CurrentPatientAccountService service = new CurrentPatientAccountService(accounts, patientProfiles);

    private final UUID accountId = UUID.randomUUID();
    private final AuthenticatedAccount patientActor =
            new AuthenticatedAccount(accountId, AccountRole.PATIENT, UUID.randomUUID());

    private AccountJpaEntity patientAccount(String displayName, String fullName) {
        AccountJpaEntity account = new AccountJpaEntity();
        account.setId(accountId);
        account.setAccountType(AccountType.PATIENT);
        account.setStatus(AccountStatus.ACTIVE);
        account.setNormalizedEmail("patient@example.test");
        account.setDisplayName(displayName);
        account.setFullName(fullName);
        return account;
    }

    @Test
    void getReturnsExplicitDisplayNameWithoutQueryingPatientProfiles() {
        when(accounts.findById(accountId)).thenReturn(Optional.of(patientAccount("Nguyen Van An", "Signup Name")));

        var result = service.get(patientActor);

        assertEquals(accountId, result.id());
        assertEquals("Nguyen Van An", result.displayName());
        assertEquals("patient@example.test", result.email());
        assertEquals("ACTIVE", result.status());
        verifyNoInteractions(patientProfiles);
    }

    @Test
    void getFallsBackToSelfProfileFullNameWhenDisplayNameBlank() {
        UUID profileId = UUID.randomUUID();
        when(accounts.findById(accountId)).thenReturn(Optional.of(patientAccount("   ", "Signup Name")));
        when(patientProfiles.findPatientProfile(accountId)).thenReturn(Optional.of(profileId));
        when(patientProfiles.findProfile(profileId)).thenReturn(Optional.of(new ProfileRecord(
                profileId, accountId, "Self Profile Name", "0912345678", "SELF", LocalDate.of(1995, 1, 1), "MALE")));

        var result = service.get(patientActor);

        assertEquals("Self Profile Name", result.displayName());
    }

    @Test
    void getFallsBackToAccountFullNameOrNullWhenNoProfileExists() {
        when(accounts.findById(accountId)).thenReturn(Optional.of(patientAccount(null, "Registered Name")));
        when(patientProfiles.findPatientProfile(accountId)).thenReturn(Optional.empty());

        assertEquals("Registered Name", service.get(patientActor).displayName());

        when(accounts.findById(accountId)).thenReturn(Optional.of(patientAccount(null, "   ")));

        assertNull(service.get(patientActor).displayName());
    }

    @Test
    void getRejectsMissingAccountNonPatientAndNullActor() {
        assertEquals("FORBIDDEN", assertThrows(CurrentPatientAccountException.class,
                () -> service.get(null)).getMessage());
        assertEquals("FORBIDDEN", assertThrows(CurrentPatientAccountException.class,
                () -> service.get(new AuthenticatedAccount(null, AccountRole.PATIENT, UUID.randomUUID()))).getMessage());
        verifyNoInteractions(accounts);

        when(accounts.findById(accountId)).thenReturn(Optional.empty());
        assertEquals("ACCOUNT_NOT_FOUND", assertThrows(CurrentPatientAccountException.class,
                () -> service.get(patientActor)).getMessage());

        AccountJpaEntity doctor = patientAccount("Dr An", "Dr An");
        doctor.setAccountType(AccountType.DOCTOR);
        when(accounts.findById(accountId)).thenReturn(Optional.of(doctor));
        assertEquals("FORBIDDEN", assertThrows(CurrentPatientAccountException.class,
                () -> service.get(patientActor)).getMessage());
    }

    @Test
    void updateDisplayNameTrimsSavesAndAcceptsBoundaryCodePointLengths() {
        AccountJpaEntity account = patientAccount(null, "Old");
        when(accounts.findById(accountId)).thenReturn(Optional.of(account));
        when(accounts.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var minResult = service.updateDisplayName(patientActor, "  An  ");
        assertEquals("An", minResult.displayName());
        assertEquals("An", account.getDisplayName());

        String hundredEmoji = "\uD83D\uDE00".repeat(100);
        assertEquals(200, hundredEmoji.length());
        var maxResult = service.updateDisplayName(patientActor, hundredEmoji);
        assertEquals(hundredEmoji, maxResult.displayName());
        verify(accounts, org.mockito.Mockito.times(2)).save(account);
    }

    @Test
    void updateDisplayNameRejectsNullBlankShortOrOverlongInputBeforeLookup() {
        for (String bad : new String[]{null, "   ", "A", "a".repeat(101), "\uD83D\uDE00".repeat(101)}) {
            assertEquals("VALIDATION_FAILED", assertThrows(CurrentPatientAccountException.class,
                    () -> service.updateDisplayName(patientActor, bad)).getMessage());
        }
        verifyNoInteractions(accounts, patientProfiles);
    }

    @Test
    void updateDisplayNameRejectsMissingAccountNonPatientAndNullActor() {
        assertEquals("FORBIDDEN", assertThrows(CurrentPatientAccountException.class,
                () -> service.updateDisplayName(null, "Valid Name")).getMessage());
        assertEquals("FORBIDDEN", assertThrows(CurrentPatientAccountException.class,
                () -> service.updateDisplayName(
                        new AuthenticatedAccount(null, AccountRole.PATIENT, UUID.randomUUID()), "Valid Name"))
                .getMessage());

        when(accounts.findById(accountId)).thenReturn(Optional.empty());
        assertEquals("ACCOUNT_NOT_FOUND", assertThrows(CurrentPatientAccountException.class,
                () -> service.updateDisplayName(patientActor, "Valid Name")).getMessage());

        AccountJpaEntity admin = patientAccount("Admin", "Admin");
        admin.setAccountType(AccountType.ADMIN);
        when(accounts.findById(accountId)).thenReturn(Optional.of(admin));
        assertEquals("FORBIDDEN", assertThrows(CurrentPatientAccountException.class,
                () -> service.updateDisplayName(patientActor, "Valid Name")).getMessage());
        verify(accounts, never()).save(any());
    }
}
