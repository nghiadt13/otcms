package fu.fa26.sep490.g58.otcms_be.appointment.model;

import java.time.Instant;
import java.util.UUID;

/** Appointment facts the payment-hold transitions need (and audit): who, when, and the current status. */
public record PaymentHoldRow(UUID id, UUID patientProfileId, UUID serviceId, UUID doctorProfileId, String status,
        Instant startTime, Instant endTime, UUID createdByAccountId, Instant paymentDeadline) {
}
