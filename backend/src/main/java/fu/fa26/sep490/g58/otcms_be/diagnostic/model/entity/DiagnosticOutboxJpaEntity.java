package fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOutboxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "diagnostic_outbox")
public class DiagnosticOutboxJpaEntity {
    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "visit_id", nullable = false)
    private UUID visitId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DiagnosticOutboxStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    protected DiagnosticOutboxJpaEntity() { }

    public DiagnosticOutboxJpaEntity(UUID id, UUID eventId, UUID requestId, UUID visitId,
            String eventType, Map<String, Object> payload, DiagnosticOutboxStatus status,
            int attemptCount, Instant nextAttemptAt, Instant deliveredAt, String lastError) {
        this.id = id;
        this.eventId = eventId;
        this.requestId = requestId;
        this.visitId = visitId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = status;
        this.attemptCount = attemptCount;
        this.nextAttemptAt = nextAttemptAt;
        this.deliveredAt = deliveredAt;
        this.lastError = lastError;
    }

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public UUID getRequestId() { return requestId; }
    public UUID getVisitId() { return visitId; }
    public String getEventType() { return eventType; }
    public Map<String, Object> getPayload() { return payload; }
    public DiagnosticOutboxStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public String getLastError() { return lastError; }

    public void setStatus(DiagnosticOutboxStatus status) { this.status = status; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
    public void setNextAttemptAt(Instant nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
    public void setDeliveredAt(Instant deliveredAt) { this.deliveredAt = deliveredAt; }
    public void setLastError(String lastError) { this.lastError = lastError; }
}
