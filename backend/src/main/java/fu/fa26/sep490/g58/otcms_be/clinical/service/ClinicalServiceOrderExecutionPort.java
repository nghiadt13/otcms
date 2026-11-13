package fu.fa26.sep490.g58.otcms_be.clinical.service;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Imaging execution columns of {@code clinical_service_orders}, for the diagnostic module (final-db phase 03,
 * decision Q7: one JPA entity per table, so diagnostic does not map its own entity on this table).
 *
 * <p>An order is a diagnostic request once reception dispatched it to a room ({@code execution_status} not null);
 * the request id in the API is the order id. Every method joins the caller's transaction (MANDATORY).
 *
 * <p>Writes are guarded native UPDATEs that return the number of changed rows (0 = the row was not in the expected
 * state or version; the caller re-reads and decides between replay and conflict). They never touch {@code version}:
 * that is the optimistic-lock column of clinical's ServiceOrderJpaEntity, which may be loaded in the same transaction.
 * Lock the row first with {@link #findForUpdate} where the old code locked the diagnostic request.
 *
 * <p>Optimistic locking of the diagnostic API ({@code version} in responses, {@code expectedVersion} in requests) uses
 * {@code execution_version} ({@link ImagingExecution#executionVersion()}), which plays the role of the old
 * diagnostic_requests @Version: 0 after dispatch, +1 on every later write below. Callers compare the request's
 * expectedVersion with it (VERSION_CONFLICT as before) and pass the version they read to the write, which matches
 * only that version.
 */
public interface ClinicalServiceOrderExecutionPort {
    /** Execution status values, as stored in {@code execution_status}. */
    enum ExecutionStatus { PENDING, IN_PROGRESS, RESULT_AVAILABLE, REVIEWED, CANCELLED }

    /** The diagnostic view of one dispatched imaging order (was a diagnostic_requests row + its current attempt). */
    record ImagingExecution(UUID orderId, UUID visitId, UUID patientId, UUID orderingDoctorAccountId,
            UUID serviceId, UUID roomId, String imagingType, String bodyArea, String clinicalReason,
            ExecutionStatus status, String cancellationCause, String cancellationReason,
            UUID attemptId, UUID technicianAccountId, String firstUseType, UUID firstUseEventId,
            Instant firstUseAt, String firstUseSourceId, String studyRef,
            String reviewIdempotencyKey, String reviewFingerprint,
            String cancelIdempotencyKey, String cancelFingerprint,
            String transferIdempotencyKey, String transferFingerprint,
            long executionVersion, Instant createdAt, Instant updatedAt) { }

    // ---- reads (only dispatched orders: execution_status is not null) ----------------------------------------

    Optional<ImagingExecution> find(UUID orderId);

    /** SELECT ... FOR UPDATE; use before any write that the old code did on a locked diagnostic request. */
    Optional<ImagingExecution> findForUpdate(UUID orderId);

    /** The request whose current attempt produced {@code studyRef} (uk_clinical_orders_study_ref). */
    Optional<ImagingExecution> findByStudyRef(String studyRef);

    /** Newest first (created_at desc, id desc). */
    List<ImagingExecution> findAllByOrderIds(Collection<UUID> orderIds);

    /** Newest first. */
    List<ImagingExecution> findAllByVisit(UUID visitId);

    /** Newest first. */
    List<ImagingExecution> findAllByPatient(UUID patientId);

    /** Newest first. Any of the filters may be null (= not filtered). */
    Page<ImagingExecution> page(UUID visitId, UUID patientId, UUID orderingDoctorAccountId, UUID roomId,
            Collection<ExecutionStatus> statuses, boolean oldestFirst, Pageable pageable);

    /** PENDING requests created before {@code createdBefore}, oldest first. */
    List<ImagingExecution> findPendingCreatedBefore(Instant createdBefore);

    // ---- guarded writes ----------------------------------------------------------------------------------------

    /**
     * Reception dispatch: the paid, open order becomes a PENDING request in {@code roomId}. Matches only when
     * execution_status is null and visit, patient and service are the order's (was INSERT diagnostic_requests).
     */
    int assignImagingRoom(UUID orderId, UUID visitId, UUID patientId, UUID serviceId, UUID roomId, String imagingType,
            UUID orderingDoctorAccountId);

    // Each write below matches only when execution_version = expectedVersion and then increments it.

    /** PENDING -> IN_PROGRESS with attempt 1 and its first-use evidence (was INSERT diagnostic_attempts). */
    int recordFirstUse(UUID orderId, long expectedVersion, UUID attemptId, UUID technicianAccountId,
            String firstUseType, UUID firstUseEventId, Instant firstUseAt, String firstUseSourceId);

    /** IN_PROGRESS -> RESULT_AVAILABLE: the attempt's study is linked. Matches only that attempt and no study yet. */
    int linkStudy(UUID orderId, long expectedVersion, UUID attemptId, String studyRef);

    /** RESULT_AVAILABLE -> REVIEWED, storing the review replay key. */
    int markReviewed(UUID orderId, long expectedVersion, String idempotencyKey, String fingerprint);

    /** PENDING -> CANCELLED (cause PATIENT_DECLINED | CLINIC_UNABLE), storing the cancel replay key. */
    int cancel(UUID orderId, long expectedVersion, String cause, String reason, String idempotencyKey,
            String fingerprint);

    /** PENDING only: moves to {@code roomId}; keeps the latest transfer replay key (decision Q3). */
    int transferRoom(UUID orderId, long expectedVersion, UUID roomId, String idempotencyKey, String fingerprint);
}
