package fu.fa26.sep490.g58.otcms_be.clinical.service;

import java.util.UUID;

/** Billing action required when a doctor cancels an unpaid clinical order. */
public interface ClinicalOrderCancellationBillingPort {
    void voidUnpaidOrder(UUID orderId);
}
