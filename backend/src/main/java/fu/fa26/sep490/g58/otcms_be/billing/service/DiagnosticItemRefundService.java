package fu.fa26.sep490.g58.otcms_be.billing.service;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DiagnosticItemRefundService {
    RefundView request(UUID invoiceId, int lineNumber, UUID incidentId, String reason, String key, AuthenticatedAccount actor);
    List<RefundView> pending(int page, int size, AuthenticatedAccount actor);
    List<RefundView> approved(int page, int size, AuthenticatedAccount actor);
    RefundView decide(UUID refundId, String decision, String reason, AuthenticatedAccount actor);
    RefundView recordExecution(UUID refundId, String providerReference, AuthenticatedAccount actor);
    record RefundView(UUID id, UUID invoiceId, int lineNumber, UUID orderId, UUID incidentId, String reason,
            String status, BigDecimal amount, UUID requestedBy, UUID approvedBy, String decisionReason,
            String providerReference, Instant requestedAt, Instant decidedAt, Instant refundedAt) { }
}
