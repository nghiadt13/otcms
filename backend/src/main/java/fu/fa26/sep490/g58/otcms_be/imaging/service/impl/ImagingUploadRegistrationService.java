package fu.fa26.sep490.g58.otcms_be.imaging.service.impl;

import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingIdempotencyRecord;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingOperationRecord;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingAssignedWork;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingIdempotencyRecordJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingIngestOperationJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationState;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationType;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingVerificationStatus;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingIdempotencyRepository;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingIngestOperationRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Atomically reserves an upload operation and its scoped content idempotency fingerprint. */
@Service
public class ImagingUploadRegistrationService {
    private final ImagingIngestOperationRepository operations;
    private final ImagingIdempotencyRepository idempotency;

    public ImagingUploadRegistrationService(ImagingIngestOperationRepository operations,
            ImagingIdempotencyRepository idempotency) {
        this.operations = operations;
        this.idempotency = idempotency;
    }

    @Transactional
    public Registration register(UUID actorId, UUID requestId, UUID attemptId, String key,
            String sha256, long bodySize, ImagingAssignedWork work) {
        var previous = idempotency.findByActorAccountIdAndRequestIdAndAttemptIdAndOperationTypeAndIdempotencyKey(
                actorId, requestId, attemptId, ImagingOperationType.UPLOAD, key);
        if (previous.isPresent()) {
            var entry = previous.get();
            if (!entry.toModel().payloadSha256().equals(sha256)) {
                throw new ImagingOperationException(ImagingOperationOutcome.IDEMPOTENCY_CONFLICT);
            }
            var current = operations.findById(entry.toModel().targetOperationId())
                    .orElseThrow(() -> new ImagingOperationException(ImagingOperationOutcome.RECONCILIATION_REQUIRED));
            return new Registration(current.toModel(), false);
        }

        Instant now = Instant.now();
        UUID operationId = UUID.randomUUID();
        ImagingOperationRecord operation = new ImagingOperationRecord(operationId, null, requestId,
                attemptId, work.visitId(), work.patientId(), actorId, ImagingOperationType.UPLOAD,
                ImagingOperationState.PROCESSING, sha256, bodySize, null, null, 0, 0,
                ImagingVerificationStatus.NOT_EVALUATED, List.of(), null, null, 0, now, now);
        try {
            operations.saveAndFlush(new ImagingIngestOperationJpaEntity(operation));
            idempotency.saveAndFlush(new ImagingIdempotencyRecordJpaEntity(new ImagingIdempotencyRecord(
                    UUID.randomUUID(), actorId, requestId, attemptId, ImagingOperationType.UPLOAD,
                    key, sha256, operationId, null, now)));
        } catch (DataIntegrityViolationException exception) {
            throw new ImagingOperationException(ImagingOperationOutcome.IDEMPOTENCY_CONFLICT);
        }
        return new Registration(operation, true);
    }

    public record Registration(ImagingOperationRecord operation, boolean created) { }
}
