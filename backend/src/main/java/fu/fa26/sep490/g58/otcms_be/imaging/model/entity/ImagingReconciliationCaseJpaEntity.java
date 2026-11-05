package fu.fa26.sep490.g58.otcms_be.imaging.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "imaging_reconciliation_cases")
public class ImagingReconciliationCaseJpaEntity {
    @Id private UUID id;
    @Column(name = "operation_id") private UUID operationId;
    @Column(name = "event_id") private UUID eventId;
    @Column(nullable = false, length = 32) private String category;
    @Column(nullable = false, length = 24) private String classification;
    @Column(nullable = false, length = 16) private String state;
    @Column(name = "safe_reason_code", nullable = false, length = 64) private String safeReasonCode;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "safe_evidence_sha256", length = 64, columnDefinition = "char(64)")
    private String safeEvidenceSha256;
    @Column(name = "assigned_owner_queue", nullable = false, length = 64) private String assignedOwnerQueue;
    @Column(name = "opened_at", nullable = false, insertable = false, updatable = false) private Instant openedAt;
    @Column(name = "updated_at", nullable = false, insertable = false) private Instant updatedAt;
    @Column(name = "resolved_at") private Instant resolvedAt;
    @Column(name = "resolution_ref", length = 128) private String resolutionRef;
    @Column(name = "resolution_decision", length = 40) private String resolutionDecision;

    protected ImagingReconciliationCaseJpaEntity() { }

    public ImagingReconciliationCaseJpaEntity(UUID id, UUID operationId, UUID eventId, String category,
            String classification, String state, String safeReasonCode, String assignedOwnerQueue, Instant updatedAt,
            Instant resolvedAt, String resolutionRef) {
        this(id, operationId, eventId, category, classification, state, safeReasonCode, null,
                assignedOwnerQueue, updatedAt, resolvedAt, resolutionRef);
    }

    public ImagingReconciliationCaseJpaEntity(UUID id, UUID operationId, UUID eventId, String category,
            String classification, String state, String safeReasonCode, String safeEvidenceSha256,
            String assignedOwnerQueue, Instant updatedAt, Instant resolvedAt, String resolutionRef) {
        this.id = id; this.operationId = operationId; this.eventId = eventId; this.category = category;
        this.classification = classification;
        this.state = state; this.safeReasonCode = safeReasonCode; this.safeEvidenceSha256 = safeEvidenceSha256;
        this.assignedOwnerQueue = assignedOwnerQueue;
        this.updatedAt = updatedAt; this.resolvedAt = resolvedAt; this.resolutionRef = resolutionRef;
    }

    public UUID getId() { return id; }
    public UUID getOperationId() { return operationId; }
    public UUID getEventId() { return eventId; }
    public String getCategory() { return category; }
    public String getClassification() { return classification; }
    public String getState() { return state; }
    public String getSafeReasonCode() { return safeReasonCode; }
    public String getSafeEvidenceSha256() { return safeEvidenceSha256; }
    public String getAssignedOwnerQueue() { return assignedOwnerQueue; }
    public Instant getOpenedAt() { return openedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public String getResolutionRef() { return resolutionRef; }
    public String getResolutionDecision() { return resolutionDecision; }
}
