package fu.fa26.sep490.g58.otcms_be.billing.integration;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Billing's view of the appointment payment hold. Appointment owns the booking state; billing only asks it to
 * confirm after money arrived and to read the hold deadline. Joins the caller's transaction (see
 * {@link #confirmPaid}); it never throws for expected states.
 */
public interface AppointmentPaymentPort {

    enum ConfirmResult {
        /** AWAITING_PAYMENT became CONFIRMED just now. */
        CONFIRMED,
        /** Already CONFIRMED (or later): nothing changed. */
        ALREADY_CONFIRMED,
        /** Hold released or appointment missing: the payment must be reconciled by staff. */
        NOT_AWAITING
    }

    /** Called inside the payment transaction after the invoice row is locked (lock order: invoice, appointment). */
    ConfirmResult confirmPaid(UUID appointmentId);

    /** Deadline of a live hold; empty unless the appointment is currently AWAITING_PAYMENT. */
    Optional<Instant> liveHoldDeadline(UUID appointmentId);

    /** Subset of the ids whose appointment is AWAITING_PAYMENT or PAYMENT_EXPIRED (hidden from invoice lists). */
    Set<UUID> heldOrReleasedIds(Collection<UUID> appointmentIds);
}
