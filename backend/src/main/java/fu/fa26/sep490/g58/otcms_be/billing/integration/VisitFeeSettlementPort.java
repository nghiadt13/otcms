package fu.fa26.sep490.g58.otcms_be.billing.integration;

import java.util.UUID;

/**
 * Visit's side of a walk-in fee. Billing calls it inside the payment transaction once the VISIT invoice is fully
 * paid, so the visit module can admit the walk-in to the exam queue in the same commit. It must accept a visit
 * that has nothing to admit (closed, cancelled, already admitted) without failing the payment.
 */
public interface VisitFeeSettlementPort {
    void onVisitInvoicePaid(UUID visitId);
}
