package fu.fa26.sep490.g58.otcms_be.billing.model;

import fu.fa26.sep490.g58.otcms_be.billing.model.entity.InvoiceItemJpaEntity;
import fu.fa26.sep490.g58.otcms_be.billing.model.entity.PaymentAttemptJpaEntity;
import java.time.Instant;

/**
 * Derived state of one invoice item (nothing here is stored as a status column). The only stored facts are
 * {@code payment_attempt_id}, {@code voided_at} and {@code refund_status}; the rest comes from the linked attempt.
 * An item keeps its link to an attempt that expired or failed, so a late PayOS webhook still knows which items it
 * pays; such an item is simply payable again (a new attempt or a counter payment re-points the link).
 */
public enum InvoiceItemStatus {
    /** Payable: nobody holds it. */
    PENDING,
    /** An online/QR attempt is open (or awaiting reconciliation) for this item. */
    PAYMENT_PENDING,
    PAID,
    /** Cancelled before it was paid. */
    VOID,
    /** Paid, and the clinic owes the money back; refunded by hand. */
    REFUND_PENDING,
    REFUNDED;

    public static final String REFUND_PENDING_VALUE = "REFUND_PENDING";
    public static final String REFUNDED_VALUE = "REFUNDED";

    /** {@code attempt} is the attempt the item points to, or null when it points to none. */
    public static InvoiceItemStatus resolve(InvoiceItemJpaEntity item, PaymentAttemptJpaEntity attempt, Instant now) {
        if (REFUNDED_VALUE.equals(item.getRefundStatus())) return REFUNDED;
        if (REFUND_PENDING_VALUE.equals(item.getRefundStatus())) return REFUND_PENDING;
        if (item.getVoidedAt() != null) return VOID;
        if (attempt == null) return PENDING;
        return switch (attempt.getStatus()) {
            case CONFIRMED -> PAID;
            case RECONCILIATION_REQUIRED -> PAYMENT_PENDING;
            // A PENDING attempt past its expiry is treated as closed: the item is free to be taken over.
            case PENDING -> attempt.isOpenAt(now) ? PAYMENT_PENDING : PENDING;
            case FAILED, CANCELLED, EXPIRED -> PENDING;
        };
    }
}
