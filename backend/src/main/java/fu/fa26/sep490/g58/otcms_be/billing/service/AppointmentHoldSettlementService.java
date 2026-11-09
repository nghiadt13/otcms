package fu.fa26.sep490.g58.otcms_be.billing.service;

import java.util.UUID;

/**
 * Billing side of releasing an AWAITING_PAYMENT hold (sweeper, user cancel, new booking). The appointment feature
 * calls this BEFORE it marks the appointment PAYMENT_EXPIRED and holds no appointment row lock while calling
 * (lock order is invoice first, appointment second). Implementations must not run the gateway query inside a
 * transaction that keeps the invoice locked.
 */
public interface AppointmentHoldSettlementService {

    enum Outcome {
        /** Gateway says paid: payment confirmed and the appointment was confirmed through ConfirmAppointmentPaymentService. */
        PAID,
        /** Open invoice voided and pending attempts closed: the caller may release the hold. */
        VOIDED,
        /** The appointment has no invoice yet: the caller may release the hold. */
        NO_INVOICE,
        /** (force=false only) a payment link is still alive: the caller must skip this round. */
        STILL_PENDING
    }

    /**
     * @param force true for user-driven release (leave page / cancel / new booking): void even if a QR is still alive.
     *              false for the sweeper: return STILL_PENDING while an unexpired attempt exists.
     */
    Outcome settle(UUID appointmentId, boolean force);
}
