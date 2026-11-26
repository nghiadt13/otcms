package fu.fa26.sep490.g58.otcms_be.idempotency.model;

/** Operation families that persist replay keys. These names are stored in idempotency_records.scope. */
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
