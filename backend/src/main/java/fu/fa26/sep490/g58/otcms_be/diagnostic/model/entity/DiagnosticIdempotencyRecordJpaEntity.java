package fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome;
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
@Table(name = "diagnostic_idempotency_records")
public class DiagnosticIdempotencyRecordJpaEntity {
    @Id
    private UUID id;

    @Column(name = "actor_account_id")
    private UUID actorAccountId;

    @Column(name = "system_source_id", length = 128)
    private String systemSourceId;

    @Column(name = "visit_id", nullable = false)
    private UUID visitId;

    @Column(name = "request_id")
    private UUID requestId;

    @Column(nullable = false, length = 64)
    private String operation;

    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Column(name = "payload_fingerprint", nullable = false, length = 64)
    private String payloadFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_outcome", nullable = false, length = 48)
    private DiagnosticOperationOutcome resultOutcome;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> resultPayload;

    @Column(name = "recorded_at", nullable = false, insertable = false, updatable = false)
    private Instant recordedAt;

    protected DiagnosticIdempotencyRecordJpaEntity() { }

    public DiagnosticIdempotencyRecordJpaEntity(UUID id, UUID actorAccountId, String systemSourceId,
            UUID visitId, UUID requestId, String operation, String idempotencyKey,
            String payloadFingerprint, DiagnosticOperationOutcome resultOutcome, Map<String, Object> resultPayload) {
        this.id = id;
        this.actorAccountId = actorAccountId;
        this.systemSourceId = systemSourceId;
        this.visitId = visitId;
        this.requestId = requestId;
        this.operation = operation;
        this.idempotencyKey = idempotencyKey;
        this.payloadFingerprint = payloadFingerprint;
        this.resultOutcome = resultOutcome;
        this.resultPayload = resultPayload;
    }

    public UUID getId() { return id; }
    public UUID getActorAccountId() { return actorAccountId; }
    public String getSystemSourceId() { return systemSourceId; }
    public UUID getVisitId() { return visitId; }
    public UUID getRequestId() { return requestId; }
    public String getOperation() { return operation; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getPayloadFingerprint() { return payloadFingerprint; }
    public DiagnosticOperationOutcome getResultOutcome() { return resultOutcome; }
    public Map<String, Object> getResultPayload() { return resultPayload; }
    public Instant getRecordedAt() { return recordedAt; }

    public void completeResult(Map<String, Object> payload) {
        this.resultPayload = Map.copyOf(payload);
    }
}
