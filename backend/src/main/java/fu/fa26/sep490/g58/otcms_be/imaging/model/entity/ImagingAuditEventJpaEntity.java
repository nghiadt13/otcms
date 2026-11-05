package fu.fa26.sep490.g58.otcms_be.imaging.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "imaging_audit_events")
public class ImagingAuditEventJpaEntity {
    @Id private UUID id;
    @Column(name = "actor_account_id") private UUID actorAccountId;
    @Column(name = "service_identity", length = 128) private String serviceIdentity;
    @Column(nullable = false, length = 64) private String action;
    @Column(nullable = false, length = 32) private String outcome;
    @Column(name = "operation_id") private UUID operationId;
    @Column(name = "study_ref", length = 128) private String studyRef;
    @Column(name = "request_id") private UUID requestId;
    @Column(name = "attempt_id") private UUID attemptId;
    @Column(name = "visit_id") private UUID visitId;
    @Column(name = "patient_id") private UUID patientId;
    @Column(name = "correlation_id", nullable = false, length = 128) private String correlationId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "safe_metadata", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> safeMetadata;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    @Column(name = "recorded_at", nullable = false, insertable = false, updatable = false) private Instant recordedAt;

    protected ImagingAuditEventJpaEntity() { }

    public ImagingAuditEventJpaEntity(UUID id, UUID actorAccountId, String serviceIdentity, String action,
            String outcome, UUID operationId, String studyRef, UUID requestId, UUID attemptId, UUID visitId,
            UUID patientId, String correlationId, Map<String, Object> safeMetadata, Instant occurredAt) {
        this.id = id; this.actorAccountId = actorAccountId; this.serviceIdentity = serviceIdentity;
        this.action = action; this.outcome = outcome; this.operationId = operationId; this.studyRef = studyRef;
        this.requestId = requestId; this.attemptId = attemptId; this.visitId = visitId; this.patientId = patientId;
        this.correlationId = correlationId; this.safeMetadata = safeMetadata; this.occurredAt = occurredAt;
    }

    public UUID getId() { return id; }
    public UUID getActorAccountId() { return actorAccountId; }
    public String getServiceIdentity() { return serviceIdentity; }
    public String getAction() { return action; }
    public String getOutcome() { return outcome; }
    public UUID getOperationId() { return operationId; }
    public String getStudyRef() { return studyRef; }
    public UUID getRequestId() { return requestId; }
    public UUID getAttemptId() { return attemptId; }
    public UUID getVisitId() { return visitId; }
    public UUID getPatientId() { return patientId; }
    public String getCorrelationId() { return correlationId; }
    public Map<String, Object> getSafeMetadata() { return safeMetadata; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getRecordedAt() { return recordedAt; }
}
