package fu.fa26.sep490.g58.otcms_be.billing.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_gateway_events")
public class PaymentGatewayEventJpaEntity {
    @Id private UUID id;
    @Column(nullable = false, length = 32) private String provider;
    @Column(name = "event_fingerprint", nullable = false, length = 64) private String eventFingerprint;
    @Column(name = "provider_order_code") private Long providerOrderCode;
    @Column(name = "provider_transaction_reference", length = 128) private String providerTransactionReference;
    @Column(name = "signature_verified", nullable = false) private boolean signatureVerified;
    @Column(nullable = false, length = 32) private String outcome;
    @Column(name = "outcome_detail", length = 200) private String outcomeDetail;
    @Column(name = "received_at", nullable = false, insertable = false, updatable = false) private Instant receivedAt;

    protected PaymentGatewayEventJpaEntity() { }
    public PaymentGatewayEventJpaEntity(UUID id, String provider, String fingerprint, Long orderCode,
            String transactionReference, boolean verified, String outcome, String outcomeDetail) {
        this.id = id;
        this.provider = provider;
        this.eventFingerprint = fingerprint;
        this.providerOrderCode = orderCode;
        this.providerTransactionReference = transactionReference;
        this.signatureVerified = verified;
        this.outcome = outcome;
        this.outcomeDetail = outcomeDetail;
    }
}
