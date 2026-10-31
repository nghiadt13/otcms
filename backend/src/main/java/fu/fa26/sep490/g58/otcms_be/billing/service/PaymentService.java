package fu.fa26.sep490.g58.otcms_be.billing.service;

import fu.fa26.sep490.g58.otcms_be.billing.model.dto.request.CounterPaymentRequest;
import fu.fa26.sep490.g58.otcms_be.billing.model.dto.response.PaymentAttemptResponse;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import java.util.UUID;

public interface PaymentService {
    PaymentAttemptResponse recordCounterPayment(UUID invoiceId, CounterPaymentRequest request,
            AuthenticatedAccount actor, String idempotencyKey);
    PaymentAttemptResponse createOnlineAttempt(UUID invoiceId, AuthenticatedAccount actor, String idempotencyKey);
    PaymentAttemptResponse getAttempt(UUID attemptId, AuthenticatedAccount actor);
    void handleVerifiedGatewayEvent(PaymentGateway.VerifiedGatewayEvent event);
    void recordRejectedGatewayEvent(String provider, Object payload);
    PaymentAttemptResponse reconcile(UUID attemptId, AuthenticatedAccount actor);
}
