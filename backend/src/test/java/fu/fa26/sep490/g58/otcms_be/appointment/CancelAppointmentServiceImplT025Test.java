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
import fu.fa26.sep490.g58.otcms_be.notification.service.NotificationPublisher;
import fu.fa26.sep490.g58.otcms_be.patient.exception.PatientAuthorizationDeniedException;
import fu.fa26.sep490.g58.otcms_be.patient.service.AuthorizePatientProfileService;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffAvailabilityProviderQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitScheduledCareService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CancelAppointmentServiceImplT025Test {
    private final UUID appointmentId = UUID.randomUUID();
    private final UUID patientId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final UUID adminAccountId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();
    private final UUID doctorId = UUID.randomUUID();
    private final UUID visitId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-14T05:00:00Z");
    private final CoordinationGuardService guard = mock(CoordinationGuardService.class);
    private final AppointmentCancellationRepository appointments = mock(AppointmentCancellationRepository.class);
    private final AuthorizePatientProfileService auth = mock(AuthorizePatientProfileService.class);
    private final AppointmentLifecycleRepository lifecycle = mock(AppointmentLifecycleRepository.class);
    private final VisitScheduledCareService visits = mock(VisitScheduledCareService.class);
    private final NotificationPublisher notifications = mock(NotificationPublisher.class);
    private final StaffAvailabilityProviderQueryService doctors = mock(StaffAvailabilityProviderQueryService.class);
    /** Without the checked-in collaborators (patient and online paths only). */
    private CancelAppointmentServiceImpl handler;
    /** With the lifecycle and visit collaborators (checked-in paths). */
    private CancelAppointmentServiceImpl fullHandler;
    private CancellableAppointment current;

    @BeforeEach
    void setUp() {
        var properties = new AppointmentBookingProperties(30, 120, 30, 15, 15,
                ZoneId.of("Asia/Ho_Chi_Minh"), new AppointmentBookingProperties.PublicRateLimit(60, 60), 15, 30000L, false);
        var clock = Clock.fixed(now, ZoneId.of("UTC"));
        handler = new CancelAppointmentServiceImpl(guard, appointments, auth, properties, clock, notifications, doctors, org.mockito.Mockito.mock(fu.fa26.sep490.g58.otcms_be.common.audit.AuditLogWriter.class));
        fullHandler = new CancelAppointmentServiceImpl(guard, appointments, auth, properties, clock, lifecycle, visits,
                notifications, doctors, null, null, org.mockito.Mockito.mock(fu.fa26.sep490.g58.otcms_be.common.audit.AuditLogWriter.class));
        current = appointment("CONFIRMED", now.plusSeconds(86400), null);
        when(appointments.findForUpdate(appointmentId)).thenReturn(Optional.of(current));
        when(auth.authorize(accountId, patientId)).thenReturn(new AuthorizePatientProfileService.AuthorizedPatientProfile(
                patientId, AuthorizePatientProfileService.AuthorizationScope.SELF));
    }

    @Test
    void patientCancelsWhenStartIsMoreThanTwoHoursAwayAndAuditUsesCurrentActorAndReason() {
        current = appointment("CONFIRMED", now.plusSeconds(121 * 60L), null);
        when(appointments.findForUpdate(appointmentId)).thenReturn(Optional.of(current));
        var expected = cancelledResult(current);
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
        verifyNoInteractions(notifications);
    }

    @Test
    void rejectsPatientWithinTwoHoursAndOutOfScopeProfileWithoutWrites() {
        var tooSoon = appointment("CONFIRMED", now.plusSeconds(119 * 60L), null);
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
    void receptionistRequiresNonblankReasonAndCannotCancelCheckedInAppointment() {
        assertEquals("VALIDATION_FAILED", assertThrows(AppointmentBookingException.class,
                () -> fullHandler.cancel(appointmentId, new CancelAppointmentCommand(accountId,
                        CancelAppointmentCommand.Actor.RECEPTIONIST, "  ", "trace-id"))).code());
        verifyNoInteractions(guard, appointments);

        var checkedIn = appointment("CHECKED_IN", current.startTime(), visitId);
        when(appointments.findForUpdate(appointmentId)).thenReturn(Optional.of(checkedIn));
        assertEquals("APPOINTMENT_NOT_CHANGEABLE", assertThrows(AppointmentBookingException.class,
                () -> fullHandler.cancel(appointmentId, new CancelAppointmentCommand(accountId,
                        CancelAppointmentCommand.Actor.RECEPTIONIST, "operational reason", "trace-id"))).code());
        verifyNoInteractions(visits, lifecycle, notifications);
        verify(appointments, never()).cancel(any(), any(), any(), any(), any(), any());
    }

    @Test
    void adminMustGiveReasonForAnyCancellation() {
        assertEquals("VALIDATION_FAILED", assertThrows(AppointmentBookingException.class,
                () -> fullHandler.cancel(appointmentId, new CancelAppointmentCommand(adminAccountId,
                        CancelAppointmentCommand.Actor.ADMIN, null, "trace-id"))).code());
        assertEquals("VALIDATION_FAILED", assertThrows(AppointmentBookingException.class,
                () -> fullHandler.cancel(appointmentId, new CancelAppointmentCommand(adminAccountId,
                        CancelAppointmentCommand.Actor.ADMIN, "   ", "trace-id"))).code());
        verifyNoInteractions(guard, appointments);
    }

    @Test
    void adminCancelsConfirmedAppointmentAnytimeWithAdminAuditAndNotifiesPatient() {
        current = appointment("CONFIRMED", now.plusSeconds(600), null);
        when(appointments.findForUpdate(appointmentId)).thenReturn(Optional.of(current));
        var expected = cancelledResult(current);
        when(appointments.cancel(current, adminAccountId, "ADMIN", "doctor called away", now, "trace-id"))
                .thenReturn(expected);

        var result = handler.cancel(appointmentId, new CancelAppointmentCommand(adminAccountId,
                CancelAppointmentCommand.Actor.ADMIN, " doctor called away ", "trace-id"));

        assertEquals(expected, result);
        verify(appointments).cancel(current, adminAccountId, "ADMIN", "doctor called away", now, "trace-id");
        verify(notifications).publishToPatient(eq(patientId), any());
        verifyNoInteractions(auth);
    }

    @Test
    void adminCancelsCheckedInVisitThroughVisitPathWithAdminAuditAndNotifiesPatient() {
        var checkedIn = appointment("CHECKED_IN", now.plusSeconds(600), visitId);
        when(appointments.findForUpdate(appointmentId)).thenReturn(Optional.of(checkedIn));
        when(lifecycle.findForUpdate(appointmentId)).thenReturn(Optional.of(checkedIn));
        var expected = cancelledResult(checkedIn);
        when(lifecycle.transition(checkedIn, adminAccountId, "ADMIN", "CANCELLED", "ops reason", now, "trace-id"))
                .thenReturn(expected);

        var result = fullHandler.cancel(appointmentId, new CancelAppointmentCommand(adminAccountId,
                CancelAppointmentCommand.Actor.ADMIN, "ops reason", "trace-id"));

        assertEquals(expected, result);
        verify(visits).cancelCheckedInVisit(any());
        verify(lifecycle).transition(checkedIn, adminAccountId, "ADMIN", "CANCELLED", "ops reason", now, "trace-id");
        verify(notifications).publishToPatient(eq(patientId), any());
    }

    @Test
    void adminCannotCancelAppointmentWhoseCareHasStarted() {
        var inProgress = appointment("IN_PROGRESS", now.plusSeconds(600), visitId);
        when(appointments.findForUpdate(appointmentId)).thenReturn(Optional.of(inProgress));
        assertEquals("VISIT_CANCELLATION_NOT_ALLOWED", assertThrows(AppointmentBookingException.class,
                () -> fullHandler.cancel(appointmentId, new CancelAppointmentCommand(adminAccountId,
                        CancelAppointmentCommand.Actor.ADMIN, "ops reason", "trace-id"))).code());
        verifyNoInteractions(visits, lifecycle, notifications);
        verify(appointments, never()).cancel(any(), any(), any(), any(), any(), any());
    }

    private CancellableAppointment appointment(String status, Instant start, UUID linkedVisit) {
        return new CancellableAppointment(appointmentId, patientId, serviceId, doctorId, status, "PATIENT_ONLINE",
                "knee pain", start, start.plusSeconds(1800), 30, 5, linkedVisit, null, now.minusSeconds(86400));
    }

    private CreatedAppointment cancelledResult(CancellableAppointment source) {
        return new CreatedAppointment(appointmentId, patientId, serviceId, doctorId, "CANCELLED", "PATIENT_ONLINE",
                "knee pain", source.startTime(), source.endTime(), 30, 5, source.endTime().plusSeconds(300),
                source.visitId(), null, source.createdAt(), now);
    }
}
