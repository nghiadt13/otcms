package fu.fa26.sep490.g58.otcms_be.appointment.service;

import java.time.Instant;
import java.util.UUID;

/** Public Appointment use case for validating care reservations created by Visit. */
public interface CheckVisitReservationCapacityService {
    CapacityDecision checkVisitReservation(VisitReservation reservation);

    class VisitReservation {
        private final UUID doctorProfileId;
        private final Instant startInclusive;
        private final Instant endExclusive;
        private final UUID visitId;
        private final UUID linkedAppointmentId;
        private final UUID patientProfileId;

        public VisitReservation(UUID doctorProfileId, Instant startInclusive, Instant endExclusive, UUID visitId,
                UUID linkedAppointmentId, UUID patientProfileId) {
            this.doctorProfileId = doctorProfileId;
            this.startInclusive = startInclusive;
            this.endExclusive = endExclusive;
            this.visitId = visitId;
            this.linkedAppointmentId = linkedAppointmentId;
            this.patientProfileId = patientProfileId;
        }

        public UUID doctorProfileId() { return doctorProfileId; }
        public Instant startInclusive() { return startInclusive; }
        public Instant endExclusive() { return endExclusive; }
        public UUID visitId() { return visitId; }
        public UUID linkedAppointmentId() { return linkedAppointmentId; }
        public UUID patientProfileId() { return patientProfileId; }
    }

    class CapacityDecision {
        private final boolean available;
        private final UUID conflictingAppointmentId;
        private final UUID validatedLinkedAppointmentId;

        public CapacityDecision(boolean available, UUID conflictingAppointmentId, UUID validatedLinkedAppointmentId) {
            this.available = available;
            this.conflictingAppointmentId = conflictingAppointmentId;
            this.validatedLinkedAppointmentId = validatedLinkedAppointmentId;
        }

        public boolean available() { return available; }
        public UUID conflictingAppointmentId() { return conflictingAppointmentId; }
        public UUID validatedLinkedAppointmentId() { return validatedLinkedAppointmentId; }
    }
}
