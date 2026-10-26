package fu.fa26.sep490.g58.otcms_be.appointment;

import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentBookingException;
import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentCapacityConflictException;
import fu.fa26.sep490.g58.otcms_be.appointment.model.CancellableAppointment;
import fu.fa26.sep490.g58.otcms_be.appointment.model.CreatedAppointment;
import fu.fa26.sep490.g58.otcms_be.appointment.repository.AppointmentLifecycleRepository;
import fu.fa26.sep490.g58.otcms_be.appointment.service.UpdateAppointmentStatusCommand;
import fu.fa26.sep490.g58.otcms_be.appointment.service.impl.UpdateAppointmentStatusServiceImpl;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitWorkflowException;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitScheduledCareService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class UpdateAppointmentStatusT029Test {
    private final UUID appointmentId = UUID.randomUUID();
    private final UUID patientId = UUID.randomUUID();
    private final UUID doctorId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final UUID visitId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-19T05:00:00Z");
    private final CoordinationGuardService guard = mock(CoordinationGuardService.class);
    private final AppointmentLifecycleRepository appointments = mock(AppointmentLifecycleRepository.class);
    private final DoctorProfileIdentityQueryService doctors = mock(DoctorProfileIdentityQueryService.class);
    private final VisitScheduledCareService visits = mock(VisitScheduledCareService.class);
    private final UpdateAppointmentStatusServiceImpl service = new UpdateAppointmentStatusServiceImpl(guard,
            appointments, doctors, visits, Clock.fixed(now, ZoneOffset.UTC));
    private final CancellableAppointment checkedIn = new CancellableAppointment(appointmentId, patientId,
            UUID.randomUUID(), doctorId, "CHECKED_IN", "PATIENT_ONLINE", "purpose", now.minusSeconds(600),
            now.plusSeconds(1200), 30, 5, visitId, null, now.minusSeconds(3600));
    private final UpdateAppointmentStatusCommand start = new UpdateAppointmentStatusCommand(accountId,
            "DOCTOR", "IN_PROGRESS", "trace-t029");

    @BeforeEach
    void setUp() {
        when(doctors.findDoctorProfileId(accountId)).thenReturn(Optional.of(doctorId));
        when(appointments.findForUpdate(appointmentId)).thenReturn(Optional.of(checkedIn));
    }

    @Test
    void startChecksProfileAndCapacityBeforePersistingStatusAndAudit() {
        CreatedAppointment result = mock(CreatedAppointment.class);
        when(appointments.transition(checkedIn, accountId, "DOCTOR", "IN_PROGRESS", now, "trace-t029"))
                .thenReturn(result);
        assertEquals(result, service.updateStatus(appointmentId, start));
        var order = inOrder(guard, appointments, visits);
        order.verify(guard).acquire();
        order.verify(appointments).findForUpdate(appointmentId);
        order.verify(visits).startScheduledCare(argThat(command -> command.appointmentId().equals(appointmentId)
                && command.visitId().equals(visitId) && command.doctorProfileId().equals(doctorId)));
        order.verify(appointments).transition(checkedIn, accountId, "DOCTOR", "IN_PROGRESS", now, "trace-t029");
    }

    @Test
    void wrongDoctorIsDeniedBeforeVisitMutation() {
        when(doctors.findDoctorProfileId(accountId)).thenReturn(Optional.of(UUID.randomUUID()));
        assertEquals("ACCESS_DENIED", assertThrows(AppointmentBookingException.class,
                () -> service.updateStatus(appointmentId, start)).code());
        verify(guard).acquire();
        verifyNoInteractions(visits);
        verify(appointments, never()).transition(any(), any(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void capacityFailureDoesNotChangeAppointmentStatus() {
        doThrow(new VisitWorkflowException("VISIT_CAPACITY_NOT_AVAILABLE"))
                .when(visits).startScheduledCare(any());
        assertEquals("VISIT_CAPACITY_NOT_AVAILABLE", assertThrows(AppointmentCapacityConflictException.class,
                () -> service.updateStatus(appointmentId, start)).code());
        verify(appointments, never()).transition(any(), any(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void completionRequiresInProgressAppointmentAndLeavesIntervalSnapshotToRepository() {
        var active = new CancellableAppointment(appointmentId, patientId, UUID.randomUUID(), doctorId,
                "IN_PROGRESS", "PATIENT_ONLINE", "purpose", checkedIn.startTime(), checkedIn.endTime(),
                30, 5, visitId, null, checkedIn.createdAt());
        when(appointments.findForUpdate(appointmentId)).thenReturn(Optional.of(active));
        when(appointments.transition(active, accountId, "DOCTOR", "COMPLETED", now, "trace-t029"))
                .thenReturn(mock(CreatedAppointment.class));
        var result = service.updateStatus(appointmentId, new UpdateAppointmentStatusCommand(accountId,
                "DOCTOR", "COMPLETED", "trace-t029"));
        verify(visits).completeScheduledCare(any());
        verify(appointments).transition(active, accountId, "DOCTOR", "COMPLETED", now, "trace-t029");
    }
}
