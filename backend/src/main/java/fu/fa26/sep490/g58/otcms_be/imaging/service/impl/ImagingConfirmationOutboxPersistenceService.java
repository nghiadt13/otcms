package fu.fa26.sep490.g58.otcms_be.imaging.service.impl;

import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingStudyJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingStudyRepository;
import fu.fa26.sep490.g58.otcms_be.imaging.service.ImagingReconciliationService;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Stores failed delivery bookkeeping in an isolated transaction after the link transaction rolls back. */
@Service
public class ImagingConfirmationOutboxPersistenceService {
    private final ImagingStudyRepository studies;
    private final ImagingReconciliationService reconciliation;
    private final ImagingAuditPersistenceService audit;

    public ImagingConfirmationOutboxPersistenceService(ImagingStudyRepository studies,
            ImagingReconciliationService reconciliation, ImagingAuditPersistenceService audit) {
        this.studies = studies;
        this.reconciliation = reconciliation;
        this.audit = audit;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retry(UUID studyId, Instant nextAt, String safeCode) {
        ImagingStudyJpaEntity study = locked(studyId);
        study.recordConfirmationRetry(nextAt, safeCode, Instant.now());
        studies.saveAndFlush(study);
        record(study, "RETRY_SCHEDULED", safeCode);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reconcile(UUID studyId, String safeCode) {
        ImagingStudyJpaEntity study = locked(studyId);
        study.recordConfirmationReconciliation(safeCode, Instant.now());
        studies.saveAndFlush(study);
        reconciliation.openOperation(studyId, "CONFIRMATION", safeCode);
        record(study, "RECONCILIATION_REQUIRED", safeCode);
    }

    private ImagingStudyJpaEntity locked(UUID id) {
        return studies.findByIdForUpdate(id)
                .orElseThrow(() -> new ImagingOperationException(ImagingOperationOutcome.RECORD_NOT_FOUND));
    }

    private void record(ImagingStudyJpaEntity study, String outcome, String reasonCode) {
        audit.recordService("FEAT_004", "IMAGING_CONFIRMATION", outcome, study.getId(), study.getStudyRef(),
                study.getRequestId(), study.getAttemptId(), study.getVisitId(), study.getPatientId(),
                study.getConfirmationEventId().toString(), Map.of("eventId", study.getConfirmationEventId().toString(),
                        "reasonCode", reasonCode));
    }
}
