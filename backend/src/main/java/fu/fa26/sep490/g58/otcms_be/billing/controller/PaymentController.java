package fu.fa26.sep490.g58.otcms_be.billing.controller;

import fu.fa26.sep490.g58.otcms_be.billing.model.dto.request.CounterPaymentRequest;
import fu.fa26.sep490.g58.otcms_be.billing.model.dto.response.PaymentAttemptResponse;
import fu.fa26.sep490.g58.otcms_be.billing.service.PaymentService;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PaymentController {
    private final PaymentService payments;
    public PaymentController(PaymentService payments) { this.payments = payments; }

    @PostMapping("/invoices/{invoiceId}/counter-payments")
    public PaymentAttemptResponse counterPayment(@PathVariable UUID invoiceId,
            @Valid @RequestBody CounterPaymentRequest request,
            @RequestHeader("Idempotency-Key") String key,
            @AuthenticationPrincipal AuthenticatedAccount actor) {
        return payments.recordCounterPayment(invoiceId, request, actor, key);
    }

    @PostMapping("/invoices/{invoiceId}/payment-attempts")
    public PaymentAttemptResponse onlineAttempt(@PathVariable UUID invoiceId,
            @RequestHeader("Idempotency-Key") String key,
            @AuthenticationPrincipal AuthenticatedAccount actor) {
        return payments.createOnlineAttempt(invoiceId, actor, key);
    }

    @GetMapping("/payment-attempts/{attemptId}")
    public PaymentAttemptResponse getAttempt(@PathVariable UUID attemptId,
            @AuthenticationPrincipal AuthenticatedAccount actor) {
        return payments.getAttempt(attemptId, actor);
    }

    @PostMapping("/payment-attempts/{attemptId}/reconciliation")
    public PaymentAttemptResponse reconcile(@PathVariable UUID attemptId,
            @AuthenticationPrincipal AuthenticatedAccount actor) {
        return payments.reconcile(attemptId, actor);
    }
}
