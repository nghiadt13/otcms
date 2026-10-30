package fu.fa26.sep490.g58.otcms_be.visit.model.dto.response;

import java.time.Instant;
import java.util.UUID;

public class CareStepAssignmentResponse {
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

    public CareStepAssignmentResponse(UUID id, UUID visitId, UUID doctorProfileId, String stepCode, String status,
            Instant startsAt, Instant expectedEnd, Instant actualEndedAt) {
        this(id, visitId, doctorProfileId, stepCode, status, startsAt, expectedEnd, actualEndedAt, null, null);
    }

    public CareStepAssignmentResponse(UUID id, UUID visitId, UUID doctorProfileId, String stepCode, String status,
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

    public UUID getId() { return id; }
    public UUID getVisitId() { return visitId; }
    public UUID getDoctorProfileId() { return doctorProfileId; }
    public String getStepCode() { return stepCode; }
    public String getStatus() { return status; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getExpectedEnd() { return expectedEnd; }
    public Instant getActualEndedAt() { return actualEndedAt; }
    public UUID getRoomId() { return roomId; }
    public String getRoomCode() { return roomCode; }
}
