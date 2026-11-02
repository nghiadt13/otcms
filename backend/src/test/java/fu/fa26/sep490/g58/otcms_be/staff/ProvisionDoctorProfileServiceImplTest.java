package fu.fa26.sep490.g58.otcms_be.staff;

import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.auth.service.DoctorAccountTypeQueryService;
import fu.fa26.sep490.g58.otcms_be.staff.repository.DoctorProfileRepository;
import fu.fa26.sep490.g58.otcms_be.staff.mapper.DoctorProfileMapper;
import fu.fa26.sep490.g58.otcms_be.staff.model.entity.DoctorProfileJpaEntity;
import fu.fa26.sep490.g58.otcms_be.staff.service.impl.ProvisionDoctorProfileServiceImpl;
import fu.fa26.sep490.g58.otcms_be.staff.service.ProvisionDoctorProfileService;
import fu.fa26.sep490.g58.otcms_be.staff.model.DoctorProfile;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProvisionDoctorProfileServiceImplTest {
    private final DoctorProfileRepository profiles = mock(DoctorProfileRepository.class);
    private final CoordinationGuardService guard = mock(CoordinationGuardService.class);
    private final DoctorAccountTypeQueryService doctorAccountTypes = mock(DoctorAccountTypeQueryService.class);

    @Test
    void provisionsOnlyAnExistingUnprofiledDoctorAccountAsInternalAndNotAccepting() {
        UUID accountId = UUID.randomUUID();
        when(doctorAccountTypes.isDoctorAccount(accountId)).thenReturn(true);
        when(profiles.existsByAccountId(accountId)).thenReturn(false);
        when(profiles.save(any(DoctorProfileJpaEntity.class))).thenAnswer(call -> call.getArgument(0));

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
        order.verify(profiles).existsByAccountId(accountId);
        order.verify(profiles).save(any(DoctorProfileJpaEntity.class));
    }

    @Test
    void rejectsNonDoctorOrAlreadyProfiledAccountsBeforeSaving() {
        UUID accountId = UUID.randomUUID();
        var command = new ProvisionDoctorProfileService.Command(accountId, "Doctor", null, null, null, null, null, null);
        when(doctorAccountTypes.isDoctorAccount(accountId)).thenReturn(false);
        assertEquals("DOCTOR_ACCOUNT_UNAVAILABLE", assertThrows(fu.fa26.sep490.g58.otcms_be.staff.exception.DoctorProfileException.class,
                () -> new ProvisionDoctorProfileServiceImpl(profiles, guard, doctorAccountTypes).provision(command)).getMessage());
        verify(profiles, never()).save(any(DoctorProfileJpaEntity.class));

        when(doctorAccountTypes.isDoctorAccount(accountId)).thenReturn(true);
        when(profiles.existsByAccountId(accountId)).thenReturn(true);
        assertEquals("DOCTOR_ACCOUNT_ALREADY_PROFILED", assertThrows(fu.fa26.sep490.g58.otcms_be.staff.exception.DoctorProfileException.class,
                () -> new ProvisionDoctorProfileServiceImpl(profiles, guard, doctorAccountTypes).provision(command)).getMessage());
        verify(profiles, never()).save(any(DoctorProfileJpaEntity.class));
    }
}
