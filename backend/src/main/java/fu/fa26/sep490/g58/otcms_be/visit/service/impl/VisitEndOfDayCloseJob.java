package fu.fa26.sep490.g58.otcms_be.visit.service.impl;

import fu.fa26.sep490.g58.otcms_be.visit.service.VisitLifecycleSweepService;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** NF-05(a): every day at 23:30 clinic time, waiting visits are Left and open queue entries are closed. */
@Component
public class VisitEndOfDayCloseJob {
    private static final Logger log = LoggerFactory.getLogger(VisitEndOfDayCloseJob.class);
    private final VisitLifecycleSweepService sweeps;
    private final Clock clock;
    private final boolean enabled;

    public VisitEndOfDayCloseJob(VisitLifecycleSweepService sweeps, Clock clock,
            @Value("${otcms.visit.lifecycle-jobs-enabled:true}") boolean enabled) {
        this.sweeps = sweeps;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Scheduled(cron = "0 30 23 * * *", zone = "${appointment.booking.clinic-time-zone:Asia/Ho_Chi_Minh}")
    public void scheduledClose() {
        if (!enabled) return;
        try {
            runOnce();
        } catch (RuntimeException failure) {
            log.error("End-of-day visit close failed", failure);
        }
    }

    /** @return number of visits changed in this run */
    public int runOnce() {
        int changed = sweeps.closeEndOfDay(clock.instant());
        if (changed > 0) log.info("End-of-day close: {} visit(s) changed", changed);
        return changed;
    }
}
