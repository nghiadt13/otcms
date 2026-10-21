package fu.fa26.sep490.g58.otcms_be.visit.model;

import java.time.Instant;
import java.util.UUID;

public class CareStepAssignment {
    private final UUID id;
    private final UUID visitId;
    private final UUID doctorProfileId;
    private final String stepCode;
    private final String status;
    private final Instant startsAt;
    private final Instant expectedEnd;
    private final Instant actualEndedAt;
    private final UUID roomId;
    private final String roomCode;

    public CareStepAssignment(UUID id, UUID visitId, UUID doctorProfileId, String stepCode, String status,
            Instant startsAt, Instant expectedEnd, Instant actualEndedAt) {
        this(id, visitId, doctorProfileId, stepCode, status, startsAt, expectedEnd, actualEndedAt, null, null);
    }

    public CareStepAssignment(UUID id, UUID visitId, UUID doctorProfileId, String stepCode, String status,
            Instant startsAt, Instant expectedEnd, Instant actualEndedAt, UUID roomId, String roomCode) {
        this.id = id;
        this.visitId = visitId;
        this.doctorProfileId = doctorProfileId;
        this.stepCode = stepCode;
        this.status = status;
        this.startsAt = startsAt;
        this.expectedEnd = expectedEnd;
        this.actualEndedAt = actualEndedAt;
        this.roomId = roomId;
        this.roomCode = roomCode;
    }

    public UUID id() { return id; }
    public UUID visitId() { return visitId; }
    public UUID doctorProfileId() { return doctorProfileId; }
    public String stepCode() { return stepCode; }
    public String status() { return status; }
    public Instant startsAt() { return startsAt; }
    public Instant expectedEnd() { return expectedEnd; }
    public Instant actualEndedAt() { return actualEndedAt; }
    public UUID roomId() { return roomId; }
    public String roomCode() { return roomCode; }
}
