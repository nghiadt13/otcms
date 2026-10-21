package fu.fa26.sep490.g58.otcms_be.visit.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "visit_care_assignments")
public class VisitCareAssignmentJpaEntity {
    @Id
    private UUID id;
    @Column(name = "visit_id", nullable = false)
    private UUID visitId;
    @Column(name = "doctor_profile_id", nullable = false)
    private UUID doctorProfileId;
    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;
    @Column(name = "ends_at", nullable = false)
    private Instant expectedEnd;
    @Column(nullable = false)
    private String status;
    @Column(name = "step_code")
    private String stepCode;
    @Column(name = "expected_duration_minutes")
    private Integer expectedDurationMinutes;
    @Column(name = "actual_ended_at")
    private Instant actualEndedAt;
    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;
    @Column(name = "released_at")
    private Instant releasedAt;

    protected VisitCareAssignmentJpaEntity() { }

    public static VisitCareAssignmentJpaEntity inProgress(UUID id, UUID visitId, UUID doctorProfileId,
            String stepCode, int durationMinutes, Instant startsAt, Instant expectedEnd) {
        var entity = new VisitCareAssignmentJpaEntity();
        entity.id = id;
        entity.visitId = visitId;
        entity.doctorProfileId = doctorProfileId;
        entity.stepCode = stepCode;
        entity.expectedDurationMinutes = durationMinutes;
        entity.startsAt = startsAt;
        entity.expectedEnd = expectedEnd;
        entity.status = "IN_PROGRESS";
        entity.assignedAt = startsAt;
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getVisitId() { return visitId; }
    public UUID getDoctorProfileId() { return doctorProfileId; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getExpectedEnd() { return expectedEnd; }
    public String getStatus() { return status; }
    public String getStepCode() { return stepCode; }
    public Integer getExpectedDurationMinutes() { return expectedDurationMinutes; }
    public Instant getActualEndedAt() { return actualEndedAt; }
}
