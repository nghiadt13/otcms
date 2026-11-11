package fu.fa26.sep490.g58.otcms_be.appointment;

import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentBookingException;
import fu.fa26.sep490.g58.otcms_be.appointment.model.CheckInAppointment;
import fu.fa26.sep490.g58.otcms_be.appointment.model.CheckInReplay;
import fu.fa26.sep490.g58.otcms_be.appointment.model.CheckedInAppointment;
import fu.fa26.sep490.g58.otcms_be.appointment.repository.AppointmentCheckInRepository;
import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentBillingSourceService;
import fu.fa26.sep490.g58.otcms_be.appointment.service.CheckInAppointmentCommand;
import fu.fa26.sep490.g58.otcms_be.appointment.service.impl.CheckInAppointmentTransaction;
import fu.fa26.sep490.g58.otcms_be.billing.service.AppointmentInvoicePaidStatusQueryService;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import fu.fa26.sep490.g58.otcms_be.visit.exception.ReceptionWorkflowException;
import fu.fa26.sep490.g58.otcms_be.visit.model.ExamQueueTicket;
import fu.fa26.sep490.g58.otcms_be.visit.service.ReceptionWorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CheckInAppointmentServiceImplT027Test {
    private final UUID appointmentId = UUID.randomUUID();
    private final UUID patientId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID doctorId = UUID.randomUUID();
    private final UUID visitId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();
    // 12:00 in clinic time (UTC+7) on 2026-10-14; the appointment below starts at 12:30 the same day.
    private final Instant now = Instant.parse("2026-10-14T05:00:00Z");
    private final ExamQueueTicket ticket = new ExamQueueTicket(3, doctorId, "Dr. Test", null, null);
    private final CoordinationGuardService guard = mock(CoordinationGuardService.class);
    private final AppointmentCheckInRepository appointments = mock(AppointmentCheckInRepository.class);
    private final ReceptionWorkflowService reception = mock(ReceptionWorkflowService.class);
    private final AppointmentBillingSourceService billingSources = mock(AppointmentBillingSourceService.class);
    private final AppointmentInvoicePaidStatusQueryService invoicePaidStatus =
            mock(AppointmentInvoicePaidStatusQueryService.class);
    private final AppointmentBookingProperties properties = new AppointmentBookingProperties(30, 1440, 30, 15, 15,
            ZoneId.of("Asia/Ho_Chi_Minh"), new AppointmentBookingProperties.PublicRateLimit(60, 60), 15, 30000L, false);
    private CheckInAppointmentTransaction handler;
    private CheckInAppointmentCommand command;
    private CheckInAppointment current;

    @BeforeEach
    void setUp() {
        handler = handlerAt(now);
        command = new CheckInAppointmentCommand(actorId, patientId, "t027-key", "trace-id");
        current = new CheckInAppointment(appointmentId, patientId, doctorId,
                "CONFIRMED", null, null, now.plusSeconds(1800));
        when(appointments.findReplay(actorId, appointmentId, "t027-key")).thenReturn(Optional.empty());
        when(appointments.findForUpdate(appointmentId)).thenReturn(Optional.of(current));
        when(billingSources.requireSource(appointmentId)).thenReturn(source(BigDecimal.ZERO));
        when(reception.checkInAtomically(any())).thenReturn(checkedInAt(now));
        when(reception.findExamQueueTicket(visitId)).thenReturn(Optional.of(ticket));
    }

    @Test
    void checksInOnTheClinicDayWithoutMinuteWindowAndReturnsTheQueueTicket() {
        var result = handler.checkIn(appointmentId, command);

        assertEquals(new CheckedInAppointment(appointmentId, visitId, now, ticket), result);
        InOrder order = inOrder(guard, appointments, reception);
        order.verify(guard).acquire();
        order.verify(appointments).findReplay(actorId, appointmentId, "t027-key");
        order.verify(appointments).findForUpdate(appointmentId);
        var request = ArgumentCaptor.forClass(ReceptionWorkflowService.CheckInCommand.class);
        verify(reception).checkInAtomically(request.capture());
        assertEquals(doctorId, request.getValue().doctorProfileId());
        assertEquals(patientId, request.getValue().patientProfileId());
        verify(appointments).completeCheckIn(eq(current), eq(actorId), eq(patientId), eq("t027-key"), anyString(),
                eq(visitId), eq(now), eq("trace-id"));
    }

    @Test
    void checksInHoursBeforeTheAppointmentWhenItIsTheSameClinicDay() {
        // 07:00 clinic time for a 15:00 clinic-time appointment: the old -30/+15 minute window is gone.
        Instant morning = Instant.parse("2026-10-14T00:00:00Z");
        current = new CheckInAppointment(appointmentId, patientId, doctorId,
                "CONFIRMED", null, null, Instant.parse("2026-10-14T08:00:00Z"));
        when(appointments.findForUpdate(appointmentId)).thenReturn(Optional.of(current));
        when(reception.checkInAtomically(any())).thenReturn(checkedInAt(morning));

        var result = handlerAt(morning).checkIn(appointmentId, command);

        assertEquals(new CheckedInAppointment(appointmentId, visitId, morning, ticket), result);
        verify(appointments).completeCheckIn(eq(current), eq(actorId), eq(patientId), eq("t027-key"), anyString(),
                eq(visitId), eq(morning), eq("trace-id"));
    }

    @Test
    void rejectsAppointmentOnAnotherClinicDayBeforeCreatingVisit() {
        // Yesterday (15:00 clinic time) and tomorrow (10:00 clinic time) are both refused.
        for (Instant start : List.of(Instant.parse("2026-10-13T08:00:00Z"), Instant.parse("2026-10-15T03:00:00Z"))) {
            current = new CheckInAppointment(appointmentId, patientId, doctorId, "CONFIRMED", null, null, start);
            when(appointments.findForUpdate(appointmentId)).thenReturn(Optional.of(current));
            assertEquals("CHECK_IN_TIME_NOT_ALLOWED", assertThrows(AppointmentBookingException.class,
                    () -> handler.checkIn(appointmentId, command)).code());
        }
        verifyNoInteractions(reception, invoicePaidStatus);
        verify(appointments, never()).completeCheckIn(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void authorizedReplayPrecedesCurrentStateDayFeeAndDoesNotRepeatWrites() {
        when(appointments.findReplay(actorId, appointmentId, "t027-key"))
                .thenReturn(Optional.of(new CheckInReplay(fingerprint(), visitId, now.minusSeconds(3600))));

        assertEquals(new CheckedInAppointment(appointmentId, visitId, now.minusSeconds(3600), ticket),
                handler.checkIn(appointmentId, command));
        verify(guard).acquire();
        verify(appointments, never()).findForUpdate(any());
        verify(reception, never()).checkInAtomically(any());
        verifyNoInteractions(billingSources, invoicePaidStatus);
        verify(appointments, never()).completeCheckIn(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void replayOfVisitWithoutQueueRowReturnsNoTicket() {
        when(appointments.findReplay(actorId, appointmentId, "t027-key"))
                .thenReturn(Optional.of(new CheckInReplay(fingerprint(), visitId, now.minusSeconds(3600))));
        when(reception.findExamQueueTicket(visitId)).thenReturn(Optional.empty());

        assertEquals(new CheckedInAppointment(appointmentId, visitId, now.minusSeconds(3600), null),
                handler.checkIn(appointmentId, command));
    }

    @Test
    void rejectsChangedReplayPayloadWithoutTouchingState() {
        when(appointments.findReplay(actorId, appointmentId, "t027-key"))
                .thenReturn(Optional.of(new CheckInReplay("different", visitId, now)));

        assertEquals("IDEMPOTENCY_CONFLICT", assertThrows(AppointmentBookingException.class,
                () -> handler.checkIn(appointmentId, command)).code());
        verify(appointments, never()).findForUpdate(any());
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
        verify(appointments, never()).completeCheckIn(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsAttestationForAnotherPatientBeforeCallingVisit() {
        command = new CheckInAppointmentCommand(actorId, UUID.randomUUID(), "t027-key", "trace-id");

        assertEquals("PATIENT_IDENTITY_MISMATCH", assertThrows(AppointmentBookingException.class,
                () -> handler.checkIn(appointmentId, command)).code());
        verifyNoInteractions(reception);
    }

    @Test
    void rejectsUnpaidExamFeeBeforeCallingVisit() {
        when(billingSources.requireSource(appointmentId)).thenReturn(source(new BigDecimal("250000")));
        when(invoicePaidStatus.paidStatus(List.of(appointmentId))).thenReturn(Map.of(appointmentId, false));

        assertEquals("EXAM_FEE_UNPAID", assertThrows(AppointmentBookingException.class,
                () -> handler.checkIn(appointmentId, command)).code());
        verifyNoInteractions(reception);
        verify(appointments, never()).completeCheckIn(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void missingAppointmentInvoiceCountsAsUnpaidExamFee() {
        // The price is set but no APPOINTMENT invoice exists yet: no entry in the paid map means unpaid.
        when(billingSources.requireSource(appointmentId)).thenReturn(source(new BigDecimal("250000")));
        when(invoicePaidStatus.paidStatus(List.of(appointmentId))).thenReturn(Map.of());

        assertEquals("EXAM_FEE_UNPAID", assertThrows(AppointmentBookingException.class,
                () -> handler.checkIn(appointmentId, command)).code());
        verifyNoInteractions(reception);
    }

    @Test
    void acceptsPaidExamFeeAndQueuesTheVisit() {
        when(billingSources.requireSource(appointmentId)).thenReturn(source(new BigDecimal("250000")));
        when(invoicePaidStatus.paidStatus(List.of(appointmentId))).thenReturn(Map.of(appointmentId, true));

        assertEquals(new CheckedInAppointment(appointmentId, visitId, now, ticket), handler.checkIn(appointmentId, command));
        verify(reception).checkInAtomically(any());
    }

    @Test
    void zeroPriceServiceNeedsNoPayment() {
        when(billingSources.requireSource(appointmentId)).thenReturn(source(BigDecimal.ZERO));

        assertEquals(new CheckedInAppointment(appointmentId, visitId, now, ticket), handler.checkIn(appointmentId, command));
        verifyNoInteractions(invoicePaidStatus);
    }

    @Test
    void missingServicePriceNeedsNoPayment() {
        when(billingSources.requireSource(appointmentId)).thenReturn(source(null));

        assertEquals(new CheckedInAppointment(appointmentId, visitId, now, ticket), handler.checkIn(appointmentId, command));
        verifyNoInteractions(invoicePaidStatus);
    }

    @Test
    void surfacesVisitSideRejectionCodeWithoutCompletingCheckIn() {
        when(reception.checkInAtomically(any()))
                .thenThrow(new ReceptionWorkflowException("VISIT_LINK_CONFLICT"));

        assertEquals("VISIT_LINK_CONFLICT", assertThrows(AppointmentBookingException.class,
                () -> handler.checkIn(appointmentId, command)).code());
        verify(appointments, never()).completeCheckIn(any(), any(), any(), any(), any(), any(), any(), any());
    }

    private CheckInAppointmentTransaction handlerAt(Instant clockNow) {
        return new CheckInAppointmentTransaction(guard, appointments, reception, billingSources, invoicePaidStatus,
                properties, Clock.fixed(clockNow, ZoneOffset.UTC));
    }

    private AppointmentBillingSourceService.BillingSource source(BigDecimal price) {
        return new AppointmentBillingSourceService.BillingSource(appointmentId, patientId, serviceId, "CONFIRMED",
                "Consultation", price);
    }

    private ReceptionWorkflowService.CheckInResult checkedInAt(Instant at) {
        return new ReceptionWorkflowService.CheckInResult(appointmentId, visitId, patientId, at, at, false, ticket);
    }

    private String fingerprint() {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(("appointment-check-in-v2:" + patientId).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
