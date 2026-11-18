package fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** A single collection attempt; a rejected attempt is retained when a new sample is collected. */
@Entity
@Table(name = "laboratory_samples")
public class LaboratorySampleJpaEntity {
    @Id private UUID id;
    @Column(name = "service_order_id", nullable = false) private UUID serviceOrderId;
    @Column(name = "attempt_number", nullable = false) private int attemptNumber;
    @Column(name = "sample_code", nullable = false, length = 128) private String sampleCode;
    @Column(name = "verified_by_account_id", nullable = false) private UUID verifiedByAccountId;
    @Column(name = "verified_at", nullable = false) private Instant verifiedAt;
    @Column(name = "collected_by_account_id", nullable = false) private UUID collectedByAccountId;
    @Column(name = "collected_at", nullable = false) private Instant collectedAt;
    @Column(name = "rejected_by_account_id") private UUID rejectedByAccountId;
    @Column(name = "rejected_at") private Instant rejectedAt;
    @Column(name = "rejection_reason", columnDefinition = "text") private String rejectionReason;
    @Column(name = "rejection_source", columnDefinition = "text") private String rejectionSource;
    @Version private Long version;

    protected LaboratorySampleJpaEntity() { }

    public LaboratorySampleJpaEntity(UUID id, UUID serviceOrderId, int attemptNumber, String sampleCode,
            UUID verifiedByAccountId, Instant verifiedAt, UUID collectedByAccountId, Instant collectedAt) {
        if (id == null || serviceOrderId == null || attemptNumber < 1 || sampleCode == null || sampleCode.isBlank()
                || sampleCode.length() > 128 || verifiedByAccountId == null || verifiedAt == null
                || collectedByAccountId == null || collectedAt == null || collectedAt.isBefore(verifiedAt)) {
            throw new IllegalArgumentException("Invalid laboratory sample");
        }
        this.id = id;
        this.serviceOrderId = serviceOrderId;
        this.attemptNumber = attemptNumber;
        this.sampleCode = sampleCode.trim();
        this.verifiedByAccountId = verifiedByAccountId;
        this.verifiedAt = verifiedAt;
        this.collectedByAccountId = collectedByAccountId;
        this.collectedAt = collectedAt;
    }

    public UUID getId() { return id; }
    public UUID getServiceOrderId() { return serviceOrderId; }
    public int getAttemptNumber() { return attemptNumber; }
    public String getSampleCode() { return sampleCode; }
    public UUID getVerifiedByAccountId() { return verifiedByAccountId; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public UUID getCollectedByAccountId() { return collectedByAccountId; }
    public Instant getCollectedAt() { return collectedAt; }
    public UUID getRejectedByAccountId() { return rejectedByAccountId; }
    public Instant getRejectedAt() { return rejectedAt; }
    public String getRejectionReason() { return rejectionReason; }
    public String getRejectionSource() { return rejectionSource; }
    public Long getVersion() { return version; }

    public void reject(String reason, String source, UUID actor, Instant when) {
        if (rejectedAt != null || reason == null || reason.isBlank() || source == null || source.isBlank()
                || actor == null || when == null || when.isBefore(collectedAt)) {
            throw new IllegalStateException("Invalid sample rejection");
        }
        rejectionReason = reason.trim();
        rejectionSource = source.trim();
        rejectedByAccountId = actor;
        rejectedAt = when;
    }
}
