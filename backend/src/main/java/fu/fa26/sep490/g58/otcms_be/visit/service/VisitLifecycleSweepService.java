package fu.fa26.sep490.g58.otcms_be.visit.service;

import java.time.Instant;

/**
 * The two nightly lifecycle sweeps of a visit (NF-05). Each visit is decided in its own transaction, so one failing
 * visit never stops the batch; both sweeps are idempotent and safe to run twice.
 */
public interface VisitLifecycleSweepService {
    /** NF-05(b): OPEN visits Awaiting Conclusion whose 7-day period ended before {@code now} become INCOMPLETE. */
    int sweepIncomplete(Instant now);

    /** NF-05(a): end of the clinic day. Returns how many visits changed. */
    int closeEndOfDay(Instant now);
}
