package fu.fa26.sep490.g58.otcms_be.appointment.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Public Appointment queries used by guarded Admin mutations. */
public interface AppointmentMutationImpactService {
    List<OccupiedBookingInterval> findProtectedBookingIntervals(UUID doctorId);
    long countBookingsOverlappingTimeOff(UUID doctorId, Instant fromInclusive, Instant toExclusive);
    long countBookingsForAssignment(UUID doctorId, UUID serviceId);

    class OccupiedBookingInterval {
        private final Instant startsAt;
        private final Instant occupiedUntil;

        public OccupiedBookingInterval(Instant startsAt, Instant occupiedUntil) {
            this.startsAt = startsAt;
            this.occupiedUntil = occupiedUntil;
        }

        public Instant startsAt() { return startsAt; }
        public Instant occupiedUntil() { return occupiedUntil; }
    }
}
