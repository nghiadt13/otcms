package fu.fa26.sep490.g58.otcms_be.billing.model.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "payment_attempts")
public class PaymentAttemptJpaEntity {
    public static final long IN_FLIGHT_LINK_SECONDS = 120;
    @Id private UUID id;
    @Column(name = "invoice_id", nullable = false) private UUID invoiceId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private PaymentChannel channel;
    @Column(name = "counter_method", length = 24) private String counterMethod;
    @Column(length = 32) private String provider;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal amount;
    @Column(name = "currency_code", nullable = false, length = 3) private String currencyCode;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private PaymentAttemptStatus status;
    @Column(name = "created_by_account_id", nullable = false) private UUID createdByAccountId;
    @Column(name = "provider_order_code") private Long providerOrderCode;
    @Column(name = "provider_transaction_reference", length = 128) private String providerTransactionReference;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "provider_payload", columnDefinition = "jsonb") private ProviderPayload providerPayload;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "expires_at") private Instant expiresAt;
    @Column(name = "confirmed_at") private Instant confirmedAt;
    @Column(name = "reconciliation_reason", length = 500) private String reconciliationReason;
    @Column(name = "last_event_fingerprint", length = 64) private String lastEventFingerprint;
    @Column(name = "signature_verified") private Boolean signatureVerified;

    protected PaymentAttemptJpaEntity() { }

    public static PaymentAttemptJpaEntity pendingOnline(UUID id, UUID invoiceId, BigDecimal amount,
            UUID actor, long orderCode) {
        return pendingProvider(PaymentChannel.ONLINE, id, invoiceId, amount, actor, orderCode);
    }

    /** A PayOS checkout in the given channel (ONLINE for the patient, COUNTER_QR for a receptionist). */
    public static PaymentAttemptJpaEntity pendingProvider(PaymentChannel channel, UUID id, UUID invoiceId,
            BigDecimal amount, UUID actor, long orderCode) {
        PaymentAttemptJpaEntity attempt = base(id, invoiceId, amount, actor);
        attempt.channel = channel;
        attempt.provider = "PAYOS";
        attempt.providerOrderCode = orderCode;
        attempt.status = PaymentAttemptStatus.PENDING;
        return attempt;
    }

    public static PaymentAttemptJpaEntity counter(UUID id, UUID invoiceId, BigDecimal amount,
            UUID actor, String method, Instant now) {
        PaymentAttemptJpaEntity attempt = base(id, invoiceId, amount, actor);
        attempt.channel = PaymentChannel.COUNTER;
        attempt.counterMethod = method;
        attempt.status = PaymentAttemptStatus.CONFIRMED;
        attempt.confirmedAt = now;
        return attempt;
    }

    private static PaymentAttemptJpaEntity base(UUID id, UUID invoiceId, BigDecimal amount, UUID actor) {
        PaymentAttemptJpaEntity attempt = new PaymentAttemptJpaEntity();
        attempt.id = id;
        attempt.invoiceId = invoiceId;
        attempt.amount = amount;
        attempt.currencyCode = "VND";
        attempt.createdByAccountId = actor;
        return attempt;
    }

    public void attachPayOsLink(String linkId, String checkoutUrl, String qrCode, Instant expiresAt,
            String bankBin, String accountNumber, String accountName, String description) {
        this.providerPayload = new ProviderPayload(linkId, checkoutUrl, qrCode, bankBin, accountNumber, accountName, description);
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
        // ck_payment_attempt_confirmation: only CONFIRMED rows carry confirmed_at (an attempt confirmed in this
        // transaction can still be reverted when the appointment hold turns out to be released).
        if (newStatus != PaymentAttemptStatus.CONFIRMED) this.confirmedAt = null;
    }
    public void requireReconciliation(String reason, String transactionReference) {
        this.status = PaymentAttemptStatus.RECONCILIATION_REQUIRED;
        this.reconciliationReason = reason;
        this.providerTransactionReference = transactionReference;
        this.confirmedAt = null;
    }
    public void recordVerifiedEvent(String fingerprint) {
        this.lastEventFingerprint = fingerprint;
        this.signatureVerified = Boolean.TRUE;
    }
    /**
     * True while this PENDING checkout can still be paid: until its link expires, and, while the link is still
     * being created at the gateway (no expiry recorded yet), for {@value #IN_FLIGHT_LINK_SECONDS} seconds.
     */
    public boolean isOpenAt(Instant now) {
        if (status != PaymentAttemptStatus.PENDING) return false;
        Instant end = expiresAt != null ? expiresAt : createdAt == null ? null : createdAt.plusSeconds(IN_FLIGHT_LINK_SECONDS);
        return end == null || end.isAfter(now);
    }
    public String getLastEventFingerprint() { return lastEventFingerprint; }
    public UUID getId() { return id; }
    public UUID getInvoiceId() { return invoiceId; }
    public PaymentChannel getChannel() { return channel; }
    public String getProvider() { return provider; }
    public String getCounterMethod() { return counterMethod; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrencyCode() { return currencyCode; }
    public PaymentAttemptStatus getStatus() { return status; }
    public UUID getCreatedByAccountId() { return createdByAccountId; }
    public Long getProviderOrderCode() { return providerOrderCode; }
    public String getProviderPaymentLinkId() { return providerPayload == null ? null : providerPayload.paymentLinkId(); }
    public String getProviderTransactionReference() { return providerTransactionReference; }
    public String getCheckoutUrl() { return providerPayload == null ? null : providerPayload.checkoutUrl(); }
    public String getQrCode() { return providerPayload == null ? null : providerPayload.qrCode(); }
    public String getTransferBankBin() { return providerPayload == null ? null : providerPayload.transferBankBin(); }
    public String getTransferAccountNumber() { return providerPayload == null ? null : providerPayload.transferAccountNumber(); }
    public String getTransferAccountName() { return providerPayload == null ? null : providerPayload.transferAccountName(); }
    public String getTransferDescription() { return providerPayload == null ? null : providerPayload.transferDescription(); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public String getReconciliationReason() { return reconciliationReason; }

    /** The jsonb column provider_payload (V144): PayOS link data; null-valued keys are omitted, as the migration does. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ProviderPayload(String paymentLinkId, String checkoutUrl, String qrCode, String transferBankBin,
            String transferAccountNumber, String transferAccountName, String transferDescription) { }
}
