package fu.fa26.sep490.g58.otcms_be.appointment.model.dto.response;

/**
 * Result of POST /api/v1/appointments/pending-hold/cancel.
 *
 * @param cancelledCount holds released
 * @param alreadyPaid    true when a hold turned out to be paid: it was confirmed instead of cancelled
 */
public record CancelPendingHoldResponse(int cancelledCount, boolean alreadyPaid) {
}
