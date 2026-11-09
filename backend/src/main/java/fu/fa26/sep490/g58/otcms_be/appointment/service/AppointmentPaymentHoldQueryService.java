package fu.fa26.sep490.g58.otcms_be.appointment.service;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Read-only view of the payment hold for billing (QR expiry cap, hiding invoices of held appointments). */
public interface AppointmentPaymentHoldQueryService {

    /** Deadline of a live hold; empty unless the appointment is currently AWAITING_PAYMENT. */
    Optional<Instant> findLiveHoldDeadline(UUID appointmentId);

    /** Subset of the given ids whose appointment is AWAITING_PAYMENT or PAYMENT_EXPIRED. */
    Set<UUID> findHoldOrReleasedAppointmentIds(Collection<UUID> appointmentIds);
}
