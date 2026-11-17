package fu.fa26.sep490.g58.otcms_be.billing.service;

import fu.fa26.sep490.g58.otcms_be.billing.service.BillingReadService.ResultPage;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Manual refunds (D7: the clinic never refunds automatically and never calls a refund API of PayOS). An item that
 * cannot be delivered after it was paid becomes REFUND_PENDING; an admin pays the patient back by hand and confirms.
 */
public interface BillingRefundService {
    /**
     * The item will not be delivered (clinic cancelled the appointment, order cancelled or changed). A PAID item
     * becomes REFUND_PENDING; an unpaid item is voided (a PayOS payment that still arrives for it later lands as
     * REFUND_PENDING). Idempotent; unknown item is MSG-41. Joins the caller's transaction when there is one.
     */
    void requestRefund(UUID itemId, String reason);

    /**
     * As {@link #requestRefund(UUID, String)}; when the item was paid, also tells the patient with MSG-97 that the
     * clinic cancelled the appointment that started at {@code clinicCancelledAppointmentStart}.
     */
    void requestRefund(UUID itemId, String reason, Instant clinicCancelledAppointmentStart);

    /**
     * The service order was cancelled without being performed and its visit is Incomplete: an unpaid item of the order
     * is voided; a paid item stays paid (BR-53, no refund). Idempotent; an order without an item is nothing to do.
     */
    void voidUnpaidServiceOrder(UUID serviceOrderId);

    /**
     * The appointment will not take place and the clinic is not at fault (patient cancelled, no-show): its unpaid fee
     * item is voided; a paid fee stays paid (no refund, D7). Idempotent; no invoice is nothing to do.
     */
    void voidUnpaidAppointmentFee(UUID appointmentId);

    /**
     * The clinic cancelled the appointment (doctor absent, nobody to replace): an unpaid fee item is voided, a paid
     * one becomes REFUND_PENDING and the patient gets MSG-97 for the appointment that started at {@code appointmentStart}.
     */
    void refundAppointmentFee(UUID appointmentId, String reason, Instant appointmentStart);

    /** ADMIN or OWNER. REFUND_PENDING becomes REFUNDED and an audit row is written; any other state is MSG-42. */
    Refund confirmRefund(UUID itemId, String note, AuthenticatedAccount actor);

    /** ADMIN or OWNER. {@code status}: PENDING (default, REFUND_PENDING items) or REFUNDED; anything else is MSG-54. */
    ResultPage<Refund> listRefunds(AuthenticatedAccount actor, String status, int page, int size);

    /** {@code status} is REFUND_PENDING or REFUNDED; {@code amount} is the item's line total. */
    record Refund(UUID itemId, UUID invoiceId, String invoiceNumber, UUID patientId, String patientName,
            String description, BigDecimal amount, String status, String reason, Instant refundedAt,
            UUID refundedByAccountId) { }
}
