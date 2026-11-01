package fu.fa26.sep490.g58.otcms_be.billing.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PaymentGateway {
    String provider();
    long linkTtlSeconds();
    GatewayLink createLink(GatewayPaymentCommand command);
    VerifiedGatewayEvent verifyWebhook(Object payload);
    GatewayPaymentStatus query(long orderCode);

    record GatewayPaymentCommand(long orderCode, long amount, String description, UUID attemptId,
            Instant expiresAt, List<GatewayItem> items) { }
    record GatewayItem(String name, int quantity, long unitPrice) { }
    record GatewayLink(String providerLinkId, long orderCode, String checkoutUrl, String qrCode,
            Instant expiresAt) { }
    record VerifiedGatewayEvent(String provider, long orderCode, long amount, String currency,
            String paymentLinkId, String transactionReference, String code, boolean successful) { }
    record GatewayPaymentStatus(String status, long orderCode, long amount, long amountPaid,
            String paymentLinkId, String transactionReference, String currency) { }
}
