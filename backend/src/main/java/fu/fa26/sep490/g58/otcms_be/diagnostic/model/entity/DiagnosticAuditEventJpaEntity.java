package fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity;

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
@Table(name = "diagnostic_audit_events")
public class DiagnosticAuditEventJpaEntity {
    @Id
    private UUID id;

    @Column(name = "event_id", unique = true)
    private UUID eventId;

    @Column(name = "actor_account_id")
    private UUID actorAccountId;

    @Column(name = "system_source_id", length = 128)
    private String systemSourceId;

    @Column(name = "request_id")
    private UUID requestId;

    @Column(name = "visit_id", nullable = false)
    private UUID visitId;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(nullable = false, length = 48)
    private String outcome;

    @Column(name = "occurred_at")
    private Instant occurredAt;

    @Column(name = "recorded_at", nullable = false, insertable = false, updatable = false)
    private Instant recordedAt;

    @Column(name = "correlation_id", nullable = false, length = 128)
    private String correlationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    protected DiagnosticAuditEventJpaEntity() { }

    public DiagnosticAuditEventJpaEntity(UUID id, UUID eventId, UUID actorAccountId, String systemSourceId,
            UUID requestId, UUID visitId, String action, String outcome, Instant occurredAt,
            String correlationId, Map<String, Object> metadata) {
        this.id = id;
        this.eventId = eventId;
        this.actorAccountId = actorAccountId;
        this.systemSourceId = systemSourceId;
        this.requestId = requestId;
        this.visitId = visitId;
        this.action = action;
        this.outcome = outcome;
        this.occurredAt = occurredAt;
        this.correlationId = correlationId;
        this.metadata = metadata;
    }

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public UUID getActorAccountId() { return actorAccountId; }
    public String getSystemSourceId() { return systemSourceId; }
    public UUID getRequestId() { return requestId; }
    public UUID getVisitId() { return visitId; }
    public String getAction() { return action; }
    public String getOutcome() { return outcome; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getRecordedAt() { return recordedAt; }
    public String getCorrelationId() { return correlationId; }
    public Map<String, Object> getMetadata() { return metadata; }
}
