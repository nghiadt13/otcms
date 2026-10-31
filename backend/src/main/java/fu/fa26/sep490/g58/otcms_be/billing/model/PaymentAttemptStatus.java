package fu.fa26.sep490.g58.otcms_be.billing.model;

public enum PaymentAttemptStatus {
    PENDING,
    CONFIRMED,
    FAILED,
    CANCELLED,
    EXPIRED,
    RECONCILIATION_REQUIRED
}
