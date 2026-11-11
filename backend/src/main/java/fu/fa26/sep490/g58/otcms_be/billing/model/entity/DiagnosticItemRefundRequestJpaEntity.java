package fu.fa26.sep490.g58.otcms_be.billing.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "diagnostic_item_refund_requests")
public class DiagnosticItemRefundRequestJpaEntity {
    @Id private UUID id;
    @Column(name = "invoice_id", nullable = false) private UUID invoiceId;
    @Column(name = "line_number", nullable = false) private int lineNumber;
    @Column(name = "clinical_order_id", nullable = false) private UUID clinicalOrderId;
    @Column(name = "incident_id", nullable = false) private UUID incidentId;
    @Column(name = "requested_by_account_id", nullable = false) private UUID requestedByAccountId;
    @Column(name = "idempotency_key", nullable = false, length = 128) private String idempotencyKey;
    @Column(name = "request_fingerprint", nullable = false, length = 64, columnDefinition = "char(64)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private String requestFingerprint;
    @Column(nullable = false) private String reason;
    @Column(nullable = false, length = 32) private String status;
    @Column(name = "allocated_amount", nullable = false, precision = 14, scale = 2) private BigDecimal allocatedAmount;
    @Column(name = "approved_by_account_id") private UUID approvedByAccountId;
    @Column(name = "decision_reason") private String decisionReason;
    @Column(name = "provider_reference", length = 128) private String providerReference;
    @Column(name = "requested_at", nullable = false, insertable = false, updatable = false) private Instant requestedAt;
    @Column(name = "decided_at") private Instant decidedAt;
    @Column(name = "refunded_at") private Instant refundedAt;
    @Version @Column(nullable = false) private long version;

    protected DiagnosticItemRefundRequestJpaEntity() { }
    public DiagnosticItemRefundRequestJpaEntity(UUID id, UUID invoiceId, int lineNumber, UUID orderId,
            UUID incidentId, UUID requesterId, String idempotencyKey, String fingerprint, String reason, BigDecimal amount) {
        this.id = id; this.invoiceId = invoiceId; this.lineNumber = lineNumber; this.clinicalOrderId = orderId;
        this.incidentId = incidentId; this.requestedByAccountId = requesterId; this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = fingerprint;
        this.reason = reason; this.status = "PENDING_ADMIN_REVIEW"; this.allocatedAmount = amount;
    }
    public void decide(UUID actorId, String status, String reason, Instant at) {
        this.approvedByAccountId = actorId; this.status = status; this.decisionReason = reason; this.decidedAt = at;
    }
    public void markRefunded(String reference, Instant at) {
        this.providerReference = reference; this.refundedAt = at; this.status = "REFUNDED";
    }
    public UUID getId() { return id; }
    public UUID getInvoiceId() { return invoiceId; }
    public int getLineNumber() { return lineNumber; }
    public UUID getClinicalOrderId() { return clinicalOrderId; }
    public UUID getIncidentId() { return incidentId; }
    public UUID getRequestedByAccountId() { return requestedByAccountId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public String getReason() { return reason; }
    public String getStatus() { return status; }
    public BigDecimal getAllocatedAmount() { return allocatedAmount; }
    public UUID getApprovedByAccountId() { return approvedByAccountId; }
    public String getDecisionReason() { return decisionReason; }
    public String getProviderReference() { return providerReference; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public Instant getRefundedAt() { return refundedAt; }
}
