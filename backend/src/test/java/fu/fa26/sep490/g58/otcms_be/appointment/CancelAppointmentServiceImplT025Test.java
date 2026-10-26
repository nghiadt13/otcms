package fu.fa26.sep490.g58.otcms_be.appointment;
import fu.fa26.sep490.g58.otcms_be.appointment.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.appointment.model.*;

import fu.fa26.sep490.g58.otcms_be.appointment.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.appointment.model.*;
import fu.fa26.sep490.g58.otcms_be.appointment.exception.*;

import fu.fa26.sep490.g58.otcms_be.appointment.service.*;
import fu.fa26.sep490.g58.otcms_be.appointment.repository.*;
import fu.fa26.sep490.g58.otcms_be.appointment.service.impl.*;
import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.patient.exception.PatientAuthorizationDeniedException;
import fu.fa26.sep490.g58.otcms_be.patient.service.AuthorizePatientProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CancelAppointmentServiceImplT025Test {
    private final UUID appointmentId = UUID.randomUUID();
    private final UUID patientId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();
    private final UUID doctorId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-14T05:00:00Z");
    private final CoordinationGuardService guard = mock(CoordinationGuardService.class);
    private final AppointmentCancellationRepository appointments = mock(AppointmentCancellationRepository.class);
    private final AuthorizePatientProfileService auth = mock(AuthorizePatientProfileService.class);
    private CancelAppointmentServiceImpl handler;
    private CancellableAppointment current;

    @BeforeEach
    void setUp() {
        var properties = new AppointmentBookingProperties(30, 120, 1440, 30, 15, 15,
                ZoneId.of("Asia/Ho_Chi_Minh"), new AppointmentBookingProperties.PublicRateLimit(60, 60));
        handler = new CancelAppointmentServiceImpl(guard, appointments, auth, properties,
                Clock.fixed(now, ZoneId.of("UTC")));
        current = new CancellableAppointment(appointmentId, patientId, serviceId,
                doctorId, "CONFIRMED", "PATIENT_ONLINE", "knee pain", now.plusSeconds(86400),
                now.plusSeconds(86400 + 1800), 30, 5, null, null, now.minusSeconds(86400));
        when(appointments.findForUpdate(appointmentId)).thenReturn(Optional.of(current));
        when(auth.authorize(accountId, patientId)).thenReturn(new AuthorizePatientProfileService.AuthorizedPatientProfile(
                patientId, AuthorizePatientProfileService.AuthorizationScope.SELF));
    }

    @Test
    void patientCanCancelAtExactDeadlineAndAuditUsesCurrentActorAndReason() {
        var expected = new CreatedAppointment(appointmentId, patientId, serviceId, doctorId, "CANCELLED",
                "PATIENT_ONLINE", "knee pain", current.startTime(), current.endTime(), 30, 5,
                current.endTime().plusSeconds(300), null, null, current.createdAt(), now);
        when(appointments.cancel(current, accountId, "PATIENT", "change of plans", now, "trace-id"))
                .thenReturn(expected);

        var result = handler.cancel(appointmentId, new CancelAppointmentCommand(accountId,
                CancelAppointmentCommand.Actor.PATIENT, " change of plans ", "trace-id"));

        assertEquals(expected, result);
        InOrder order = inOrder(guard, appointments);
        order.verify(guard).acquire();
        order.verify(appointments).findForUpdate(appointmentId);
        verify(auth).authorize(accountId, patientId);
        verify(appointments).cancel(current, accountId, "PATIENT", "change of plans", now, "trace-id");
    }

    @Test
    void rejectsPatientInsideDeadlineAndOutOfScopeProfileWithoutWrites() {
        var tooSoon = new CancellableAppointment(appointmentId, patientId, serviceId,
                doctorId, "CONFIRMED", "PATIENT_ONLINE", "knee pain", now.plusSeconds(86399),
                now.plusSeconds(86399 + 1800), 30, 5, null, null, current.createdAt());
        when(appointments.findForUpdate(appointmentId)).thenReturn(Optional.of(tooSoon));
        assertEquals("APPOINTMENT_CHANGE_TOO_LATE", assertThrows(AppointmentBookingException.class,
                () -> handler.cancel(appointmentId, new CancelAppointmentCommand(accountId,
                        CancelAppointmentCommand.Actor.PATIENT, null, "trace-id"))).code());
        verify(appointments, never()).cancel(any(), any(), any(), any(), any(), any());

        when(auth.authorize(accountId, patientId)).thenThrow(new PatientAuthorizationDeniedException());
        assertEquals("APPOINTMENT_NOT_FOUND", assertThrows(AppointmentBookingException.class,
                () -> handler.cancel(appointmentId, new CancelAppointmentCommand(accountId,
                        CancelAppointmentCommand.Actor.PATIENT, null, "trace-id"))).code());
        verify(appointments, never()).cancel(any(), any(), any(), any(), any(), any());
    }

    @Test
    void receptionistRequiresNonblankReasonAndCheckedInIsNotChangeable() {
        assertEquals("VALIDATION_FAILED", assertThrows(AppointmentBookingException.class,
                () -> handler.cancel(appointmentId, new CancelAppointmentCommand(accountId,
                        CancelAppointmentCommand.Actor.RECEPTIONIST, "  ", "trace-id"))).code());
        verifyNoInteractions(guard, appointments);

        var checkedIn = new CancellableAppointment(appointmentId, patientId, serviceId,
                doctorId, "CHECKED_IN", "PATIENT_ONLINE", "knee pain", current.startTime(), current.endTime(),
                30, 5, UUID.randomUUID(), null, current.createdAt());
        when(appointments.findForUpdate(appointmentId)).thenReturn(Optional.of(checkedIn));
        assertEquals("APPOINTMENT_NOT_CHANGEABLE", assertThrows(AppointmentBookingException.class,
                () -> handler.cancel(appointmentId, new CancelAppointmentCommand(accountId,
                        CancelAppointmentCommand.Actor.RECEPTIONIST, "operational reason", "trace-id"))).code());
        verify(appointments, never()).cancel(any(), any(), any(), any(), any(), any());
    }
}
