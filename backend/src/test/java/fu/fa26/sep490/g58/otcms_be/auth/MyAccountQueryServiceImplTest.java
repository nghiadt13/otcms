package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AccountJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.PatientProfileJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import fu.fa26.sep490.g58.otcms_be.auth.repository.PatientProfileRepository;
import fu.fa26.sep490.g58.otcms_be.auth.service.impl.MyAccountQueryServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MyAccountQueryServiceImplTest {
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final PatientProfileRepository profiles = mock(PatientProfileRepository.class);
    private final MyAccountQueryServiceImpl service = new MyAccountQueryServiceImpl(accounts, profiles);

    private AccountJpaEntity account(UUID id, String email, AccountType type) {
        AccountJpaEntity account = new AccountJpaEntity();
        account.setId(id);
        account.setNormalizedEmail(email);
        account.setAccountType(type);
        return account;
    }

    @Test
    void usesPatientProfileNameAsDisplayName() {
        UUID id = UUID.randomUUID();
        PatientProfileJpaEntity profile = new PatientProfileJpaEntity();
        profile.setFullName("Nguyễn Văn An");
        when(accounts.findById(id)).thenReturn(Optional.of(account(id, "an@example.com", AccountType.PATIENT)));
        when(profiles.findByAccountId(id)).thenReturn(Optional.of(profile));

        var view = service.find(id).orElseThrow();

        assertEquals("Nguyễn Văn An", view.displayName());
        assertEquals("PATIENT", view.accountType());
        assertEquals("an@example.com", view.email());
    }

    @Test
    void usesRegistrationNameWhenNoProfileExists() {
        UUID id = UUID.randomUUID();
        AccountJpaEntity account = account(id, "an@example.com", AccountType.PATIENT);
        account.setFullName("Đàm Trọng Nghĩa");
        when(accounts.findById(id)).thenReturn(Optional.of(account));
        when(profiles.findByAccountId(id)).thenReturn(Optional.empty());

        assertEquals("Đàm Trọng Nghĩa", service.find(id).orElseThrow().displayName());
    }

    @Test
    void fallsBackToEmailLocalPartWithoutProfile() {
        UUID id = UUID.randomUUID();
        when(accounts.findById(id)).thenReturn(Optional.of(account(id, "demo.staff@localhost", AccountType.RECEPTIONIST)));
        when(profiles.findByAccountId(id)).thenReturn(Optional.empty());

        assertEquals("demo.staff", service.find(id).orElseThrow().displayName());
    }

    @Test
    void returnsEmptyForUnknownAccount() {
        UUID id = UUID.randomUUID();
        when(accounts.findById(id)).thenReturn(Optional.empty());

        assertTrue(service.find(id).isEmpty());
    }

    @Test void explicitAccountNameWinsOverProfile() {
        UUID id = UUID.randomUUID();
        var a = account(id, "an@example.com", AccountType.PATIENT);
        a.setDisplayName("Updated name");
        when(accounts.findById(id)).thenReturn(Optional.of(a));
        assertEquals("Updated name", service.find(id).orElseThrow().displayName());
    }

    @Test void ownerUsesFrontendRoleAndPreservesBackendAccountType() {
        UUID id = UUID.randomUUID();
        when(accounts.findById(id)).thenReturn(Optional.of(account(id, null, AccountType.OWNER)));
        var view = service.find(id).orElseThrow();
        assertEquals("OWNER", view.accountType());
        assertEquals("CLINIC_OWNER", view.role());
    }

    @Test void doctorUsesFrontendRoleAndPreservesBackendAccountType() {
        UUID id = UUID.randomUUID();
        when(accounts.findById(id)).thenReturn(Optional.of(account(id, null, AccountType.DOCTOR)));
        var view = service.find(id).orElseThrow();
        assertEquals("DOCTOR", view.accountType());
        assertEquals("ORTHOPEDIC_DOCTOR", view.role());
    }

    @Test void legacyStaffHasEmptyPresentationUsernameWithoutChangingCredential() {
        UUID id = UUID.randomUUID();
        var legacy = account(id, "legacy@example.test", AccountType.RECEPTIONIST);
        when(accounts.findById(id)).thenReturn(Optional.of(legacy));
        var view = service.find(id).orElseThrow();
        assertEquals("", view.username());
        assertEquals("legacy@example.test", view.email());
        org.junit.jupiter.api.Assertions.assertNull(legacy.getUsername());
    }

    @Test void mapsEveryNonActiveStatusToInactive() {
        for (var status : fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus.values()) {
            UUID id = UUID.randomUUID();
            var a = account(id, "an@example.com", AccountType.PATIENT);
            a.setDisplayName("Name"); a.setStatus(status);
            when(accounts.findById(id)).thenReturn(Optional.of(a));
            assertEquals(status.name().equals("ACTIVE") ? "ACTIVE" : "INACTIVE", service.find(id).orElseThrow().status());
        }
    }

    @Test void forcedPasswordOffersOnlyUsablePasswordActionEvenWithAvatar() {
        UUID id = UUID.randomUUID();
        var a = account(id, null, AccountType.DOCTOR);
        a.setDisplayName("Doctor"); a.setPasswordHash("hash"); a.setMustChangePassword(true);
        a.setStatus(fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus.ACTIVE);
        when(accounts.findById(id)).thenReturn(Optional.of(a));
        a.setAvatarContentType("image/png");
        var view = service.find(id).orElseThrow();
        assertEquals(java.util.List.of("changePassword"), view.allowedActions());
        assertEquals("ORTHOPEDIC_DOCTOR", view.role());
        assertEquals("/api/v1/accounts/" + id + "/avatar", view.avatarUrl());
    }
}
