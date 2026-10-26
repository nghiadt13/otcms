package fu.fa26.sep490.g58.otcms_be.visit.model;

import java.time.Instant;
import java.util.UUID;

/** Persistence input for Visit scheduled-care writes, independent of the service API. */
public class ScheduledCarePersistenceData {
    private final UUID appointmentId;
    private final UUID visitId;
    private final UUID patientProfileId;
    private final UUID doctorProfileId;
    private final Instant scheduledEnd;
    private final Instant occurredAt;
    private final UUID actorAccountId;
    private final int expectedDurationMinutes;
    private final String correlationId;

    public ScheduledCarePersistenceData(UUID appointmentId, UUID visitId, UUID patientProfileId,
            UUID doctorProfileId, Instant scheduledEnd, Instant occurredAt, UUID actorAccountId,
            int expectedDurationMinutes, String correlationId) {
        this.appointmentId = appointmentId;
        this.visitId = visitId;
        this.patientProfileId = patientProfileId;
        this.doctorProfileId = doctorProfileId;
        this.scheduledEnd = scheduledEnd;
        this.occurredAt = occurredAt;
        this.actorAccountId = actorAccountId;
        this.expectedDurationMinutes = expectedDurationMinutes;
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
