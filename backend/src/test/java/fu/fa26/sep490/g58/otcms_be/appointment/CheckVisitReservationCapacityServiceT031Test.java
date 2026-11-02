package fu.fa26.sep490.g58.otcms_be.appointment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fu.fa26.sep490.g58.otcms_be.appointment.service.impl.CheckVisitReservationCapacityServiceImpl;
import fu.fa26.sep490.g58.otcms_be.appointment.model.AppointmentOccupancy;
import fu.fa26.sep490.g58.otcms_be.appointment.model.AvailabilityInterval;
import fu.fa26.sep490.g58.otcms_be.appointment.repository.AppointmentOccupancyRepository;
import fu.fa26.sep490.g58.otcms_be.appointment.service.CheckVisitReservationCapacityService.VisitReservation;
import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentCapacityUnavailableException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CheckVisitReservationCapacityServiceT031Test {
    private final AppointmentOccupancyRepository occupancy = Mockito.mock(AppointmentOccupancyRepository.class);
    private final CheckVisitReservationCapacityServiceImpl service = new CheckVisitReservationCapacityServiceImpl(occupancy);
    private final UUID doctorId = UUID.randomUUID();
    private final UUID visitId = UUID.randomUUID();
    private final UUID patientId = UUID.randomUUID();
    private final UUID appointmentId = UUID.randomUUID();
    private final Instant start = Instant.parse("2026-10-19T10:00:00Z");
    private final Instant end = start.plusSeconds(900);

    @Test
    void validatesExactLinkedAppointmentBeforeExcludingItFromOccupancy() {
        when(occupancy.findVisitLinkEvidence(appointmentId))
                .thenReturn(java.util.Collections.singletonList(new Object[]{patientId, visitId, doctorId, "IN_PROGRESS",
                        start.minusSeconds(1800), end, end, null}));
        when(occupancy.findOccupiedIntervals(List.of(doctorId), start, end, appointmentId)).thenReturn(List.of());

        var decision = service.checkVisitReservation(new VisitReservation(doctorId, start, end, visitId,
                appointmentId, patientId));

        assertTrue(decision.available());
        assertEquals(appointmentId, decision.validatedLinkedAppointmentId());
        verify(occupancy).findOccupiedIntervals(List.of(doctorId), start, end, appointmentId);
    }

    @Test
    void preservesLinkedAppointmentBufferDuringExtension() {
        Instant appointmentEnd = start;
        Instant occupiedUntil = start.plusSeconds(600);
        Instant extensionEnd = start.plusSeconds(900);
        when(occupancy.findVisitLinkEvidence(appointmentId)).thenReturn(java.util.Collections.singletonList(
                new Object[]{patientId, visitId, doctorId, "IN_PROGRESS", start.minusSeconds(1800),
                        appointmentEnd, occupiedUntil, null}));
        when(occupancy.findOccupiedIntervals(List.of(doctorId), appointmentEnd, extensionEnd, appointmentId))
                .thenReturn(List.of());

        var decision = service.checkVisitReservation(new VisitReservation(doctorId, appointmentEnd, extensionEnd,
                visitId, appointmentId, patientId));

        assertFalse(decision.available());
        assertEquals(appointmentId, decision.conflictingAppointmentId());
        assertEquals(appointmentId, decision.validatedLinkedAppointmentId());
    }

    @Test
    void completedAppointmentBufferUsesActualCompletionAsReleasedBoundary() {
        Instant appointmentEnd = start;
        Instant occupiedUntil = start.plusSeconds(600);
        Instant completedAt = start.plusSeconds(120);
        Instant extensionStart = completedAt.plusSeconds(60);
        Instant extensionEnd = extensionStart.plusSeconds(600);
        when(occupancy.findVisitLinkEvidence(appointmentId)).thenReturn(java.util.Collections.singletonList(
                new Object[]{patientId, visitId, doctorId, "COMPLETED", start.minusSeconds(1800),
                        appointmentEnd, occupiedUntil, completedAt}));
        when(occupancy.findOccupiedIntervals(List.of(doctorId), extensionStart, extensionEnd, appointmentId))
                .thenReturn(List.of());

        var decision = service.checkVisitReservation(new VisitReservation(doctorId, extensionStart, extensionEnd,
                visitId, appointmentId, patientId));

        assertTrue(decision.available());
        assertEquals(appointmentId, decision.validatedLinkedAppointmentId());
    }

    @Test
    void completedAppointmentStillProtectsBufferUntilActualCompletion() {
        Instant appointmentEnd = start;
        Instant occupiedUntil = start.plusSeconds(600);
        Instant completedAt = start.plusSeconds(300);
        Instant extensionStart = start.plusSeconds(120);
        Instant extensionEnd = start.plusSeconds(240);
        when(occupancy.findVisitLinkEvidence(appointmentId)).thenReturn(java.util.Collections.singletonList(
                new Object[]{patientId, visitId, doctorId, "COMPLETED", start.minusSeconds(1800),
                        appointmentEnd, occupiedUntil, completedAt}));
        when(occupancy.findOccupiedIntervals(List.of(doctorId), extensionStart, extensionEnd, appointmentId))
                .thenReturn(List.of());

        var decision = service.checkVisitReservation(new VisitReservation(doctorId, extensionStart, extensionEnd,
                visitId, appointmentId, patientId));

        assertFalse(decision.available());
        assertEquals(appointmentId, decision.conflictingAppointmentId());
    }

    @Test
    void completedAppointmentReleasesBufferAtCompletionWhenCompletionPrecedesScheduledEnd() {
        Instant appointmentEnd = start.plusSeconds(600);
        Instant occupiedUntil = start.plusSeconds(900);
        Instant completedAt = start.plusSeconds(300);
        Instant extensionStart = completedAt;
        Instant extensionEnd = completedAt.plusSeconds(120);
        when(occupancy.findVisitLinkEvidence(appointmentId)).thenReturn(java.util.Collections.singletonList(
                new Object[]{patientId, visitId, doctorId, "COMPLETED", start.minusSeconds(1800),
                        appointmentEnd, occupiedUntil, completedAt}));
        when(occupancy.findOccupiedIntervals(List.of(doctorId), extensionStart, extensionEnd, appointmentId))
                .thenReturn(List.of());

        var decision = service.checkVisitReservation(new VisitReservation(doctorId, extensionStart, extensionEnd,
                visitId, appointmentId, patientId));

        assertTrue(decision.available());
    }

    @Test
    void completedAppointmentWithoutCompletionAuditFailsClosedAsUnavailable() {
        when(occupancy.findVisitLinkEvidence(appointmentId)).thenReturn(java.util.Collections.singletonList(
                new Object[]{patientId, visitId, doctorId, "COMPLETED", start.minusSeconds(1800),
                        start, end, null}));
        when(occupancy.findOccupiedIntervals(List.of(doctorId), start, end, appointmentId)).thenReturn(List.of());

        assertThrows(AppointmentCapacityUnavailableException.class,
                () -> service.checkVisitReservation(new VisitReservation(doctorId, start, end, visitId,
                        appointmentId, patientId)));
    }

    @Test
    void invalidSelfLinkFailsWithoutExcludingAnyAppointment() {
        when(occupancy.findVisitLinkEvidence(appointmentId))
                .thenReturn(java.util.Collections.singletonList(new Object[]{UUID.randomUUID(), visitId, doctorId, "IN_PROGRESS",
                        start.minusSeconds(1800), end, end, null}));

        var decision = service.checkVisitReservation(new VisitReservation(doctorId, start, end, visitId,
                appointmentId, patientId));

        assertFalse(decision.available());
        assertNull(decision.validatedLinkedAppointmentId());
        verify(occupancy, never()).findOccupiedIntervals(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void confirmedAppointmentCannotBeUsedAsScheduledVisitSelfLink() {
        when(occupancy.findVisitLinkEvidence(appointmentId)).thenReturn(java.util.Collections.singletonList(
                new Object[]{patientId, visitId, doctorId, "CONFIRMED", start.minusSeconds(1800), end, end, null}));

        var decision = service.checkVisitReservation(new VisitReservation(doctorId, start, end, visitId,
                appointmentId, patientId));

        assertFalse(decision.available());
        assertNull(decision.validatedLinkedAppointmentId());
        verify(occupancy, never()).findOccupiedIntervals(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void returnsOtherAppointmentConflict() {
        UUID conflicting = UUID.randomUUID();
        when(occupancy.findOccupiedIntervals(List.of(doctorId), start, end, null)).thenReturn(List.of(
                new AppointmentOccupancy(conflicting, doctorId,
                        new AvailabilityInterval(start.plusSeconds(300), end.plusSeconds(300)))));

        var decision = service.checkVisitReservation(new VisitReservation(doctorId, start, end, visitId,
                null, patientId));

        assertFalse(decision.available());
        assertEquals(conflicting, decision.conflictingAppointmentId());
        assertNull(decision.validatedLinkedAppointmentId());
    }
}
