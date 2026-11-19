package fu.fa26.sep490.g58.otcms_be.common.idempotency;

/**
 * Operation families that keep an idempotency key in {@code idempotency_records} (V137).
 * The name is stored as-is in the {@code scope} column, so never rename a constant.
 */
public enum IdempotencyScope {
    APPOINTMENT_CHECKIN,
    WALK_IN_VISIT,
    ORDER_ROUND_SUBMIT,
    ORDER_REVIEW,
    ORDER_CANCEL,
    ORDER_TRANSFER,
    RESULT_DRAFT,
    RESULT_CORRECTION,
    RESULT_UPLOAD,
    RESULT_COMPLETE,
    IMAGING_UPLOAD,
    IMAGING_SEND,
    PAYMENT_ATTEMPT
}
