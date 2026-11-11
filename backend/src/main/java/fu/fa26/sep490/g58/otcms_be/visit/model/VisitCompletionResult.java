package fu.fa26.sep490.g58.otcms_be.visit.model;

import java.time.Instant;
import java.util.UUID;

/** Outcome of a doctor closing a visit: the visit is CLOSED and its queue entry COMPLETED at {@code closedAt}. */
public record VisitCompletionResult(UUID visitId, VisitExamStatus examStatus, Instant closedAt) {
}
