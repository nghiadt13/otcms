package fu.fa26.sep490.g58.otcms_be.imaging.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "imaging_reconciliation_cases")
public class ImagingReconciliationCaseJpaEntity {
    @Id private UUID id;
    @Column(name = "operation_id") private UUID operationId;
    @Column(name = "event_id") private UUID eventId;
    @Column(nullable = false, length = 32) private String category;
    @Column(nullable = false, length = 16) private String state;
    @Column(name = "safe_reason_code", nullable = false, length = 64) private String safeReasonCode;
    @Column(name = "assigned_owner_queue", nullable = false, length = 64) private String assignedOwnerQueue;
    @Column(name = "opened_at", nullable = false, insertable = false, updatable = false) private Instant openedAt;
    @Column(name = "updated_at", nullable = false, insertable = false) private Instant updatedAt;
    @Column(name = "resolved_at") private Instant resolvedAt;
    @Column(name = "resolution_ref", length = 128) private String resolutionRef;

    protected ImagingReconciliationCaseJpaEntity() { }

    public ImagingReconciliationCaseJpaEntity(UUID id, UUID operationId, UUID eventId, String category,
            String state, String safeReasonCode, String assignedOwnerQueue, Instant updatedAt,
            Instant resolvedAt, String resolutionRef) {
        this.id = id; this.operationId = operationId; this.eventId = eventId; this.category = category;
        this.state = state; this.safeReasonCode = safeReasonCode; this.assignedOwnerQueue = assignedOwnerQueue;
        this.updatedAt = updatedAt; this.resolvedAt = resolvedAt; this.resolutionRef = resolutionRef;
    }

    public UUID getId() { return id; }
    public UUID getOperationId() { return operationId; }
    public UUID getEventId() { return eventId; }
    public String getCategory() { return category; }
    public String getState() { return state; }
    public String getSafeReasonCode() { return safeReasonCode; }
    public String getAssignedOwnerQueue() { return assignedOwnerQueue; }
    public Instant getOpenedAt() { return openedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public String getResolutionRef() { return resolutionRef; }
}
