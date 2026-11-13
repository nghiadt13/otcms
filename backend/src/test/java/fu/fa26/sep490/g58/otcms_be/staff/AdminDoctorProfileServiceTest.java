package fu.fa26.sep490.g58.otcms_be.staff;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import fu.fa26.sep490.g58.otcms_be.auth.service.DoctorAccountTypeQueryService;
import fu.fa26.sep490.g58.otcms_be.auth.service.DoctorAccountTypeQueryService.DoctorAccountOption;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.staff.model.entity.StaffProfileJpaEntity;
import fu.fa26.sep490.g58.otcms_be.staff.repository.*;
import fu.fa26.sep490.g58.otcms_be.staff.service.AdminDoctorProfileService.ProfileEdit;
import fu.fa26.sep490.g58.otcms_be.staff.service.impl.AdminDoctorProfileServiceImpl;
import java.util.*;
import org.junit.jupiter.api.Test;

class AdminDoctorProfileServiceTest {
    private final StaffProfileRepository profiles = mock(StaffProfileRepository.class);
    private final DoctorAccountTypeQueryService accounts = mock(DoctorAccountTypeQueryService.class);
    private final CoordinationGuardService guard = mock(CoordinationGuardService.class);
    private final AdminDoctorProfileServiceImpl service = new AdminDoctorProfileServiceImpl(profiles, accounts, guard);
    private final UUID id = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();

    private StaffProfileJpaEntity profile() {
        var profile = new StaffProfileJpaEntity(id, accountId, "Doctor", "contact");
        profile.becomeDoctor("", "", "", "", true);
        profile.updateDirectoryState(true, false, true);
        return profile;
    }

    @Test void eligibleAccountsExcludeAnyAlreadyLinkedAccount() {
        UUID available = UUID.randomUUID();
        when(profiles.findAllDoctors()).thenReturn(List.of(profile()));
        when(accounts.listActiveDoctorAccounts()).thenReturn(List.of(new DoctorAccountOption(accountId, "linked", "Linked"), new DoctorAccountOption(available, "new", "New")));
        assertEquals(List.of(available), service.eligibleAccounts().stream().map(DoctorAccountOption::accountId).toList());
    }

    @Test void editingPreservesAccountAndDirectoryFlags() {
        when(profiles.findByIdAndDoctorTrue(id)).thenReturn(Optional.of(profile()));
        when(profiles.save(any())).thenAnswer(c -> c.getArgument(0));
        var result = service.update(id, new ProfileEdit(" New name ", null, " contact ", " Orthopedics ", null, null, null));
        assertEquals(accountId, result.accountId());
        assertTrue(result.publiclyVisible()); assertFalse(result.acceptingNewBookings()); assertTrue(result.inActiveDirectory());
        assertEquals("New name", result.fullName()); assertEquals("Orthopedics", result.specialties());
        var order = inOrder(guard, profiles); order.verify(guard).acquire(); order.verify(profiles).findByIdAndDoctorTrue(id);
    }

    @Test void nonDoctorStaffProfileCannotBeEditedAsDoctor() {
        when(profiles.findByIdAndDoctorTrue(id)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> service.update(id, new ProfileEdit("Name", null, null, null, null, null, null)));
        verify(profiles, never()).save(any());
    }
}
