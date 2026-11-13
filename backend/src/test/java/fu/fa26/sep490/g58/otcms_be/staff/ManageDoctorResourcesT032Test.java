package fu.fa26.sep490.g58.otcms_be.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentMutationImpactService;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditEntry;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditLogWriter;
import fu.fa26.sep490.g58.otcms_be.common.exception.ResourceMutationBlockedException;
import fu.fa26.sep490.g58.otcms_be.staff.model.entity.StaffProfileJpaEntity;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.staff.model.entity.DoctorWorkShiftJpaEntity;
import fu.fa26.sep490.g58.otcms_be.staff.repository.StaffProfileRepository;
import fu.fa26.sep490.g58.otcms_be.staff.repository.DoctorWorkShiftRepository;
import fu.fa26.sep490.g58.otcms_be.staff.repository.StaffResourceManagementAuditRepository;
import fu.fa26.sep490.g58.otcms_be.staff.service.impl.ManageDoctorResourcesServiceImpl;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitMutationImpactService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ManageDoctorResourcesT032Test {
    private static final Instant START = Instant.parse("2026-10-20T09:00:00Z");
    private static final Instant END = Instant.parse("2026-10-20T10:00:00Z");
    private final UUID doctorId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID shiftId = UUID.randomUUID();
    private final AuthenticatedAccount admin = new AuthenticatedAccount(actorId, AccountRole.ADMIN, UUID.randomUUID());

    @Mock private StaffProfileRepository profiles;
    @Mock private DoctorWorkShiftRepository shifts;
    @Mock private StaffResourceManagementAuditRepository audits;
    @Mock private AuditLogWriter auditLogs;
    @Mock private AppointmentMutationImpactService appointments;
    @Mock private VisitMutationImpactService visits;
    @Mock private CoordinationGuardService guard;
    private ManageDoctorResourcesServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ManageDoctorResourcesServiceImpl(profiles, shifts, audits, auditLogs, appointments, visits,
                guard, Clock.fixed(START, ZoneOffset.UTC));
    }

    @Test
    void deletingShiftThatRemovesBookingCoverageReturnsMinimalImpactAndDoesNotWrite() {
        var shift = new DoctorWorkShiftJpaEntity(shiftId, doctorId, START, END);
        when(profiles.existsByIdAndDoctorTrue(doctorId)).thenReturn(true);
        when(shifts.findByIdAndDoctorProfileId(shiftId, doctorId)).thenReturn(Optional.of(shift));
        when(shifts.findAllByDoctorProfileIdOrderByStartsAtAscIdAsc(doctorId)).thenReturn(List.of(shift));
        when(appointments.findProtectedBookingIntervals(doctorId)).thenReturn(List.of(
                new AppointmentMutationImpactService.OccupiedBookingInterval(START, END)));
        when(visits.findActiveCareIntervals(doctorId)).thenReturn(List.of());

        ResourceMutationBlockedException failure = assertThrows(ResourceMutationBlockedException.class,
                () -> service.deleteShift(doctorId, shiftId, admin, "t032-correlation"));

        assertEquals("WORK_SHIFT", failure.impact().getResourceType());
        assertEquals(shiftId, failure.impact().getResourceId());
        assertTrue(failure.impact().isBlocked());
        assertEquals(1, failure.impact().getAffectedBookingCount());
        assertFalse(failure.impact().isActiveCareBlocks());
        verify(guard).acquire();
        verify(shifts, never()).delete(any(DoctorWorkShiftJpaEntity.class));
        verify(audits, never()).append(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void removingShiftIsAllowedWhenAnotherShiftCoversProtectedBooking() {
        UUID replacementId = UUID.randomUUID();
        var shift = new DoctorWorkShiftJpaEntity(shiftId, doctorId, START, END);
        var replacement = new DoctorWorkShiftJpaEntity(replacementId, doctorId, START.minusSeconds(60), END.plusSeconds(60));
        when(profiles.existsByIdAndDoctorTrue(doctorId)).thenReturn(true);
        when(shifts.findByIdAndDoctorProfileId(shiftId, doctorId)).thenReturn(Optional.of(shift));
        when(shifts.findAllByDoctorProfileIdOrderByStartsAtAscIdAsc(doctorId)).thenReturn(List.of(shift, replacement));
        when(appointments.findProtectedBookingIntervals(doctorId)).thenReturn(List.of(
                new AppointmentMutationImpactService.OccupiedBookingInterval(START, END)));
        when(visits.findActiveCareIntervals(doctorId)).thenReturn(List.of());

        service.deleteShift(doctorId, shiftId, admin, "t032-correlation");

        verify(shifts).delete(shift);
        verify(audits).append(any(), eq(doctorId), eq(actorId), eq("WORK_SHIFT"), eq(shiftId), eq("DELETED"),
                any(), eq("{}"), any(), eq("t032-correlation"));
    }

    @Test
    void impactReadFailureFailsClosedAsDependencyUnavailable() {
        when(profiles.existsByIdAndDoctorTrue(doctorId)).thenReturn(true);
        var shift = new DoctorWorkShiftJpaEntity(shiftId, doctorId, START, END);
        when(shifts.findByIdAndDoctorProfileId(shiftId, doctorId)).thenReturn(Optional.of(shift));
        when(shifts.findAllByDoctorProfileIdOrderByStartsAtAscIdAsc(doctorId)).thenReturn(List.of(shift));
        when(appointments.findProtectedBookingIntervals(doctorId))
                .thenThrow(new IllegalStateException("owner query unavailable"));

        var failure = assertThrows(fu.fa26.sep490.g58.otcms_be.staff.exception.DoctorProfileException.class,
                () -> service.deleteShift(doctorId, shiftId, admin, "t032-correlation"));

        assertEquals("DEPENDENCY_UNAVAILABLE", failure.getMessage());
        verify(shifts, never()).delete(any(DoctorWorkShiftJpaEntity.class));
        verify(audits, never()).append(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void creatingPastScheduleDoesNotAcquireLockOrWrite() {
        var failure = assertThrows(fu.fa26.sep490.g58.otcms_be.staff.exception.DoctorProfileException.class,
                () -> service.createShift(doctorId, START.minusSeconds(60), END, admin, "correlation"));
        assertEquals("DOCTOR_RESOURCE_IN_PAST", failure.getMessage());
        org.mockito.Mockito.verifyNoInteractions(guard, shifts, audits);
    }

    @Test
    void historicalShiftCannotBeMovedToFutureOrDeleted() {
        var shift = new DoctorWorkShiftJpaEntity(shiftId, doctorId, START.minusSeconds(3600), START.minusSeconds(60));
        when(profiles.existsByIdAndDoctorTrue(doctorId)).thenReturn(true);
        when(shifts.findByIdAndDoctorProfileId(shiftId, doctorId)).thenReturn(Optional.of(shift));
        assertThrows(fu.fa26.sep490.g58.otcms_be.staff.exception.DoctorProfileException.class,
                () -> service.deleteShift(doctorId, shiftId, admin, "correlation"));
        assertThrows(fu.fa26.sep490.g58.otcms_be.staff.exception.DoctorProfileException.class,
                () -> service.updateShift(doctorId, shiftId, END, END.plusSeconds(3600), admin, "correlation"));
        verify(shifts, never()).delete(any());
        verify(shifts, never()).save(any());
    }

    @Test
    void directoryStateChangeWritesDoctorProfileAuditWithObjectSnapshots() {
        var profile = new StaffProfileJpaEntity(doctorId, UUID.randomUUID(), "BS. An", null);
        profile.becomeDoctor("s", "q", "b", "c", true);
        when(profiles.findByIdAndDoctorTrue(doctorId)).thenReturn(Optional.of(profile));
        when(profiles.save(profile)).thenReturn(profile);

        service.updateDirectoryState(doctorId, false, false, true, admin, "t032-correlation");

        var entry = org.mockito.ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditLogs).record(entry.capture());
        assertEquals("staff_resource_audits", entry.getValue().source());
        assertEquals("STAFF_PROFILE", entry.getValue().category());
        assertEquals("UPDATE_STAFF_PROFILE", entry.getValue().action());
        assertEquals("DOCTOR_PROFILE", entry.getValue().recordType());
        assertEquals(doctorId, entry.getValue().entityId());
        assertEquals("BS. An", entry.getValue().recordLabel());
        assertEquals(actorId, entry.getValue().actorAccountId());
        assertTrue(entry.getValue().visibleInActivityLog());
        assertEquals(java.util.Map.of("publiclyVisible", true, "acceptingNewBookings", false,
                "inActiveDirectory", true), entry.getValue().beforeState());
        assertEquals(java.util.Map.of("publiclyVisible", false, "acceptingNewBookings", false,
                "inActiveDirectory", true), entry.getValue().afterState());
    }
}
