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
@Table(name = "imaging_confirmation_outbox")
public class ImagingConfirmationOutboxJpaEntity {
    @Id private UUID id;
    @Column(name = "event_id", nullable = false, unique = true) private UUID eventId;
    @Column(name = "operation_id", nullable = false, unique = true) private UUID operationId;
    @Column(name = "study_ref", nullable = false, length = 128) private String studyRef;
    @Column(name = "request_id", nullable = false) private UUID requestId;
    @Column(name = "attempt_id", nullable = false) private UUID attemptId;
    @Column(name = "visit_id", nullable = false) private UUID visitId;
    @Column(name = "patient_id", nullable = false) private UUID patientId;
    @Column(name = "verification_status", nullable = false, length = 24) private String verificationStatus;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "verification_evidence", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> verificationEvidence;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "payload_sha256", nullable = false, length = 64, columnDefinition = "char(64)")
    private String payloadSha256;
    @Column(nullable = false, length = 16) private String status;
    @Column(name = "attempt_count", nullable = false) private int attemptCount;
    @Column(name = "next_attempt_at", nullable = false) private Instant nextAttemptAt;
    @Column(name = "delivered_at") private Instant deliveredAt;
    @Column(name = "acknowledged_at") private Instant acknowledgedAt;
    @Column(name = "last_safe_error_code", length = 64) private String lastSafeErrorCode;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) private Instant createdAt;

    protected ImagingConfirmationOutboxJpaEntity() { }

    public ImagingConfirmationOutboxJpaEntity(UUID id, UUID eventId, UUID operationId, String studyRef,
            UUID requestId, UUID attemptId, UUID visitId, UUID patientId, String verificationStatus,
            Map<String, Object> verificationEvidence, String payloadSha256, String status, int attemptCount,
            Instant nextAttemptAt, Instant deliveredAt, Instant acknowledgedAt, String lastSafeErrorCode) {
        this.id = id; this.eventId = eventId; this.operationId = operationId; this.studyRef = studyRef;
        this.requestId = requestId; this.attemptId = attemptId; this.visitId = visitId; this.patientId = patientId;
        this.verificationStatus = verificationStatus; this.verificationEvidence = verificationEvidence;
        this.payloadSha256 = payloadSha256; this.status = status; this.attemptCount = attemptCount;
        this.nextAttemptAt = nextAttemptAt; this.deliveredAt = deliveredAt;
        this.acknowledgedAt = acknowledgedAt; this.lastSafeErrorCode = lastSafeErrorCode;
    }

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public UUID getOperationId() { return operationId; }
    public String getStudyRef() { return studyRef; }
    public UUID getRequestId() { return requestId; }
    public UUID getAttemptId() { return attemptId; }
    public UUID getVisitId() { return visitId; }
    public UUID getPatientId() { return patientId; }
    public String getVerificationStatus() { return verificationStatus; }
    public Map<String, Object> getVerificationEvidence() { return verificationEvidence; }
    public String getPayloadSha256() { return payloadSha256; }
    public String getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public String getLastSafeErrorCode() { return lastSafeErrorCode; }
    public Instant getCreatedAt() { return createdAt; }

    public void markAcknowledged(Instant now) {
        status = "ACKNOWLEDGED";
        attemptCount = Math.min(8, attemptCount + 1);
        deliveredAt = now;
        acknowledgedAt = now;
        lastSafeErrorCode = null;
    }

    public void scheduleRetry(Instant nextAt, String safeCode) {
        attemptCount = Math.min(8, attemptCount + 1);
        nextAttemptAt = nextAt;
        lastSafeErrorCode = safeCode;
        deliveredAt = Instant.now();
    }

    public void markRejected(String safeCode) {
        status = "REJECTED";
        attemptCount = Math.min(8, attemptCount + 1);
        lastSafeErrorCode = safeCode;
    }
}
