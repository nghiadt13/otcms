package fu.fa26.sep490.g58.otcms_be.clinical.service.impl;

import fu.fa26.sep490.g58.otcms_be.clinical.repository.ClinicalServiceOrderExecutionRepository;
import fu.fa26.sep490.g58.otcms_be.clinical.model.dto.ClinicalImagingExecutionRow;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class ClinicalServiceOrderExecutionPortImpl implements ClinicalServiceOrderExecutionPort {
    private final ClinicalServiceOrderExecutionRepository repository;

    public ClinicalServiceOrderExecutionPortImpl(ClinicalServiceOrderExecutionRepository repository) {
        this.repository = repository;
    }

    @Override public Optional<ImagingExecution> find(UUID id) { return repository.find(id).map(this::execution); }
    @Override public Optional<ImagingExecution> findForUpdate(UUID id) { return repository.findForUpdate(id).map(this::execution); }
    @Override public Optional<ImagingExecution> findByStudyRef(String ref) { return repository.findByStudyRef(ref).map(this::execution); }
    @Override public Optional<ImagingExecution> findByFirstUseEventId(UUID eventId) { return repository.findByFirstUseEventId(eventId).map(this::execution); }
    @Override public List<ImagingExecution> findAllByOrderIds(Collection<UUID> ids) { return repository.findAllByOrderIds(ids).stream().map(this::execution).toList(); }
    @Override public List<ImagingExecution> findAllByVisit(UUID id) { return repository.findAllByVisit(id).stream().map(this::execution).toList(); }
    @Override public List<ImagingExecution> findAllByPatient(UUID id) { return repository.findAllByPatient(id).stream().map(this::execution).toList(); }
    @Override public Page<ImagingExecution> page(UUID visitId, UUID patientId, UUID doctorId, UUID roomId,
            Collection<ExecutionStatus> statuses, boolean oldestFirst, Pageable pageable) {
        return repository.page(visitId, patientId, doctorId, roomId,
                statuses == null ? null : statuses.stream().map(Enum::name).toList(), oldestFirst, pageable).map(this::execution);
    }
    @Override public List<ImagingExecution> findPendingCreatedBefore(Instant before) { return repository.findPendingCreatedBefore(before).stream().map(this::execution).toList(); }
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
        return repository.markReviewed(orderId, version, key, fingerprint);
    }
    @Override public int cancel(UUID orderId, long version, String cause, String reason, String key, String fingerprint) {
        return repository.cancel(orderId, version, cause, reason, key, fingerprint);
    }
    @Override public int transferRoom(UUID orderId, long version, UUID roomId, String key, String fingerprint) {
        return repository.transferRoom(orderId, version, roomId, key, fingerprint);
    }

    private ImagingExecution execution(ClinicalImagingExecutionRow row) {
        return new ImagingExecution(row.orderId(), row.visitId(), row.patientId(), row.orderingDoctorAccountId(),
                row.serviceId(), row.roomId(), row.imagingType(), row.bodyArea(), row.clinicalReason(),
                ExecutionStatus.valueOf(row.status()), row.cancellationCause(), row.cancellationReason(), row.attemptId(),
                row.technicianAccountId(), row.firstUseType(), row.firstUseEventId(), row.firstUseAt(), row.firstUseSourceId(),
                row.studyRef(), row.reviewIdempotencyKey(), row.reviewFingerprint(), row.cancelIdempotencyKey(),
                row.cancelFingerprint(), row.transferIdempotencyKey(), row.transferFingerprint(), row.executionVersion(),
                row.createdAt(), row.updatedAt());
    }
}
