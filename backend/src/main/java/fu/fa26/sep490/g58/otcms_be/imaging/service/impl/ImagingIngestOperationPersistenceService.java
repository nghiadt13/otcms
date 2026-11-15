package fu.fa26.sep490.g58.otcms_be.imaging.service.impl;

import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingDicomStudyValidation;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingOperationRecord;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingStudyJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationState;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingStudyRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persists upload workflow state on its owning imaging_studies row. */
@Service
public class ImagingIngestOperationPersistenceService {
    private final ImagingStudyRepository studies;

    public ImagingIngestOperationPersistenceService(ImagingStudyRepository studies) {
        this.studies = studies;
    }

    @Transactional(readOnly = true)
    public Optional<ImagingOperationRecord> findById(UUID id) {
        return studies.findById(id).map(ImagingStudyJpaEntity::toModel);
    }

    @Transactional
    public void insert(ImagingOperationRecord operation) {
        studies.saveAndFlush(new ImagingStudyJpaEntity(operation, operation.id().toString()));
    }

    @Transactional
    public void recordValidation(UUID studyId, ImagingDicomStudyValidation validation) {
        ImagingStudyJpaEntity study = studies.findById(studyId)
                .orElseThrow(() -> new ImagingOperationException(ImagingOperationOutcome.RECORD_NOT_FOUND));
        study.applyValidation(validation);
        studies.saveAndFlush(study);
    }

    @Transactional
    public void recordStudyReferences(UUID studyId, String studyRef, String archiveStudyRef) {
        ImagingStudyJpaEntity study = studies.findById(studyId)
                .orElseThrow(() -> new ImagingOperationException(ImagingOperationOutcome.RECORD_NOT_FOUND));
        study.setStudyRef(studyRef);
        study.setArchiveStudyRef(archiveStudyRef);
        studies.saveAndFlush(study);
    }

    @Transactional
    public void transition(UUID studyId, long expectedVersion, ImagingOperationState next,
            String safeOutcomeCode, Instant updatedAt) {
        ImagingStudyJpaEntity current = studies.findById(studyId)
                .orElseThrow(() -> new ImagingOperationException(ImagingOperationOutcome.RECORD_NOT_FOUND));
        if (!current.getState().canTransitionTo(next)) {
            throw new ImagingOperationException(ImagingOperationOutcome.STATE_CONFLICT);
        }
        int changed = studies.transitionStateIfVersionMatches(studyId, expectedVersion,
                current.getState(), next, safeOutcomeCode, updatedAt);
        if (changed != 1) throw new ImagingOperationException(ImagingOperationOutcome.VERSION_CONFLICT);
    }
}
