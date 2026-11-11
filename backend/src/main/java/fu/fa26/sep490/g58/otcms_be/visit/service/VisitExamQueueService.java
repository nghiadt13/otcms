package fu.fa26.sep490.g58.otcms_be.visit.service;

import fu.fa26.sep490.g58.otcms_be.visit.model.ExamQueueEntry;
import fu.fa26.sep490.g58.otcms_be.visit.model.ExamQueueTicket;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * The only writer of the exam queue ({@code visit_exam_queue}).
 * Write methods require an existing transaction and a held {@code CoordinationGuardService} guard
 * from the caller; queue numbers are assigned as max+1 under that guard.
 * An illegal move throws {@code VisitOperationException("VISIT_EXAM_STATE_CONFLICT")}.
 */
public interface VisitExamQueueService {
    /** Check-in of a scheduled visit or a free walk-in: queues the visit as WAITING with a new number. */
    ExamQueueTicket enqueueWaiting(UUID visitId, UUID doctorProfileId, UUID serviceIdOrNull, Instant now);

    /** Walk-in with a fee: creates the row as PENDING_PAYMENT without a number. */
    void createPendingPayment(UUID visitId, UUID doctorProfileId, UUID serviceId, Instant now);

    /** PENDING_PAYMENT to WAITING with a new number. Idempotent when the row is already WAITING. */
    ExamQueueTicket admitAfterPayment(UUID visitId, Instant now);

    /** WAITING to IN_EXAMINATION by the responsible doctor, who may have only one such visit at a time. */
    void startExamination(UUID visitId, UUID doctorProfileId, Instant now);

    /** IN_EXAMINATION to AWAITING_CONCLUSION. Skipped when there is no row or it is already AWAITING_CONCLUSION. */
    void markAwaitingConclusion(UUID visitId, Instant now);

    /** IN_EXAMINATION or AWAITING_CONCLUSION to COMPLETED by the responsible doctor. */
    void complete(UUID visitId, UUID doctorProfileId, Instant now);

    /** PENDING_PAYMENT or WAITING to CANCELLED. Skipped when there is no row. */
    void cancel(UUID visitId, Instant now);

    Optional<ExamQueueEntry> find(UUID visitId);

    /** Service id of the walk-in fee while the row is PENDING_PAYMENT; empty otherwise. */
    Optional<UUID> requiredFeeServiceId(UUID visitId);
}
