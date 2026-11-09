package fu.fa26.sep490.g58.otcms_be.appointment.service.impl;

import fu.fa26.sep490.g58.otcms_be.appointment.repository.AppointmentPaymentHoldRepository;
import fu.fa26.sep490.g58.otcms_be.appointment.service.ReleaseAppointmentPaymentHoldService;
import fu.fa26.sep490.g58.otcms_be.appointment.service.ReleaseAppointmentPaymentHoldService.Actor;
import fu.fa26.sep490.g58.otcms_be.appointment.service.ReleaseAppointmentPaymentHoldService.Outcome;
import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Safety net that releases lapsed AWAITING_PAYMENT holds. Candidates are read without any lock; each appointment is
 * then settled with billing (force=false: a still-alive QR is skipped) and released in its own transaction, so one
 * failing appointment never stops the batch.
 */
@Component
public class ExpireUnpaidAppointmentsJob {
    private static final Logger log = LoggerFactory.getLogger(ExpireUnpaidAppointmentsJob.class);
    static final int BATCH_SIZE = 50;
    private static final int MAX_BATCHES_PER_RUN = 20;

    private final AppointmentPaymentHoldRepository holds;
    private final ReleaseAppointmentPaymentHoldService release;
    private final AppointmentBookingProperties properties;
    private final Clock clock;

    public ExpireUnpaidAppointmentsJob(AppointmentPaymentHoldRepository holds,
            ReleaseAppointmentPaymentHoldService release, AppointmentBookingProperties properties, Clock clock) {
        this.holds = holds;
        this.release = release;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${appointment.booking.payment-hold-sweep-ms:30000}")
    public void scheduledSweep() {
        if (!properties.paymentHoldSweepEnabled()) return;
        try {
            sweepOnce();
        } catch (RuntimeException failure) {
            log.error("Payment hold sweep failed", failure);
        }
    }

    /** @return number of holds released (PAYMENT_EXPIRED) in this run. */
    public int sweepOnce() {
        int released = 0;
        int skipped = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            // Skipped (still pending / failed) rows stay in the result set, so page past them.
            List<UUID> ids = holds.findLapsedHoldIds(clock.instant(), BATCH_SIZE, skipped);
            for (UUID id : ids) {
                Outcome outcome = releaseOne(id);
                // RELEASED, PAID (confirmed) and NOT_AWAITING all leave the lapsed set; only STILL_PENDING stays.
                if (outcome == Outcome.RELEASED) released++;
                else if (outcome == Outcome.STILL_PENDING) skipped++;
            }
            if (ids.size() < BATCH_SIZE) break;
        }
        return released;
    }

    private Outcome releaseOne(UUID appointmentId) {
        try {
            return release.release(appointmentId, false, Actor.system("payment-hold-sweep-" + appointmentId));
        } catch (RuntimeException failure) {
            log.warn("Releasing payment hold {} failed; will retry next sweep", appointmentId, failure);
            return Outcome.STILL_PENDING;
        }
    }
}
