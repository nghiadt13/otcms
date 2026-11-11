package fu.fa26.sep490.g58.otcms_be.diagnostic.service;

import java.util.UUID;

/** Billing's verified item-refund handoff; only that item's unstarted imaging request is cancelled. */
public interface DiagnosticRefundCancellationService {
    void cancelRefundedOrder(UUID eventId, UUID invoiceId, UUID serviceOrderId, String reason);
}
