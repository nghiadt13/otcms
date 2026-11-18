package fu.fa26.sep490.g58.otcms_be.visit.service;

import java.util.UUID;

/** NF-04: the patient joins the result-review queue when every result of the latest ordering round is available. */
public interface ResultReviewQueueService {
    /**
     * Public hook: call it in the same transaction that marked the order RESULTS_AVAILABLE (imaging study linked,
     * laboratory results approved). When all non-cancelled orders of the visit's latest round have results, creates
     * one RESULT_REVIEW entry in the attending doctor's room and tells the patient with MSG-74. A single result of
     * a larger round does nothing. Idempotent while a RESULT_REVIEW entry of the visit is open; never throws for
     * data that is simply missing (no doctor room, visit not OPEN).
     */
    void onOrderResultAvailable(UUID serviceOrderId);
}
