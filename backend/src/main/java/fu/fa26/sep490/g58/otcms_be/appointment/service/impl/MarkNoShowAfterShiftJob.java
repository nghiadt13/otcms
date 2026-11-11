package fu.fa26.sep490.g58.otcms_be.appointment.service.impl;

import fu.fa26.sep490.g58.otcms_be.appointment.repository.AppointmentNoShowRepository;
import fu.fa26.sep490.g58.otcms_be.appointment.service.impl.MarkNoShowAfterShiftTransaction.Outcome;
import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Automatic no-show (BR-64). Candidates are read without any lock; each booking is then decided in its own
 * transaction, so one failing booking never stops the batch and is retried by the next run.
 */
@Component
public class MarkNoShowAfterShiftJob {
    private static final Logger log = LoggerFactory.getLogger(MarkNoShowAfterShiftJob.class);
    static final int BATCH_SIZE = 50;
    private static final int MAX_BATCHES_PER_RUN = 20;
    /** Only bookings that started inside this window are scanned, which bounds the query on a large table. */
    static final Duration LOOKBACK = Duration.ofDays(2);

    private final AppointmentNoShowRepository appointments;
    private final MarkNoShowAfterShiftTransaction transaction;
    private final AppointmentBookingProperties properties;
    private final Clock clock;

    public MarkNoShowAfterShiftJob(AppointmentNoShowRepository appointments,
            MarkNoShowAfterShiftTransaction transaction, AppointmentBookingProperties properties, Clock clock) {
        this.appointments = appointments;
        this.transaction = transaction;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${appointment.booking.no-show-sweep-ms:600000}")
    public void scheduledSweep() {
        if (!properties.noShowSweepEnabled()) return;
        try {
            sweepOnce();
        } catch (RuntimeException failure) {
            log.error("No-show sweep failed", failure);
        }
    }

    /** @return number of bookings marked NO_SHOW in this run. */
    public int sweepOnce() {
        Instant now = clock.instant();
        Instant since = now.minus(LOOKBACK);
        int marked = 0;
        int pending = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            // Pending rows (shift still running, or a failed attempt) stay in the result set, so page past them.
            List<UUID> ids = appointments.findNoShowCandidateIds(now, since, BATCH_SIZE, pending);
            for (UUID id : ids) {
                Outcome outcome = decide(id, now);
                if (outcome == Outcome.MARKED) marked++;
                else if (outcome == Outcome.PENDING) pending++;
            }
            if (ids.size() < BATCH_SIZE) break;
        }
        return marked;
    }

    private Outcome decide(UUID appointmentId, Instant now) {
        try {
            return transaction.markNoShow(appointmentId, now);
        } catch (RuntimeException failure) {
            log.warn("Automatic no-show for booking {} failed; will retry next sweep", appointmentId, failure);
            return Outcome.PENDING;
        }
    }
}
