package fu.fa26.sep490.g58.otcms_be.visit.service.impl;

import fu.fa26.sep490.g58.otcms_be.visit.service.VisitLifecycleSweepService;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** NF-05(b), BR-65: every day at 00:15 clinic time, visits whose 7-day period ended become Incomplete. */
@Component
public class VisitIncompleteSweepJob {
    private static final Logger log = LoggerFactory.getLogger(VisitIncompleteSweepJob.class);
    private final VisitLifecycleSweepService sweeps;
    private final Clock clock;
    private final boolean enabled;

    public VisitIncompleteSweepJob(VisitLifecycleSweepService sweeps, Clock clock,
            @Value("${otcms.visit.lifecycle-jobs-enabled:true}") boolean enabled) {
        this.sweeps = sweeps;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Scheduled(cron = "0 15 0 * * *", zone = "${appointment.booking.clinic-time-zone:Asia/Ho_Chi_Minh}")
    public void scheduledSweep() {
        if (!enabled) return;
        try {
            runOnce();
        } catch (RuntimeException failure) {
            log.error("Incomplete visit sweep failed", failure);
        }
    }

    /** @return number of visits that became Incomplete in this run */
    public int runOnce() {
        int changed = sweeps.sweepIncomplete(clock.instant());
        if (changed > 0) log.info("Incomplete sweep: {} visit(s) became Incomplete", changed);
        return changed;
    }
}
