package fu.fa26.sep490.g58.otcms_be.staff;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import fu.fa26.sep490.g58.otcms_be.auth.service.DoctorAccountTypeQueryService;
import fu.fa26.sep490.g58.otcms_be.auth.service.DoctorAccountTypeQueryService.DoctorAccountOption;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.staff.model.entity.DoctorProfileJpaEntity;
import fu.fa26.sep490.g58.otcms_be.staff.repository.*;
import fu.fa26.sep490.g58.otcms_be.staff.service.AdminDoctorProfileService.ProfileEdit;
import fu.fa26.sep490.g58.otcms_be.staff.service.impl.AdminDoctorProfileServiceImpl;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class AdminDoctorProfileServiceTest {
    private final DoctorProfileRepository profiles = mock(DoctorProfileRepository.class);
    private final DoctorOperationalTimeOffRepository timeOffs = mock(DoctorOperationalTimeOffRepository.class);
    private final DoctorAccountTypeQueryService accounts = mock(DoctorAccountTypeQueryService.class);
    private final CoordinationGuardService guard = mock(CoordinationGuardService.class);
    private final AdminDoctorProfileServiceImpl service = new AdminDoctorProfileServiceImpl(profiles, timeOffs, accounts, guard);
    private final UUID id = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();

    private DoctorProfileJpaEntity profile() {
        return new DoctorProfileJpaEntity(id, accountId, "Doctor", null, "contact", "", "", "", "", true, false, true);
    }

    @Test void eligibleAccountsExcludeAnyAlreadyLinkedAccount() {
        UUID available = UUID.randomUUID();
        when(profiles.findAll()).thenReturn(List.of(profile()));
        when(accounts.listActiveDoctorAccounts()).thenReturn(List.of(new DoctorAccountOption(accountId, "linked", "Linked"), new DoctorAccountOption(available, "new", "New")));
        assertEquals(List.of(available), service.eligibleAccounts().stream().map(DoctorAccountOption::accountId).toList());
    }

    @Test void editingPreservesAccountAndDirectoryFlags() {
        when(profiles.findById(id)).thenReturn(Optional.of(profile()));
        when(profiles.save(any())).thenAnswer(c -> c.getArgument(0));
        var result = service.update(id, new ProfileEdit(" New name ", null, " contact ", " Orthopedics ", null, null, null));
        assertEquals(accountId, result.accountId());
        assertTrue(result.publiclyVisible()); assertFalse(result.acceptingNewBookings()); assertTrue(result.inActiveDirectory());
        assertEquals("New name", result.fullName()); assertEquals("Orthopedics", result.specialties());
        var order = inOrder(guard, profiles); order.verify(guard).acquire(); order.verify(profiles).findById(id);
    }

    @Test void badRangeAndMissingDoctorNeverReadTimeOffs() {
        Instant now = Instant.parse("2026-10-29T00:00:00Z");
        assertThrows(RuntimeException.class, () -> service.timeOffs(id, now, now));
        assertThrows(RuntimeException.class, () -> service.timeOffs(id, now, now.plusSeconds(60)));
        verifyNoInteractions(timeOffs);
    }
}
