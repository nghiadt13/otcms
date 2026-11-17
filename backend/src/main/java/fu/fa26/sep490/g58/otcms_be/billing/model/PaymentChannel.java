package fu.fa26.sep490.g58.otcms_be.billing.model;

public enum PaymentChannel {
    /** Cash taken at the counter by a receptionist. */
    COUNTER,
    /** PayOS checkout opened by the patient. */
    ONLINE,
    /** PayOS QR shown at the counter by a receptionist (same gateway flow as ONLINE, staff-initiated). */
    COUNTER_QR
}
