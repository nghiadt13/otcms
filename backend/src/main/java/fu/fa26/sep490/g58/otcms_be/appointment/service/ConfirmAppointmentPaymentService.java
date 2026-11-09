package fu.fa26.sep490.g58.otcms_be.appointment.service;

import java.util.UUID;

/**
 * Public entry point for billing: a payment for the appointment invoice completed, so the
 * AWAITING_PAYMENT hold becomes a real booking. Called inside the payment transaction, so it MUST NOT throw for
 * expected states (already confirmed, hold released, appointment missing) - it reports them and billing marks the
 * attempt for reconciliation instead of failing the webhook.
 */
public interface ConfirmAppointmentPaymentService {

    enum Result {
        /** AWAITING_PAYMENT -> CONFIRMED just now (booking notification sent once). */
        CONFIRMED,
        /** Already CONFIRMED (or later): nothing changed, no second notification. */
        ALREADY_CONFIRMED,
        /** Hold released (PAYMENT_EXPIRED/CANCELLED), appointment missing or not an online hold. */
        NOT_AWAITING
    }

    Result confirmPaid(UUID appointmentId);
}
