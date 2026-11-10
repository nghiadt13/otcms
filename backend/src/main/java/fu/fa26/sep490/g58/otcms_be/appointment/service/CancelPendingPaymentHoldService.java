package fu.fa26.sep490.g58.otcms_be.appointment.service;

import java.util.UUID;

/**
 * Cancels every live payment hold of the signed-in account (leaving the payment step, reload, sign-out, "Cancel").
 * Idempotent: with no hold it succeeds and reports zero.
 */
public interface CancelPendingPaymentHoldService {

    /**
     * @param cancelledCount holds released (now PAYMENT_EXPIRED)
     * @param alreadyPaid    true when billing found a hold already paid: that appointment was confirmed instead
     */
    record Result(int cancelledCount, boolean alreadyPaid) { }

    Result cancelAll(UUID accountId, String correlationId);
}
