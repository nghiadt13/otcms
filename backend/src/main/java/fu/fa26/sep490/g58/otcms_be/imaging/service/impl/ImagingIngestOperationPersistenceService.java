package fu.fa26.sep490.g58.otcms_be.imaging.service.impl;

import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingOperationRecord;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingIngestOperationJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingDicomStudyValidation;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationState;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingIngestOperationRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Maps domain projections to FEAT-004 JPA entities and owns operation persistence rules. */
@Service
public class ImagingIngestOperationPersistenceService {
    private final ImagingIngestOperationRepository operations;

    public ImagingIngestOperationPersistenceService(ImagingIngestOperationRepository operations) {
        this.operations = operations;
    }

    @Transactional(readOnly = true)
    public Optional<ImagingOperationRecord> findById(UUID id) {
        return operations.findById(id).map(ImagingIngestOperationJpaEntity::toModel);
    }

    @Transactional
    public void insert(ImagingOperationRecord operation) {
        operations.saveAndFlush(new ImagingIngestOperationJpaEntity(operation));
    }

    @Transactional
    public void recordValidation(UUID operationId, ImagingDicomStudyValidation validation) {
        ImagingIngestOperationJpaEntity operation = operations.findById(operationId)
                .orElseThrow(() -> new ImagingOperationException(ImagingOperationOutcome.RECORD_NOT_FOUND));
        operation.applyValidation(validation);
        operations.saveAndFlush(operation);
    }

    @Transactional
    public void recordStudyReferences(UUID operationId, String studyRef, String archiveStudyRef) {
        ImagingIngestOperationJpaEntity operation = operations.findById(operationId)
                .orElseThrow(() -> new ImagingOperationException(ImagingOperationOutcome.RECORD_NOT_FOUND));
        operation.setStudyRef(studyRef);
        operation.setArchiveStudyRef(archiveStudyRef);
        operations.saveAndFlush(operation);
    }

    @Transactional
    public void transition(UUID operationId, long expectedVersion, ImagingOperationState next,
            String safeOutcomeCode, Instant updatedAt) {
        ImagingIngestOperationJpaEntity current = operations.findById(operationId)
                .orElseThrow(() -> new ImagingOperationException(ImagingOperationOutcome.RECORD_NOT_FOUND));
        if (!current.getState().canTransitionTo(next)) {
            throw new ImagingOperationException(ImagingOperationOutcome.STATE_CONFLICT);
        }
        int changed = operations.transitionStateIfVersionMatches(operationId, expectedVersion,
                current.getState(), next, safeOutcomeCode, updatedAt);
        if (changed != 1) throw new ImagingOperationException(ImagingOperationOutcome.VERSION_CONFLICT);
    }
}
