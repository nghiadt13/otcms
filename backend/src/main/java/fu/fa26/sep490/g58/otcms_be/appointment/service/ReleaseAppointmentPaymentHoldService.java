package fu.fa26.sep490.g58.otcms_be.appointment.service;

import java.util.UUID;

/**
 * Releases one AWAITING_PAYMENT hold: asks billing to settle first (a paid invoice confirms the appointment instead),
 * then moves the appointment to PAYMENT_EXPIRED. Shared by the sweeper, user cancel, pending-hold cancel and the
 * lazy release inside appointment creation.
 *
 * <p>Lock order is invoice first, appointment second: implementations hold no appointment row lock while calling
 * billing, and lock the appointment row only afterwards.
 */
public interface ReleaseAppointmentPaymentHoldService {

    enum Outcome {
        /** Hold released: the appointment is now PAYMENT_EXPIRED. */
        RELEASED,
        /** Billing found the payment completed: the appointment is CONFIRMED, nothing was released. */
        PAID,
        /** (force=false only) a payment link is still alive: nothing changed, retry later. */
        STILL_PENDING,
        /** The appointment is missing or no longer AWAITING_PAYMENT (confirmed/released concurrently). */
        NOT_AWAITING
    }

    /**
     * Who is releasing, for the audit row.
     *
     * @param accountId  acting account, or null for the system (the booking account is then recorded)
     * @param actorType  PATIENT or SYSTEM
     * @param reason     audit reason (1..1000 characters)
     */
    record Actor(UUID accountId, String actorType, String reason, String correlationId) {
        public static final String EXPIRED_REASON = "Hết thời gian thanh toán";

        public static Actor system(String correlationId) {
            return new Actor(null, "SYSTEM", EXPIRED_REASON, correlationId);
        }

        public static Actor patient(UUID accountId, String reason, String correlationId) {
            return new Actor(accountId, "PATIENT", reason, correlationId);
        }
    }

    /**
     * @param force true for user-driven release (void even if a QR is alive); false for the sweeper
     *              (skip while an unexpired payment attempt exists)
     */
    Outcome release(UUID appointmentId, boolean force, Actor actor);
}
