package fu.fa26.sep490.g58.otcms_be.billing.service;

import java.util.UUID;

/** Read-only billing answers for other features (public boundary of the billing package). */
public interface BillingQueryService {
    /**
     * True when the invoice item of the service order is paid and still stands: its attempt is CONFIRMED and the item
     * is neither void nor waiting for / done with a refund. False for an unknown order or an order not billed yet.
     */
    boolean isServiceOrderPaid(UUID serviceOrderId);
}
