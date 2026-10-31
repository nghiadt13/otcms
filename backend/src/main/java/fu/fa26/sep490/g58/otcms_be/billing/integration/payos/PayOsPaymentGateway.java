package fu.fa26.sep490.g58.otcms_be.billing.integration.payos;

import fu.fa26.sep490.g58.otcms_be.billing.config.PayOsProperties;
import fu.fa26.sep490.g58.otcms_be.billing.service.PaymentGateway;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.v2.paymentRequests.PaymentLinkItem;
import vn.payos.model.webhooks.Webhook;

@Component
@ConditionalOnProperty(prefix = "app.payment.payos", name = "enabled", havingValue = "true")
public class PayOsPaymentGateway implements PaymentGateway {
    private final PayOS payOS;
    private final PayOsProperties properties;
    private final ObjectMapper objectMapper;

    public PayOsPaymentGateway(PayOS payOS, PayOsProperties properties, ObjectMapper objectMapper) {
        this.payOS = payOS;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override public String provider() { return "PAYOS"; }
    @Override public long linkTtlSeconds() { return Math.max(60, properties.linkTtlSeconds()); }

    @Override
    public GatewayLink createLink(GatewayPaymentCommand command) {
        List<PaymentLinkItem> items = command.items().stream().map(item -> PaymentLinkItem.builder()
                .name(shorten(item.name(), 80)).quantity(item.quantity()).price(item.unitPrice()).build()).toList();
        var builder = CreatePaymentLinkRequest.builder()
                .orderCode(command.orderCode()).amount(command.amount())
                .description(shorten(command.description(), 25))
                .returnUrl(callbackUrl(properties.returnUrl(), command.attemptId(), false))
                .cancelUrl(callbackUrl(properties.cancelUrl(), command.attemptId(), true)).items(items);
        Instant expiresAt = command.expiresAt();
        if (expiresAt != null) builder.expiredAt(expiresAt.getEpochSecond());
        var response = payOS.paymentRequests().create(builder.build());
        Instant providerExpiry = response.getExpiredAt() == null
                ? expiresAt : Instant.ofEpochSecond(response.getExpiredAt());
        return new GatewayLink(response.getPaymentLinkId(), response.getOrderCode(), response.getCheckoutUrl(),
                response.getQrCode(), providerExpiry);
    }

    @Override
    public VerifiedGatewayEvent verifyWebhook(Object payload) {
        Webhook webhook = payload instanceof Webhook typed ? typed : objectMapper.convertValue(payload, Webhook.class);
        var data = payOS.webhooks().verify(webhook);
        String code = data.getCode();
        boolean success = Boolean.TRUE.equals(webhook.getSuccess())
                && "00".equals(webhook.getCode()) && "00".equals(code);
        return new VerifiedGatewayEvent(provider(), data.getOrderCode(), data.getAmount(), data.getCurrency(),
                data.getPaymentLinkId(), data.getReference(), code, success);
    }

    @Override
    public GatewayPaymentStatus query(long orderCode) {
        PaymentLink payment = payOS.paymentRequests().get(orderCode);
        String reference = payment.getTransactions() == null || payment.getTransactions().isEmpty()
                ? null : payment.getTransactions().getFirst().getReference();
        return new GatewayPaymentStatus(payment.getStatus().getValue(), payment.getOrderCode(),
                payment.getAmount(), payment.getAmountPaid(), payment.getId(), reference, "VND");
    }

    private static String shorten(String value, int max) {
        String clean = value == null ? "" : value.replaceAll("[\\p{Cntrl}]", " ").trim();
        return clean.length() <= max ? clean : clean.substring(0, max);
    }

    private static String callbackUrl(String baseUrl, UUID attemptId, boolean cancelled) {
        var builder = UriComponentsBuilder.fromUriString(baseUrl).queryParam("attemptId", attemptId);
        if (cancelled) builder.queryParam("cancelled", "true");
        return builder.build().encode().toUriString();
    }
}
