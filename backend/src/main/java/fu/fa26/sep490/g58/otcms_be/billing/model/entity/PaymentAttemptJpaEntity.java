package fu.fa26.sep490.g58.otcms_be.billing.model.entity;

import fu.fa26.sep490.g58.otcms_be.billing.model.PaymentAttemptStatus;
import fu.fa26.sep490.g58.otcms_be.billing.model.PaymentChannel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_attempts")
public class PaymentAttemptJpaEntity {
    @Id private UUID id;
    @Column(name = "invoice_id", nullable = false) private UUID invoiceId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private PaymentChannel channel;
    @Column(name = "counter_method", length = 24) private String counterMethod;
    @Column(length = 32) private String provider;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal amount;
    @Column(name = "currency_code", nullable = false, length = 3) private String currencyCode;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private PaymentAttemptStatus status;
    @Column(name = "created_by_account_id", nullable = false) private UUID createdByAccountId;
    @Column(name = "idempotency_key", nullable = false, length = 128) private String idempotencyKey;
    @Column(name = "request_fingerprint", nullable = false, length = 64) private String requestFingerprint;
    @Column(name = "provider_order_code") private Long providerOrderCode;
    @Column(name = "provider_payment_link_id", length = 100) private String providerPaymentLinkId;
    @Column(name = "provider_transaction_reference", length = 128) private String providerTransactionReference;
    @Column(name = "checkout_url", length = 1000) private String checkoutUrl;
    @Column(name = "qr_code", columnDefinition = "text") private String qrCode;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "expires_at") private Instant expiresAt;
    @Column(name = "confirmed_at") private Instant confirmedAt;
    @Column(name = "reconciliation_reason", length = 500) private String reconciliationReason;

    protected PaymentAttemptJpaEntity() { }

    public static PaymentAttemptJpaEntity pendingOnline(UUID id, UUID invoiceId, BigDecimal amount,
            UUID actor, String key, String fingerprint, long orderCode) {
        PaymentAttemptJpaEntity attempt = base(id, invoiceId, amount, actor, key, fingerprint);
        attempt.channel = PaymentChannel.ONLINE;
        attempt.provider = "PAYOS";
        attempt.providerOrderCode = orderCode;
        attempt.status = PaymentAttemptStatus.PENDING;
        return attempt;
    }

    public static PaymentAttemptJpaEntity counter(UUID id, UUID invoiceId, BigDecimal amount,
            UUID actor, String key, String fingerprint, String method, Instant now) {
        PaymentAttemptJpaEntity attempt = base(id, invoiceId, amount, actor, key, fingerprint);
        attempt.channel = PaymentChannel.COUNTER;
        attempt.counterMethod = method;
        attempt.status = PaymentAttemptStatus.CONFIRMED;
        attempt.confirmedAt = now;
        return attempt;
    }

    private static PaymentAttemptJpaEntity base(UUID id, UUID invoiceId, BigDecimal amount,
            UUID actor, String key, String fingerprint) {
        PaymentAttemptJpaEntity attempt = new PaymentAttemptJpaEntity();
        attempt.id = id;
        attempt.invoiceId = invoiceId;
        attempt.amount = amount;
        attempt.currencyCode = "VND";
        attempt.createdByAccountId = actor;
        attempt.idempotencyKey = key;
        attempt.requestFingerprint = fingerprint;
        return attempt;
    }

    public void attachPayOsLink(String linkId, String checkoutUrl, String qrCode, Instant expiresAt) {
        this.providerPaymentLinkId = linkId;
        this.checkoutUrl = checkoutUrl;
        this.qrCode = qrCode;
        this.expiresAt = expiresAt;
    }
    public void confirm(String transactionReference, Instant now) {
        this.status = PaymentAttemptStatus.CONFIRMED;
        this.providerTransactionReference = transactionReference;
        this.confirmedAt = now;
        this.reconciliationReason = null;
    }
    public void fail(PaymentAttemptStatus newStatus, String reason) {
        this.status = newStatus;
        this.reconciliationReason = reason;
    }
    public void requireReconciliation(String reason, String transactionReference) {
        this.status = PaymentAttemptStatus.RECONCILIATION_REQUIRED;
        this.reconciliationReason = reason;
        this.providerTransactionReference = transactionReference;
    }
    public UUID getId() { return id; }
    public UUID getInvoiceId() { return invoiceId; }
    public PaymentChannel getChannel() { return channel; }
    public String getProvider() { return provider; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrencyCode() { return currencyCode; }
    public PaymentAttemptStatus getStatus() { return status; }
    public UUID getCreatedByAccountId() { return createdByAccountId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public Long getProviderOrderCode() { return providerOrderCode; }
    public String getProviderPaymentLinkId() { return providerPaymentLinkId; }
    public String getProviderTransactionReference() { return providerTransactionReference; }
    public String getCheckoutUrl() { return checkoutUrl; }
    public String getQrCode() { return qrCode; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public String getReconciliationReason() { return reconciliationReason; }
}
