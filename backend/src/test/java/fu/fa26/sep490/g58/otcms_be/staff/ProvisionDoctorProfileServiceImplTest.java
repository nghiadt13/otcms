package fu.fa26.sep490.g58.otcms_be.staff;

import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.auth.service.DoctorAccountTypeQueryService;
import fu.fa26.sep490.g58.otcms_be.staff.repository.StaffProfileRepository;
import fu.fa26.sep490.g58.otcms_be.staff.mapper.DoctorProfileMapper;
import fu.fa26.sep490.g58.otcms_be.staff.model.entity.StaffProfileJpaEntity;
import fu.fa26.sep490.g58.otcms_be.staff.service.impl.ProvisionDoctorProfileServiceImpl;
import fu.fa26.sep490.g58.otcms_be.staff.service.ProvisionDoctorProfileService;
import fu.fa26.sep490.g58.otcms_be.staff.model.DoctorProfile;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProvisionDoctorProfileServiceImplTest {
    private final StaffProfileRepository profiles = mock(StaffProfileRepository.class);
    private final CoordinationGuardService guard = mock(CoordinationGuardService.class);
    private final DoctorAccountTypeQueryService doctorAccountTypes = mock(DoctorAccountTypeQueryService.class);

    @Test
    void provisionsOnlyAnExistingUnprofiledDoctorAccountAsInternalAndNotAccepting() {
        UUID accountId = UUID.randomUUID();
        when(doctorAccountTypes.isDoctorAccount(accountId)).thenReturn(true);
        when(profiles.existsByAccountIdAndDoctorTrue(accountId)).thenReturn(false);
        when(profiles.save(any(StaffProfileJpaEntity.class))).thenAnswer(call -> call.getArgument(0));

        DoctorProfile profile = new ProvisionDoctorProfileServiceImpl(profiles, guard, doctorAccountTypes).provision(
                new ProvisionDoctorProfileService.Command(accountId, "  Dr Example ", null, null,
                        "  Orthopedics ", null, null, null));

        assertNotNull(profile.id());
        assertEquals("Dr Example", profile.fullName());
        assertEquals("Orthopedics", profile.specialties());
        assertFalse(profile.publiclyVisible());
        assertFalse(profile.acceptingNewBookings());
        assertTrue(profile.inActiveDirectory());
        var order = inOrder(guard, doctorAccountTypes, profiles);
        order.verify(guard).acquire();
        order.verify(doctorAccountTypes).isDoctorAccount(accountId);
        order.verify(profiles).existsByAccountIdAndDoctorTrue(accountId);
        order.verify(profiles).save(any(StaffProfileJpaEntity.class));
    }

    @Test
    void turnsAnExistingNonDoctorStaffProfileIntoADoctorInsteadOfInsertingASecondRow() {
        UUID accountId = UUID.randomUUID();
        var existing = new StaffProfileJpaEntity(UUID.randomUUID(), accountId, "Old name", null);
        when(doctorAccountTypes.isDoctorAccount(accountId)).thenReturn(true);
        when(profiles.existsByAccountIdAndDoctorTrue(accountId)).thenReturn(false);
        when(profiles.findByAccountId(accountId)).thenReturn(java.util.Optional.of(existing));
        when(profiles.save(existing)).thenReturn(existing);

        DoctorProfile profile = new ProvisionDoctorProfileServiceImpl(profiles, guard, doctorAccountTypes).provision(
                new ProvisionDoctorProfileService.Command(accountId, " Dr New ", null, null,
                        "Orthopedics", null, null, null));

        assertEquals(existing.id(), profile.id());
        assertEquals("Dr New", profile.fullName());
        assertTrue(existing.isDoctor());
        assertFalse(profile.acceptingNewBookings());
        verify(profiles).save(existing);
    }

    @Test
    void rejectsNonDoctorOrAlreadyProfiledAccountsBeforeSaving() {
        UUID accountId = UUID.randomUUID();
        var command = new ProvisionDoctorProfileService.Command(accountId, "Doctor", null, null, null, null, null, null);
        when(doctorAccountTypes.isDoctorAccount(accountId)).thenReturn(false);
        assertEquals("DOCTOR_ACCOUNT_UNAVAILABLE", assertThrows(fu.fa26.sep490.g58.otcms_be.staff.exception.DoctorProfileException.class,
                () -> new ProvisionDoctorProfileServiceImpl(profiles, guard, doctorAccountTypes).provision(command)).getMessage());
        verify(profiles, never()).save(any(StaffProfileJpaEntity.class));

        when(doctorAccountTypes.isDoctorAccount(accountId)).thenReturn(true);
        when(profiles.existsByAccountIdAndDoctorTrue(accountId)).thenReturn(true);
        assertEquals("DOCTOR_ACCOUNT_ALREADY_PROFILED", assertThrows(fu.fa26.sep490.g58.otcms_be.staff.exception.DoctorProfileException.class,
                () -> new ProvisionDoctorProfileServiceImpl(profiles, guard, doctorAccountTypes).provision(command)).getMessage());
        verify(profiles, never()).save(any(StaffProfileJpaEntity.class));
    }
}
