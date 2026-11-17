package fu.fa26.sep490.g58.otcms_be.visit.service.impl;

import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitLifecycleRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitLifecycleSweepService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class VisitLifecycleSweepServiceImpl implements VisitLifecycleSweepService {
    private static final Logger log = LoggerFactory.getLogger(VisitLifecycleSweepServiceImpl.class);

    private final VisitLifecycleRepository lifecycle;
    private final VisitLifecycleVisitTransaction transaction;
    private final AppointmentBookingProperties booking;

    public VisitLifecycleSweepServiceImpl(VisitLifecycleRepository lifecycle,
            VisitLifecycleVisitTransaction transaction, AppointmentBookingProperties booking) {
        this.lifecycle = lifecycle;
        this.transaction = transaction;
        this.booking = booking;
    }

    @Override
    public int sweepIncomplete(Instant now) {
        // Day 8 or later: check-in day <= today - 7.
        var lastDueDay = now.atZone(booking.clinicTimeZone()).toLocalDate()
                .minusDays(VisitLifecycleVisitTransaction.PERIOD_DAYS);
        List<UUID> candidates = lifecycle.findIncompleteCandidates(lastDueDay, booking.clinicTimeZone());
        int changed = 0;
        for (UUID visitId : candidates) {
            try {
                if (transaction.markIncompleteIfDue(visitId, now)) changed++;
            } catch (RuntimeException failure) {
                log.warn("Incomplete sweep of visit {} failed; it is retried by the next run", visitId, failure);
            }
        }
        return changed;
    }

    @Override
    public int closeEndOfDay(Instant now) {
        List<UUID> candidates = lifecycle.findEndOfDayCandidates();
        int changed = 0;
        for (UUID visitId : candidates) {
            try {
                if (transaction.closeEndOfDay(visitId, now)) changed++;
            } catch (RuntimeException failure) {
                log.warn("End-of-day close of visit {} failed; it is retried by the next run", visitId, failure);
            }
        }
        return changed;
    }
}
