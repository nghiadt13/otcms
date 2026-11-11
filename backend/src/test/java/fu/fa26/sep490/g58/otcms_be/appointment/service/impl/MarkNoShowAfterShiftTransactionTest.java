package fu.fa26.sep490.g58.otcms_be.appointment.service.impl;

import fu.fa26.sep490.g58.otcms_be.appointment.model.NoShowAppointment;
import fu.fa26.sep490.g58.otcms_be.appointment.repository.AppointmentNoShowRepository;
import fu.fa26.sep490.g58.otcms_be.appointment.service.impl.MarkNoShowAfterShiftTransaction.Outcome;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import fu.fa26.sep490.g58.otcms_be.staff.model.DoctorWorkShift;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorScheduleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MarkNoShowAfterShiftTransactionTest {
    private final UUID appointmentId = UUID.randomUUID();
    private final UUID doctorId = UUID.randomUUID();
    private final UUID creatorAccountId = UUID.randomUUID();
    /** 10:00 clinic time on 2026-11-11. Its clinic day runs from 2026-11-10T17:00Z to 2026-11-11T17:00Z. */
    private final Instant start = Instant.parse("2026-11-11T03:00:00Z");
    private final Instant dayEnd = Instant.parse("2026-11-11T17:00:00Z");
    private final CoordinationGuardService guard = mock(CoordinationGuardService.class);
    private final AppointmentNoShowRepository appointments = mock(AppointmentNoShowRepository.class);
    private final DoctorScheduleService schedules = mock(DoctorScheduleService.class);
    private MarkNoShowAfterShiftTransaction transaction;

    @BeforeEach
    void setUp() {
        var properties = new AppointmentBookingProperties(30, 120, 30, 15, 15, ZoneId.of("Asia/Ho_Chi_Minh"),
                new AppointmentBookingProperties.PublicRateLimit(60, 60), 15, 30000L, false);
        transaction = new MarkNoShowAfterShiftTransaction(guard, appointments, schedules, properties);
        when(appointments.findForUpdate(appointmentId)).thenReturn(Optional.of(confirmed("CONFIRMED", null)));
        when(appointments.createdByAccountId(appointmentId)).thenReturn(creatorAccountId);
    }

    @Test
    void bookingIsDecidedOnlyStrictlyAfterTheCoveringShiftEndsAndAuditsTheSystemActor() {
        Instant shiftEnd = Instant.parse("2026-11-11T05:00:00Z");
        when(schedules.forProfile(eq(doctorId), any(), any())).thenReturn(List.of(
                new DoctorWorkShift(UUID.randomUUID(), doctorId, Instant.parse("2026-11-11T01:00:00Z"), shiftEnd)));

        assertEquals(Outcome.PENDING, transaction.markNoShow(appointmentId, shiftEnd));
        verify(appointments, never()).markNoShow(any(), any(), any(), any(), any(), any());

        Instant afterEnd = shiftEnd.plusSeconds(1);
        assertEquals(Outcome.MARKED, transaction.markNoShow(appointmentId, afterEnd));
        verify(guard, times(2)).acquire();
        verify(schedules, times(2)).forProfile(doctorId, Instant.parse("2026-11-10T17:00:00Z"), dayEnd);
        verify(appointments).markNoShow(any(NoShowAppointment.class), eq(creatorAccountId), eq("SYSTEM"),
                eq(MarkNoShowAfterShiftTransaction.SHIFT_ENDED_REASON), eq(afterEnd), eq("no-show-sweep-" + appointmentId));
    }

    @Test
    void shiftThatDoesNotCoverTheStartDoesNotDecideTheEnd() {
        when(schedules.forProfile(eq(doctorId), any(), any())).thenReturn(List.of(
                new DoctorWorkShift(UUID.randomUUID(), doctorId, Instant.parse("2026-11-11T00:00:00Z"),
                        Instant.parse("2026-11-11T02:00:00Z"))));

        assertEquals(Outcome.PENDING, transaction.markNoShow(appointmentId, Instant.parse("2026-11-11T03:30:00Z")));
        verify(appointments, never()).markNoShow(any(), any(), any(), any(), any(), any());
    }

    @Test
    void withoutCoveringShiftTheBookingIsDecidedAtTheEndOfItsClinicDay() {
        when(schedules.forProfile(eq(doctorId), any(), any())).thenReturn(List.of());

        assertEquals(Outcome.PENDING, transaction.markNoShow(appointmentId, dayEnd));
        assertEquals(Outcome.MARKED, transaction.markNoShow(appointmentId, dayEnd.plusSeconds(1)));
    }

    @Test
    void bookingThatIsNoLongerConfirmedOrHasVanishedIsNotCandidate() {
        when(appointments.findForUpdate(appointmentId)).thenReturn(Optional.of(
                confirmed("CHECKED_IN", UUID.randomUUID())));
        assertEquals(Outcome.NOT_CANDIDATE, transaction.markNoShow(appointmentId, dayEnd.plusSeconds(1)));

        when(appointments.findForUpdate(appointmentId)).thenReturn(Optional.empty());
        assertEquals(Outcome.NOT_CANDIDATE, transaction.markNoShow(appointmentId, dayEnd.plusSeconds(1)));

        verifyNoInteractions(schedules);
        verify(appointments, never()).markNoShow(any(), any(), any(), any(), any(), any());
    }

    private NoShowAppointment confirmed(String status, UUID visitId) {
        return new NoShowAppointment(appointmentId, UUID.randomUUID(), doctorId, status, start,
                start.plusSeconds(1800), visitId, null);
    }
}
