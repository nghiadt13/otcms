package fu.fa26.sep490.g58.otcms_be.idempotency.model.entity;

import fu.fa26.sep490.g58.otcms_be.idempotency.model.IdempotencyScope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecordJpaEntity {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 40)
    private IdempotencyScope scope;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdempotencyRecordJpaEntity() { }

    public IdempotencyScope getScope() { return scope; }
    public UUID getOwnerId() { return ownerId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public UUID getResourceId() { return resourceId; }
    public Instant getCreatedAt() { return createdAt; }
}
