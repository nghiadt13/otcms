package fu.fa26.sep490.g58.otcms_be.appointment.service.impl;

import fu.fa26.sep490.g58.otcms_be.appointment.repository.AppointmentPaymentHoldRepository;
import fu.fa26.sep490.g58.otcms_be.appointment.service.ReleaseAppointmentPaymentHoldService.Actor;
import fu.fa26.sep490.g58.otcms_be.appointment.service.ReleaseAppointmentPaymentHoldService.Outcome;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * The appointment-row half of releasing a hold. Joins the caller's transaction (create / cancel) or opens its own
 * (sweeper, one transaction per appointment). It runs AFTER billing settled the invoice, so the row lock taken here
 * never precedes the invoice lock.
 */
@Component
public class AppointmentHoldExpiryTransaction {
    private final AppointmentPaymentHoldRepository holds;
    private final Clock clock;

    public AppointmentHoldExpiryTransaction(AppointmentPaymentHoldRepository holds, Clock clock) {
        this.holds = holds;
        this.clock = clock;
    }

    /** @return RELEASED, or NOT_AWAITING when the row is gone or already left AWAITING_PAYMENT. */
    @Transactional
    public Outcome expire(UUID appointmentId, Actor actor) {
        var row = holds.findForUpdate(appointmentId).orElse(null);
        if (row == null || !"AWAITING_PAYMENT".equals(row.status())) return Outcome.NOT_AWAITING;
        Instant now = Instant.now(clock);
        if (holds.markPaymentExpired(appointmentId, now) != 1) return Outcome.NOT_AWAITING;
        // audit_actor_account_id is NOT NULL: the system acts on behalf of the account that created the hold.
        UUID actorAccount = actor.accountId() != null ? actor.accountId() : row.createdByAccountId();
        holds.insertStatusAudit(UUID.randomUUID(), appointmentId, actorAccount, actor.actorType(),
                "AWAITING_PAYMENT", "PAYMENT_EXPIRED", row.doctorProfileId(), row.startTime(), row.endTime(),
                actor.reason(), now, actor.correlationId());
        return Outcome.RELEASED;
    }
}
