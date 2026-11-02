package fu.fa26.sep490.g58.otcms_be.visit.service;

import java.time.Instant;
import java.util.UUID;

/** Visit-owned scheduled-care state and immediate Doctor capacity boundary for linked appointments. */
public interface VisitScheduledCareService {
    void startScheduledCare(ScheduledCareCommand command);
    void completeScheduledCare(ScheduledCareCommand command);
    void cancelCheckedInVisit(ScheduledCareCommand command);

    class ScheduledCareCommand {
        private final UUID appointmentId;
        private final UUID visitId;
        private final UUID patientProfileId;
        private final UUID doctorProfileId;
        private final Instant scheduledEnd;
        private final Instant occurredAt;
        private final UUID actorAccountId;
        private final int expectedDurationMinutes;
        private final String correlationId;
        public ScheduledCareCommand(UUID appointmentId, UUID visitId, UUID patientProfileId,
                UUID doctorProfileId, Instant scheduledEnd, Instant occurredAt) {
            this(appointmentId, visitId, patientProfileId, doctorProfileId, scheduledEnd, occurredAt, null, 0, null);
        }
        public ScheduledCareCommand(UUID appointmentId, UUID visitId, UUID patientProfileId,
                UUID doctorProfileId, Instant scheduledEnd, Instant occurredAt, UUID actorAccountId,
                int expectedDurationMinutes, String correlationId) {
            this.appointmentId = appointmentId; this.visitId = visitId; this.patientProfileId = patientProfileId;
            this.doctorProfileId = doctorProfileId; this.scheduledEnd = scheduledEnd; this.occurredAt = occurredAt;
            this.actorAccountId = actorAccountId; this.expectedDurationMinutes = expectedDurationMinutes;
            this.correlationId = correlationId;
        }
        public UUID appointmentId() { return appointmentId; }
        public UUID visitId() { return visitId; }
        public UUID patientProfileId() { return patientProfileId; }
        public UUID doctorProfileId() { return doctorProfileId; }
        public Instant scheduledEnd() { return scheduledEnd; }
        public Instant occurredAt() { return occurredAt; }
        public UUID actorAccountId() { return actorAccountId; }
        public int expectedDurationMinutes() { return expectedDurationMinutes; }
        public String correlationId() { return correlationId; }
    }
}
