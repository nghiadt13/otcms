package fu.fa26.sep490.g58.otcms_be.billing.controller;

import fu.fa26.sep490.g58.otcms_be.billing.exception.BillingException;
import fu.fa26.sep490.g58.otcms_be.billing.service.PaymentGatewayRegistry;
import fu.fa26.sep490.g58.otcms_be.billing.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payment-webhooks")
public class PaymentWebhookController {
    private final PaymentGatewayRegistry gateways;
    private final PaymentService payments;
    public PaymentWebhookController(PaymentGatewayRegistry gateways, PaymentService payments) {
        this.gateways = gateways;
        this.payments = payments;
    }

    @PostMapping("/payos")
    public ResponseEntity<Void> payOs(@RequestBody Object payload) {
        fu.fa26.sep490.g58.otcms_be.billing.service.PaymentGateway.VerifiedGatewayEvent verified;
        try {
            verified = gateways.require("PAYOS").verifyWebhook(payload);
        } catch (RuntimeException exception) {
            if (exception instanceof BillingException billing && "GATEWAY_UNAVAILABLE".equals(billing.code())) throw billing;
            payments.recordRejectedGatewayEvent("PAYOS", payload);
            throw new BillingException("INVALID_WEBHOOK", exception);
        }
        payments.handleVerifiedGatewayEvent(verified);
        return ResponseEntity.ok().build();
    }
}
