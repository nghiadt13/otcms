package fu.fa26.sep490.g58.otcms_be.appointment.service.impl;

import fu.fa26.sep490.g58.otcms_be.appointment.repository.AppointmentOccupancyRepository;
import fu.fa26.sep490.g58.otcms_be.appointment.service.CheckVisitReservationCapacityService;
import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentCapacityUnavailableException;
import fu.fa26.sep490.g58.otcms_be.appointment.service.CheckVisitReservationCapacityService.CapacityDecision;
import fu.fa26.sep490.g58.otcms_be.appointment.service.CheckVisitReservationCapacityService.VisitReservation;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Appointment-owned implementation of the bounded Visit care-capacity decision. */
@Service
public class CheckVisitReservationCapacityServiceImpl implements CheckVisitReservationCapacityService {
    private final AppointmentOccupancyRepository occupancy;

    public CheckVisitReservationCapacityServiceImpl(AppointmentOccupancyRepository occupancy) {
        this.occupancy = occupancy;
    }

    @Override
    public CapacityDecision checkVisitReservation(VisitReservation reservation) {
        if (reservation == null || reservation.doctorProfileId() == null || reservation.startInclusive() == null
                || reservation.endExclusive() == null || !reservation.startInclusive().isBefore(reservation.endExclusive())
                || reservation.visitId() == null || reservation.patientProfileId() == null) {
            throw new IllegalArgumentException("A Doctor, Visit, Patient, and positive interval are required");
        }
        UUID validatedLink = validateLink(reservation);
        if (reservation.linkedAppointmentId() != null && validatedLink == null) {
            return new CapacityDecision(false, reservation.linkedAppointmentId(), null);
        }
        boolean conflictsWithSelfBuffer = false;
        var conflict = occupancy.findOccupiedIntervals(java.util.List.of(reservation.doctorProfileId()),
                        reservation.startInclusive(), reservation.endExclusive(), validatedLink)
                .stream().findFirst();
        if (validatedLink != null) {
            var link = findValidatedLink(reservation);
            Instant appointmentStart = instant(link[4]);
            Instant appointmentEnd = instant(link[5]);
            Instant occupiedUntil = instant(link[6]);
            Instant protectedUntil = "COMPLETED".equals(link[3])
                    ? requiredInstant(link[7], "Completed appointment has no completion audit boundary")
                    : occupiedUntil;
            if (appointmentEnd.isBefore(protectedUntil)
                    && reservation.startInclusive().isBefore(protectedUntil)
                    && reservation.endExclusive().isAfter(appointmentEnd)) {
                conflictsWithSelfBuffer = true;
            }
        }
        return new CapacityDecision(conflict.isEmpty() && !conflictsWithSelfBuffer,
                conflict.map(row -> row.appointmentId()).orElse(conflictsWithSelfBuffer ? validatedLink : null),
                validatedLink);
    }

    private UUID validateLink(VisitReservation reservation) {
        UUID appointmentId = reservation.linkedAppointmentId();
        if (appointmentId == null) return null;
        return occupancy.findVisitLinkEvidence(appointmentId).stream()
                .filter(row -> reservation.patientProfileId().equals(row[0])
                        && reservation.visitId().equals(row[1])
                        && reservation.doctorProfileId().equals(row[2])
                        && java.util.Set.of("CHECKED_IN", "IN_PROGRESS", "COMPLETED").contains(row[3]))
                .map(row -> appointmentId)
                .findFirst().orElse(null);
    }

    private Object[] findValidatedLink(VisitReservation reservation) {
        return occupancy.findVisitLinkEvidence(reservation.linkedAppointmentId()).stream()
                .filter(row -> reservation.patientProfileId().equals(row[0])
                        && reservation.visitId().equals(row[1])
                        && reservation.doctorProfileId().equals(row[2])
                        && java.util.Set.of("CHECKED_IN", "IN_PROGRESS", "COMPLETED").contains(row[3]))
                .findFirst().orElseThrow(() -> new AppointmentCapacityUnavailableException(
                        "Validated Visit link disappeared during capacity evaluation"));
    }

    private static Instant requiredInstant(Object value, String message) {
        if (value == null) throw new AppointmentCapacityUnavailableException(message);
        return instant(value);
    }

    private static Instant instant(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof java.time.OffsetDateTime offset) return offset.toInstant();
        throw new AppointmentCapacityUnavailableException("Unexpected appointment instant projection");
    }
}
