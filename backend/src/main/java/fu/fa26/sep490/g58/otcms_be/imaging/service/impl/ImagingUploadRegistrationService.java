package fu.fa26.sep490.g58.otcms_be.imaging.service.impl;

import fu.fa26.sep490.g58.otcms_be.common.idempotency.IdempotencyReplay;
import fu.fa26.sep490.g58.otcms_be.common.idempotency.IdempotencyScope;
import fu.fa26.sep490.g58.otcms_be.common.idempotency.IdempotencyStore;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingAssignedWork;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingOperationRecord;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingStudyJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationState;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationType;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingVerificationStatus;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingStudyRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Atomically reserves an imaging_studies row and its scoped upload key (idempotency_records) in one transaction. */
@Service
public class ImagingUploadRegistrationService {
    private final ImagingStudyRepository studies;
    private final IdempotencyStore idempotency;

    public ImagingUploadRegistrationService(ImagingStudyRepository studies, IdempotencyStore idempotency) {
        this.studies = studies;
        this.idempotency = idempotency;
    }

    @Transactional
    public Registration register(UUID actorId, UUID requestId, UUID attemptId, String key,
            String sha256, long bodySize, ImagingAssignedWork work) {
        Instant now = Instant.now();
        UUID studyId = UUID.randomUUID();
        String scopedKey = uploadKey(requestId, attemptId, key);
        // Claim the key before inserting: a duplicate, or a concurrent winner, is replayed from the stored row.
        if (!idempotency.record(IdempotencyScope.IMAGING_UPLOAD, actorId, scopedKey, sha256, studyId, now)) {
            return replay(actorId, scopedKey, sha256);
        }
        ImagingOperationRecord operation = new ImagingOperationRecord(studyId, null, requestId,
                attemptId, work.visitId(), work.patientId(), actorId, ImagingOperationType.UPLOAD,
                ImagingOperationState.PROCESSING, sha256, bodySize, null, null, 0, 0,
                ImagingVerificationStatus.NOT_EVALUATED, List.of(), null, null, 0, now, now);
        ImagingStudyJpaEntity inserted = studies.saveAndFlush(new ImagingStudyJpaEntity(operation));
        return new Registration(inserted.toModel(), true);
    }

    /** Upload scope stored as the idempotency key: service order, attempt and the client's Idempotency-Key. */
    static String uploadKey(UUID requestId, UUID attemptId, String key) {
        return requestId + ":" + attemptId + ":" + key;
    }

    private Registration replay(UUID actorId, String scopedKey, String sha256) {
        IdempotencyReplay existing = idempotency.find(IdempotencyScope.IMAGING_UPLOAD, actorId, scopedKey)
                .orElseThrow(() -> new ImagingOperationException(ImagingOperationOutcome.IDEMPOTENCY_CONFLICT));
        if (!existing.matches(sha256)) {
            throw new ImagingOperationException(ImagingOperationOutcome.IDEMPOTENCY_CONFLICT);
        }
        ImagingStudyJpaEntity study = studies.findById(existing.resourceId())
                .orElseThrow(() -> new ImagingOperationException(ImagingOperationOutcome.RECORD_NOT_FOUND));
        return new Registration(study.toModel(), false);
    }

    public record Registration(ImagingOperationRecord operation, boolean created) { }
}
