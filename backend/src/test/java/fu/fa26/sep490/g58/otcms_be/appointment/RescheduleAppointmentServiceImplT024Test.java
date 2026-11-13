package fu.fa26.sep490.g58.otcms_be.appointment;
import fu.fa26.sep490.g58.otcms_be.appointment.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.appointment.model.*;

import fu.fa26.sep490.g58.otcms_be.appointment.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.appointment.model.*;
import fu.fa26.sep490.g58.otcms_be.appointment.exception.*;

import fu.fa26.sep490.g58.otcms_be.appointment.service.*;
import fu.fa26.sep490.g58.otcms_be.appointment.repository.*;
import fu.fa26.sep490.g58.otcms_be.appointment.service.impl.*;
import fu.fa26.sep490.g58.otcms_be.appointment.model.AvailableSlot;
import fu.fa26.sep490.g58.otcms_be.appointment.model.SlotAvailabilityRequest;
import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.patient.exception.PatientAuthorizationDeniedException;
import fu.fa26.sep490.g58.otcms_be.patient.service.AuthorizePatientProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RescheduleAppointmentServiceImplT024Test {
    private final UUID appointmentId = UUID.randomUUID();
    private final UUID patientId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();
    private final UUID doctorId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-14T00:00:00Z");
    private final CoordinationGuardService guard = mock(CoordinationGuardService.class);
    private final AppointmentRescheduleRepository appointments = mock(AppointmentRescheduleRepository.class);
    private final AuthorizePatientProfileService auth = mock(AuthorizePatientProfileService.class);
    private final GenerateAvailableSlotsService slots = mock(GenerateAvailableSlotsService.class);
    private RescheduleAppointmentServiceImpl handler;
    private ReschedulableAppointment current;

    @BeforeEach
    void setUp() {
        var properties = new AppointmentBookingProperties(30, 120, 30, 15, 15,
                ZoneId.of("Asia/Ho_Chi_Minh"), new AppointmentBookingProperties.PublicRateLimit(60, 60), 15, 30000L, false);
        handler = new RescheduleAppointmentServiceImpl(guard, appointments, auth, slots, properties,
                Clock.fixed(now, ZoneId.of("UTC")),
                mock(fu.fa26.sep490.g58.otcms_be.notification.service.NotificationPublisher.class),
                mock(fu.fa26.sep490.g58.otcms_be.staff.service.StaffAvailabilityProviderQueryService.class), org.mockito.Mockito.mock(fu.fa26.sep490.g58.otcms_be.common.audit.AuditLogWriter.class));
        current = new ReschedulableAppointment(appointmentId, patientId, serviceId,
                doctorId, "CONFIRMED", "knee pain", now.plusSeconds(4 * 86400L), now.plusSeconds(4 * 86400L + 1800),
                30, 5, "PATIENT_ONLINE", null, null, now.minusSeconds(86400));
        when(appointments.findForUpdate(appointmentId)).thenReturn(Optional.of(current));
        when(auth.authorize(accountId, patientId)).thenReturn(new AuthorizePatientProfileService.AuthorizedPatientProfile(
                patientId, AuthorizePatientProfileService.AuthorizationScope.SELF));
    }

    @Test
    void receptionistRescheduleUsesGuardLockedSnapshotWithoutPatientAuthorization() {
        Instant target = Instant.parse("2026-10-17T04:00:00Z");
        var expected = new CreatedAppointment(appointmentId, patientId, serviceId, doctorId, "CONFIRMED",
                "PATIENT_ONLINE", "knee pain", target, target.plusSeconds(1800), 30, 5,
                target.plusSeconds(2100), null, null, current.createdAt(), now);
        when(slots.generate(any())).thenReturn(List.of(new AvailableSlot(serviceId, doctorId, target,
                target.plusSeconds(1800), target.plusSeconds(2100))));
        when(appointments.reschedule(any(), eq(target), eq(target.plusSeconds(1800)), eq(accountId),
                eq("RECEPTIONIST"), eq("operational reason"), eq(now), any())).thenReturn(expected);
        String correlationId = UUID.randomUUID().toString();

        var result = handler.reschedule(appointmentId, new RescheduleAppointmentCommand(accountId,
                RescheduleAppointmentCommand.Actor.RECEPTIONIST, OffsetDateTime.ofInstant(target, ZoneId.of("UTC")),
                "operational reason", correlationId));

        assertEquals(expected, result);
        InOrder order = inOrder(guard, appointments);
        order.verify(guard).acquire();
        order.verify(appointments).findForUpdate(appointmentId);
        verifyNoInteractions(auth);
        var request = org.mockito.ArgumentCaptor.forClass(SlotAvailabilityRequest.class);
        verify(slots).generate(request.capture());
        assertEquals(Set.of(doctorId), request.getValue().doctorProfileIds());
        assertEquals(appointmentId, request.getValue().serviceContext().excludedAppointmentId());
        assertEquals(patientId, request.getValue().serviceContext().patientProfileId());
        assertEquals(30, request.getValue().serviceContext().consultationDuration().toMinutes());
        assertEquals(5, request.getValue().serviceContext().bufferDuration().toMinutes());
        verify(appointments).reschedule(current, target, target.plusSeconds(1800), accountId, "RECEPTIONIST",
                "operational reason", now, correlationId);
    }

    @Test
    void receptionistMustSupplyReasonBeforeGuardOrPersistenceAccess() {
        var command = new RescheduleAppointmentCommand(accountId, RescheduleAppointmentCommand.Actor.RECEPTIONIST,
                OffsetDateTime.parse("2026-10-17T04:00:00Z"), "  ", UUID.randomUUID().toString());

        var failure = assertThrows(AppointmentBookingException.class,
                () -> handler.reschedule(appointmentId, command));

        assertEquals("VALIDATION_FAILED", failure.code());
        verifyNoInteractions(guard, appointments, slots);
    }

    @Test
    void patientCannotRescheduleOwnBookingAtAnyTimeAndNothingIsWritten() {
        Instant target = now.plusSeconds(3 * 86400L);
        var command = new RescheduleAppointmentCommand(accountId, RescheduleAppointmentCommand.Actor.PATIENT,
                OffsetDateTime.ofInstant(target, ZoneId.of("UTC")), null, UUID.randomUUID().toString());

        var failure = assertThrows(AppointmentBookingException.class, () -> handler.reschedule(appointmentId, command));

        assertEquals("APPOINTMENT_NOT_CHANGEABLE", failure.code());
        verify(auth).authorize(accountId, patientId);
        verifyNoInteractions(slots);
        verify(appointments, never()).reschedule(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void patientOfAnotherProfileGetsNotFoundBeforeTheSelfRescheduleRule() {
        when(auth.authorize(accountId, patientId)).thenThrow(new PatientAuthorizationDeniedException());
        var command = new RescheduleAppointmentCommand(accountId, RescheduleAppointmentCommand.Actor.PATIENT,
                OffsetDateTime.ofInstant(now.plusSeconds(3 * 86400L), ZoneId.of("UTC")), null, UUID.randomUUID().toString());

        assertEquals("APPOINTMENT_NOT_FOUND", assertThrows(AppointmentBookingException.class,
                () -> handler.reschedule(appointmentId, command)).code());
        verifyNoInteractions(slots);
        verify(appointments, never()).reschedule(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void receptionistHasNoTwoHourLimitOnAnAppointmentThatStartsSoon() {
        current = new ReschedulableAppointment(appointmentId, patientId, serviceId, doctorId, "CONFIRMED",
                "knee pain", now.plusSeconds(30 * 60L), now.plusSeconds(30 * 60L + 1800), 30, 5,
                "PATIENT_ONLINE", null, null, now.minusSeconds(86400));
        when(appointments.findForUpdate(appointmentId)).thenReturn(Optional.of(current));
        Instant target = now.plusSeconds(3 * 86400L);
        when(slots.generate(any())).thenReturn(List.of(new AvailableSlot(serviceId, doctorId, target,
                target.plusSeconds(1800), target.plusSeconds(2100))));
        var expected = new CreatedAppointment(appointmentId, patientId, serviceId, doctorId, "CONFIRMED",
                "PATIENT_ONLINE", "knee pain", target, target.plusSeconds(1800), 30, 5,
                target.plusSeconds(2100), null, null, current.createdAt(), now);
        doReturn(expected).when(appointments).reschedule(any(), any(), any(), any(), any(), any(), any(), any());
        var command = new RescheduleAppointmentCommand(accountId, RescheduleAppointmentCommand.Actor.RECEPTIONIST,
                OffsetDateTime.ofInstant(target, ZoneId.of("UTC")), "clinic request", UUID.randomUUID().toString());

        assertEquals(expected, handler.reschedule(appointmentId, command));
        verify(appointments).reschedule(current, target, target.plusSeconds(1800), accountId, "RECEPTIONIST",
                "clinic request", now, command.correlationId());
    }
}
