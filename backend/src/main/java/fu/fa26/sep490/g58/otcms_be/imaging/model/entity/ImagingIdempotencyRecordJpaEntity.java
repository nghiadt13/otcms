package fu.fa26.sep490.g58.otcms_be.imaging.model.entity;

import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingIdempotencyRecord;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "imaging_idempotency_records")
public class ImagingIdempotencyRecordJpaEntity {
    @Id
    private UUID id;

    @Column(name = "actor_account_id", nullable = false)
    private UUID actorAccountId;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "attempt_id", nullable = false)
    private UUID attemptId;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 16)
    private ImagingOperationType operationType;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "payload_sha256", nullable = false, length = 64, columnDefinition = "char(64)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private String payloadSha256;

    @Column(name = "target_operation_id", nullable = false)
    private UUID targetOperationId;

    @Column(name = "safe_outcome_code", length = 64)
    private String safeOutcomeCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ImagingIdempotencyRecordJpaEntity() { }

    public ImagingIdempotencyRecordJpaEntity(ImagingIdempotencyRecord record) {
        id = record.id();
        actorAccountId = record.actorAccountId();
        requestId = record.requestId();
        attemptId = record.attemptId();
        operationType = record.operationType();
        idempotencyKey = record.idempotencyKey();
        payloadSha256 = record.payloadSha256();
        targetOperationId = record.targetOperationId();
        safeOutcomeCode = record.safeOutcomeCode();
        createdAt = record.createdAt();
    }

    public ImagingIdempotencyRecord toModel() {
        return new ImagingIdempotencyRecord(id, actorAccountId, requestId, attemptId,
                operationType, idempotencyKey, payloadSha256, targetOperationId, safeOutcomeCode, createdAt);
    }
}
