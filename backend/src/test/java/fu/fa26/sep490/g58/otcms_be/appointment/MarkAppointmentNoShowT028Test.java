package fu.fa26.sep490.g58.otcms_be.appointment;

import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentBookingException;
import fu.fa26.sep490.g58.otcms_be.appointment.model.CreatedAppointment;
import fu.fa26.sep490.g58.otcms_be.appointment.model.NoShowAppointment;
import fu.fa26.sep490.g58.otcms_be.appointment.repository.AppointmentNoShowRepository;
import fu.fa26.sep490.g58.otcms_be.appointment.service.MarkAppointmentNoShowCommand;
import fu.fa26.sep490.g58.otcms_be.appointment.service.impl.MarkAppointmentNoShowTransaction;
import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.visit.service.RecordedArrivalQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.exception.RecordedArrivalNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class MarkAppointmentNoShowT028Test {
    private final Instant now = Instant.parse("2026-10-18T05:00:00Z");
    private final UUID appointmentId = UUID.randomUUID();
    private final UUID patientId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID doctorId = UUID.randomUUID();
    private final CoordinationGuardService guard = mock(CoordinationGuardService.class);
    private final AppointmentNoShowRepository appointments = mock(AppointmentNoShowRepository.class);
    private final RecordedArrivalQueryService arrivals = mock(RecordedArrivalQueryService.class);
    private final MarkAppointmentNoShowTransaction transaction = new MarkAppointmentNoShowTransaction(guard,
            appointments, arrivals, new AppointmentBookingProperties(30, 120, 1440, 30, 15, 15,
                    ZoneId.of("Asia/Ho_Chi_Minh"), new AppointmentBookingProperties.PublicRateLimit(60, 60)),
            Clock.fixed(now, ZoneId.of("UTC")));
    private final MarkAppointmentNoShowCommand command = new MarkAppointmentNoShowCommand(actorId, "  absent  ", "trace-id");
    private final NoShowAppointment confirmed = new NoShowAppointment(appointmentId, patientId, doctorId,
            "CONFIRMED", now.minusSeconds(3600), now.minusSeconds(1800), null, null);

    @BeforeEach
    void setUp() {
        when(appointments.findForUpdate(appointmentId)).thenReturn(Optional.of(confirmed));
        when(arrivals.requireRecordedArrival(any(), any(), any(), any()))
                .thenThrow(new RecordedArrivalNotFoundException());
    }

    @Test
    void absenceEvidenceAfterStrictGraceMarksAndAuditsInsideGuardedFlow() {
        CreatedAppointment result = mock(CreatedAppointment.class);
        when(appointments.markNoShow(confirmed, actorId, "absent", now, "trace-id")).thenReturn(result);

        assertEquals(result, transaction.markNoShow(appointmentId, command));
        org.mockito.InOrder order = inOrder(guard, appointments, arrivals);
        order.verify(guard).acquire();
        order.verify(appointments).findForUpdate(appointmentId);
        order.verify(arrivals).requireRecordedArrival(appointmentId, patientId,
                now.minusSeconds(5400), now.minusSeconds(2700));
        verify(appointments).markNoShow(confirmed, actorId, "absent", now, "trace-id");
    }

    @Test
    void graceBoundaryIsStrictAndDoesNotQueryReception() {
        var boundaryClock = Clock.fixed(now.minusSeconds(1), ZoneId.of("UTC"));
        var boundaryTransaction = new MarkAppointmentNoShowTransaction(guard, appointments, arrivals,
                new AppointmentBookingProperties(30, 120, 1440, 30, 15, 15, ZoneId.of("UTC"),
                        new AppointmentBookingProperties.PublicRateLimit(60, 60)), boundaryClock);
        // Set appointment start so now-minus-one equals start plus the 15 minute grace.
        when(appointments.findForUpdate(appointmentId)).thenReturn(Optional.of(new NoShowAppointment(appointmentId,
                patientId, doctorId, "CONFIRMED", now.minusSeconds(901), now.minusSeconds(1), null, null)));

        assertEquals("NO_SHOW_NOT_ALLOWED", assertThrows(AppointmentBookingException.class,
                () -> boundaryTransaction.markNoShow(appointmentId, command)).code());
        verifyNoInteractions(arrivals);
        verify(appointments, never()).markNoShow(any(), any(), any(), any(), any());
    }

    @Test
    void timelyArrivalBlocksNoShowAndUnavailableEvidenceFailsClosed() {
        doReturn(new RecordedArrivalQueryService.RecordedArrival(appointmentId, patientId,
                now.minusSeconds(3000), UUID.randomUUID())).when(arrivals)
                .requireRecordedArrival(appointmentId, patientId, now.minusSeconds(5400), now.minusSeconds(2700));
        assertEquals("NO_SHOW_NOT_ALLOWED", assertThrows(AppointmentBookingException.class,
                () -> transaction.markNoShow(appointmentId, command)).code());

        doThrow(new IllegalStateException("EVIDENCE_STORE_UNAVAILABLE")).when(arrivals)
                .requireRecordedArrival(any(), any(), any(), any());
        assertEquals("DEPENDENCY_UNAVAILABLE", assertThrows(AppointmentBookingException.class,
                () -> transaction.markNoShow(appointmentId, command)).code());
        verify(appointments, never()).markNoShow(any(), any(), any(), any(), any());
    }
}
