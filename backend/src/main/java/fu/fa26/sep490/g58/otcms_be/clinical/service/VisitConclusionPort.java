package fu.fa26.sep490.g58.otcms_be.clinical.service;

import java.time.Instant;
import java.util.UUID;

/** Clinical-owned write boundary: ordering imaging moves the visit's exam queue entry to AWAITING_CONCLUSION. */
public interface VisitConclusionPort {
    /**
     * IN_EXAMINATION becomes AWAITING_CONCLUSION. A visit already awaiting, or without a queue entry, is left as it is.
     * Runs inside the caller's transaction.
     */
    void markAwaitingConclusion(UUID visitId, Instant now);
}
