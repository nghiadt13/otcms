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
import fu.fa26.sep490.g58.otcms_be.visit.service.ReceptionWorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CheckInAppointmentServiceImplT027Test {
    private final UUID appointmentId = UUID.randomUUID();
    private final UUID patientId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID doctorId = UUID.randomUUID();
    private final UUID visitId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-14T05:00:00Z");
    private final CoordinationGuardService guard = mock(CoordinationGuardService.class);
    private final AppointmentCheckInRepository appointments = mock(AppointmentCheckInRepository.class);
    private final ReceptionWorkflowService reception = mock(ReceptionWorkflowService.class);
    private CheckInAppointmentTransaction handler;
    private CheckInAppointmentCommand command;
    private CheckInAppointment current;

    @BeforeEach
    void setUp() {
        var properties = new AppointmentBookingProperties(30, 120, 1440, 30, 15, 15,
                ZoneId.of("Asia/Ho_Chi_Minh"), new AppointmentBookingProperties.PublicRateLimit(60, 60));
        handler = new CheckInAppointmentTransaction(guard, appointments, reception, properties);
        command = new CheckInAppointmentCommand(actorId, "t027-key", "trace-id");
        current = new CheckInAppointment(appointmentId, patientId, doctorId,
                "CONFIRMED", null, null, now.plusSeconds(1800));
        when(appointments.findReplay(actorId, appointmentId, "t027-key")).thenReturn(Optional.empty());
        when(appointments.findForUpdate(appointmentId)).thenReturn(Optional.of(current));
        when(reception.checkInAtomically(any())).thenReturn(new ReceptionWorkflowService.CheckInResult(
                appointmentId, visitId, patientId, now, now, false));
        when(appointments.completeCheckIn(eq(current), eq(actorId), eq("t027-key"), anyString(), eq(visitId),
                eq(now), eq("trace-id"))).thenReturn(new CheckedInAppointment(appointmentId, visitId, now));
    }

    @Test
    void checksInAtInclusiveEarlyBoundaryAndPersistsThroughAllPorts() {
        var result = handler.checkIn(appointmentId, command);

        assertEquals(new CheckedInAppointment(appointmentId, visitId, now), result);
        InOrder order = inOrder(guard, appointments, reception);
        order.verify(guard).acquire();
        order.verify(appointments).findReplay(actorId, appointmentId, "t027-key");
        order.verify(appointments).findForUpdate(appointmentId);
        var request = org.mockito.ArgumentCaptor.forClass(ReceptionWorkflowService.CheckInCommand.class);
        verify(reception).checkInAtomically(request.capture());
        assertEquals(now, request.getValue().allowedWindowStartInclusive());
        assertEquals(now.plusSeconds(2700), request.getValue().allowedWindowEndInclusive());
        verify(appointments).completeCheckIn(eq(current), eq(actorId), eq("t027-key"), anyString(),
                eq(visitId), eq(now), eq("trace-id"));
    }

    @Test
    void authorizedReplayPrecedesCurrentStateAndWindowAndDoesNotRepeatWrites() {
        when(appointments.findReplay(actorId, appointmentId, "t027-key"))
                .thenReturn(Optional.of(new CheckInReplay(
                        fingerprint(), visitId, now.minusSeconds(3600))));

        assertEquals(new CheckedInAppointment(appointmentId, visitId, now.minusSeconds(3600)),
                handler.checkIn(appointmentId, command));
        verify(guard).acquire();
        verify(appointments, never()).findForUpdate(any());
        verifyNoInteractions(reception);
        verify(appointments, never()).completeCheckIn(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsChangedReplayPayloadAndInvalidStateOrTimeWithoutCreatingVisit() {
        when(appointments.findReplay(actorId, appointmentId, "t027-key"))
                .thenReturn(Optional.of(new CheckInReplay("different", visitId, now)));
        assertEquals("IDEMPOTENCY_CONFLICT", assertThrows(AppointmentBookingException.class,
                () -> handler.checkIn(appointmentId, command)).code());
        verify(appointments, never()).findForUpdate(any());

        when(appointments.findReplay(actorId, appointmentId, "t027-key")).thenReturn(Optional.empty());
        when(appointments.findForUpdate(appointmentId)).thenReturn(Optional.of(new CheckInAppointment(
                appointmentId, patientId, doctorId, "CONFIRMED", null, null, now.plusSeconds(1801))));
        when(reception.checkInAtomically(any())).thenThrow(
                new fu.fa26.sep490.g58.otcms_be.visit.exception.ReceptionWorkflowException("CHECK_IN_TIME_NOT_ALLOWED"));
        assertEquals("CHECK_IN_TIME_NOT_ALLOWED", assertThrows(AppointmentBookingException.class,
                () -> handler.checkIn(appointmentId, command)).code());
        verify(reception).checkInAtomically(any());
        verify(appointments, never()).completeCheckIn(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsAnInconsistentExistingVisitLinkBeforeCreatingAnotherVisit() {
        var linked = new CheckInAppointment(appointmentId, patientId, doctorId,
                "CHECKED_IN", visitId, null, now.plusSeconds(1800));
        when(appointments.findForUpdate(appointmentId)).thenReturn(Optional.of(linked));

        assertEquals("VISIT_LINK_CONFLICT", assertThrows(AppointmentBookingException.class,
                () -> handler.checkIn(appointmentId, command)).code());
        verify(reception).hasConsistentCheckInEvidence(appointmentId, patientId, visitId);
        verify(reception, never()).checkInAtomically(any());
        verify(appointments, never()).completeCheckIn(any(), any(), any(), any(), any(), any(), any());
    }

    private String fingerprint() {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest("appointment-check-in-v1:{}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
