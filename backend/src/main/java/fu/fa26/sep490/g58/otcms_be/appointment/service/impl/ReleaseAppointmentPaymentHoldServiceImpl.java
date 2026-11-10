package fu.fa26.sep490.g58.otcms_be.appointment.service.impl;

import fu.fa26.sep490.g58.otcms_be.appointment.service.ReleaseAppointmentPaymentHoldService;
import fu.fa26.sep490.g58.otcms_be.billing.service.AppointmentHoldSettlementService;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Deliberately not transactional: billing settles the invoice first (it may call the gateway), then
 * {@link AppointmentHoldExpiryTransaction} locks the appointment row. When the caller already runs in a
 * transaction (create, cancel) both steps simply join it.
 */
@Service
public class ReleaseAppointmentPaymentHoldServiceImpl implements ReleaseAppointmentPaymentHoldService {
    private final AppointmentHoldSettlementService settlement;
    private final AppointmentHoldExpiryTransaction expiry;

    public ReleaseAppointmentPaymentHoldServiceImpl(AppointmentHoldSettlementService settlement,
            AppointmentHoldExpiryTransaction expiry) {
        this.settlement = settlement;
        this.expiry = expiry;
    }

    @Override
    public Outcome release(UUID appointmentId, boolean force, Actor actor) {
        if (appointmentId == null || actor == null || actor.reason() == null || actor.reason().isBlank()
                || actor.correlationId() == null || actor.correlationId().isBlank()) {
            throw new IllegalArgumentException("Appointment, reason and correlation id are required to release a hold");
        }
        return switch (settlement.settle(appointmentId, force)) {
            case PAID -> Outcome.PAID;
            case STILL_PENDING -> Outcome.STILL_PENDING;
            case VOIDED, NO_INVOICE -> expiry.expire(appointmentId, actor);
        };
    }
}
