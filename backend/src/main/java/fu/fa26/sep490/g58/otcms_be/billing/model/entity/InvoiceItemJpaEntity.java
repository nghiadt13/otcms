package fu.fa26.sep490.g58.otcms_be.billing.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "invoice_items")
@IdClass(InvoiceItemJpaEntity.Key.class)
public class InvoiceItemJpaEntity {
    @Column(name = "id", nullable = false, unique = true)
    private UUID id = UUID.randomUUID();
    @Id
    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;
    @Id
    @Column(name = "line_number", nullable = false)
    private int lineNumber;
    @Column(name = "service_id")
    private UUID serviceId;
    @Column(name = "service_order_id")
    private UUID serviceOrderId;
    @Column(name = "payment_attempt_id")
    private UUID paymentAttemptId;
    @Column(name = "voided_at")
    private Instant voidedAt;
    @Column(name = "refund_status", length = 16)
    private String refundStatus;
    @Column(name = "refund_reason", columnDefinition = "text")
    private String refundReason;
    @Column(name = "refunded_at")
    private Instant refundedAt;
    @Column(name = "refunded_by_account_id")
    private UUID refundedByAccountId;
    @Column(name = "description_snapshot", nullable = false, length = 255)
    private String descriptionSnapshot;
    @Column(nullable = false)
    private int quantity;
    @Column(name = "unit_price_snapshot", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPriceSnapshot;

    protected InvoiceItemJpaEntity() { }

    public InvoiceItemJpaEntity(UUID invoiceId, int lineNumber, UUID serviceId, String description,
            int quantity, BigDecimal unitPrice) {
        this.invoiceId = invoiceId;
        this.lineNumber = lineNumber;
        this.serviceId = serviceId;
        this.descriptionSnapshot = description;
        this.quantity = quantity;
        this.unitPriceSnapshot = unitPrice;
    }

    public UUID getInvoiceId() { return invoiceId; }
    public UUID getId() { return id; }
    public UUID getServiceOrderId() { return serviceOrderId; }
    public void setServiceOrderId(UUID serviceOrderId) { this.serviceOrderId = serviceOrderId; }
    public UUID getPaymentAttemptId() { return paymentAttemptId; }
    public void setPaymentAttemptId(UUID paymentAttemptId) { this.paymentAttemptId = paymentAttemptId; }
    public Instant getVoidedAt() { return voidedAt; }
    public String getRefundStatus() { return refundStatus; }
    public String getRefundReason() { return refundReason; }
    public Instant getRefundedAt() { return refundedAt; }
    public UUID getRefundedByAccountId() { return refundedByAccountId; }
    /** Cancels an unpaid item. Idempotent; never touches payment or refund fields. */
    public void markVoided(Instant now) { if (voidedAt == null) voidedAt = now; }
    /** Flags a paid item as owed back to the patient. Idempotent: the first reason is kept. */
    public void requestRefund(String reason) {
        if (refundStatus == null) { refundStatus = "REFUND_PENDING"; refundReason = reason; }
    }
    public void confirmRefund(Instant now, UUID accountId) {
        refundStatus = "REFUNDED"; refundedAt = now; refundedByAccountId = accountId;
    }
    public int getLineNumber() { return lineNumber; }
    public UUID getServiceId() { return serviceId; }
    public String getDescriptionSnapshot() { return descriptionSnapshot; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitPriceSnapshot() { return unitPriceSnapshot; }

    public static class Key implements Serializable {
        private UUID invoiceId;
        private int lineNumber;
        public Key() { }
        @Override public boolean equals(Object other) {
            return other instanceof Key key && lineNumber == key.lineNumber && Objects.equals(invoiceId, key.invoiceId);
        }
        @Override public int hashCode() { return Objects.hash(invoiceId, lineNumber); }
    }
}
