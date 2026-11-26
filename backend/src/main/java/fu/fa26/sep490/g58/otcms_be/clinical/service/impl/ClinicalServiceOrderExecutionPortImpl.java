package fu.fa26.sep490.g58.otcms_be.clinical.service.impl;

import fu.fa26.sep490.g58.otcms_be.clinical.model.dto.ClinicalImagingExecutionRow;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.ClinicalServiceOrderExecutionRepository;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort;
import fu.fa26.sep490.g58.otcms_be.idempotency.model.IdempotencyScope;
import fu.fa26.sep490.g58.otcms_be.idempotency.model.dto.IdempotencyReplay;
import fu.fa26.sep490.g58.otcms_be.idempotency.service.IdempotencyService;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntSupplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class ClinicalServiceOrderExecutionPortImpl implements ClinicalServiceOrderExecutionPort {
    private static final List<IdempotencyScope> REPLAY_SCOPES = List.of(
            IdempotencyScope.ORDER_REVIEW, IdempotencyScope.ORDER_CANCEL, IdempotencyScope.ORDER_TRANSFER);
    private final ClinicalServiceOrderExecutionRepository repository;
    private final IdempotencyService store;

    public ClinicalServiceOrderExecutionPortImpl(ClinicalServiceOrderExecutionRepository repository,
            IdempotencyService store) {
        this.repository = repository;
        this.store = store;
    }

    @Override
    public Optional<ImagingExecution> find(UUID id) {
        return repository.find(id).map(row -> executions(List.of(row)).get(0));
    }

    @Override
    public Optional<ImagingExecution> findForUpdate(UUID id) {
        return repository.findForUpdate(id).map(row -> executions(List.of(row)).get(0));
    }

    @Override
    public Optional<ImagingExecution> findByStudyRef(String ref) {
        return repository.findByStudyRef(ref).map(row -> executions(List.of(row)).get(0));
    }

    @Override
    public Optional<ImagingExecution> findByFirstUseEventId(UUID eventId) {
        return repository.findByFirstUseEventId(eventId).map(row -> executions(List.of(row)).get(0));
    }

    @Override
    public List<ImagingExecution> findAllByOrderIds(Collection<UUID> ids) {
        return executions(repository.findAllByOrderIds(ids));
    }

    @Override
    public List<ImagingExecution> findAllByVisit(UUID id) {
        return executions(repository.findAllByVisit(id));
    }

    @Override
    public List<ImagingExecution> findAllByPatient(UUID id) {
        return executions(repository.findAllByPatient(id));
    }
    @Override public Page<ImagingExecution> page(UUID visitId, UUID patientId, UUID doctorId, UUID roomId,
            Collection<ExecutionStatus> statuses, boolean oldestFirst, Pageable pageable) {
        Page<ClinicalImagingExecutionRow> rows = repository.page(visitId, patientId, doctorId, roomId,
                statuses == null ? null : statuses.stream().map(Enum::name).toList(), oldestFirst, pageable);
        return new PageImpl<>(executions(rows.getContent()), rows.getPageable(), rows.getTotalElements());
    }
    @Override
    public List<ImagingExecution> findPendingCreatedBefore(Instant before) {
        return executions(repository.findPendingCreatedBefore(before));
    }
    @Override public int assignImagingRoom(UUID orderId, UUID visitId, UUID patientId, UUID serviceId, UUID roomId,
            String imagingType, UUID doctorId) {
        return repository.assignImagingRoom(orderId, visitId, patientId, serviceId, roomId, imagingType, doctorId);
    }
    @Override public int recordFirstUse(UUID orderId, long version, UUID attemptId, UUID technicianId,
            String type, UUID eventId, Instant at, String sourceId) {
        return repository.recordFirstUse(orderId, version, attemptId, technicianId, type, eventId, at, sourceId);
    }
    @Override public int linkStudy(UUID orderId, long version, UUID attemptId, String studyRef) {
        return repository.linkStudy(orderId, version, attemptId, studyRef);
    }
    @Override public int markReviewed(UUID orderId, long version, String key, String fingerprint) {
        return keyed(IdempotencyScope.ORDER_REVIEW, orderId, key, fingerprint,
                () -> repository.markReviewed(orderId, version));
    }
    @Override public int cancel(UUID orderId, long version, String cause, String reason, String key, String fingerprint) {
        return keyed(IdempotencyScope.ORDER_CANCEL, orderId, key, fingerprint,
                () -> repository.cancel(orderId, version, cause, reason));
    }
    @Override public int transferRoom(UUID orderId, long version, UUID roomId, String key, String fingerprint) {
        return keyed(IdempotencyScope.ORDER_TRANSFER, orderId, key, fingerprint,
                () -> repository.transferRoom(orderId, version, roomId));
    }

    /**
     * Guarded write plus its replay key in idempotency_records (owner = resource = the order). A key already used for
     * this order is not written again: the caller re-reads the order and decides, as for any lost guarded write.
     */
    private int keyed(IdempotencyScope scope, UUID orderId, String key, String fingerprint, IntSupplier write) {
        if (store.find(scope, orderId, key).isPresent()) return 0;
        int changed = write.getAsInt();
        if (changed == 1 && !store.record(scope, orderId, key, fingerprint, orderId)) {
            throw new IllegalStateException(scope + " key was recorded concurrently for order " + orderId);
        }
        return changed;
    }

    private List<ImagingExecution> executions(List<ClinicalImagingExecutionRow> rows) {
        if (rows.isEmpty()) return List.of();
        List<IdempotencyReplay> replayRecords = Optional.ofNullable(store.findLatest(
                rows.stream().map(ClinicalImagingExecutionRow::orderId).distinct().toList(), REPLAY_SCOPES))
                .orElseGet(List::of);
        Map<IdempotencyScope, Map<UUID, IdempotencyReplay>> replayByScopeAndOwner =
                new EnumMap<>(IdempotencyScope.class);
        for (IdempotencyReplay replay : replayRecords) {
            if (replay.scope() == null || replay.ownerId() == null) continue;
            replayByScopeAndOwner.computeIfAbsent(replay.scope(), ignored -> new HashMap<>())
                    .put(replay.ownerId(), replay);
        }
        return rows.stream().map(row -> execution(row, replayByScopeAndOwner)).toList();
    }

    private ImagingExecution execution(ClinicalImagingExecutionRow row,
            Map<IdempotencyScope, Map<UUID, IdempotencyReplay>> replayRecords) {
        IdempotencyReplay review = replayRecords.getOrDefault(IdempotencyScope.ORDER_REVIEW, Map.of())
                .get(row.orderId());
        IdempotencyReplay cancel = replayRecords.getOrDefault(IdempotencyScope.ORDER_CANCEL, Map.of())
                .get(row.orderId());
        IdempotencyReplay transfer = replayRecords.getOrDefault(IdempotencyScope.ORDER_TRANSFER, Map.of())
                .get(row.orderId());
        return new ImagingExecution(row.orderId(), row.visitId(), row.patientId(), row.orderingDoctorAccountId(),
                row.serviceId(), row.roomId(), row.imagingType(), row.bodyArea(), row.clinicalReason(),
                row.dicomPatientId(), row.dicomPatientIssuer(), row.dicomAccessionNumber(),
                row.dicomAccessionLocalNamespace(),
                ExecutionStatus.valueOf(row.status()), row.cancellationCause(), row.cancellationReason(),
                row.attemptId(), row.technicianAccountId(), row.firstUseType(), row.firstUseEventId(),
                row.firstUseAt(), row.firstUseSourceId(), row.studyRef(),
                review == null ? null : review.idempotencyKey(),
                review == null ? null : review.requestFingerprint(),
                cancel == null ? null : cancel.idempotencyKey(),
                cancel == null ? null : cancel.requestFingerprint(),
                transfer == null ? null : transfer.idempotencyKey(),
                transfer == null ? null : transfer.requestFingerprint(), row.executionVersion(),
                row.createdAt(), row.updatedAt());
    }
}
