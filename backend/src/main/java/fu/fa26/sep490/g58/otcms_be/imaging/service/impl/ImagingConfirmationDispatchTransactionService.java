package fu.fa26.sep490.g58.otcms_be.imaging.service.impl;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticEvidenceService;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingStudyJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationState;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingStudyRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Executes diagnostic, clinical, visit and imaging acknowledgements in one transaction. */
@Service
public class ImagingConfirmationDispatchTransactionService {
    private final ImagingStudyRepository studies;
    private final DiagnosticEvidenceService diagnosticEvidence;
    private final ImagingAuditPersistenceService audit;
    private final ObjectMapper objectMapper;

    public ImagingConfirmationDispatchTransactionService(ImagingStudyRepository studies,
            DiagnosticEvidenceService diagnosticEvidence, ImagingAuditPersistenceService audit,
            ObjectMapper objectMapper) {
        this.studies = studies;
        this.diagnosticEvidence = diagnosticEvidence;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void dispatch(UUID studyId) {
        ImagingStudyJpaEntity study = studies.findByIdForUpdate(studyId)
                .orElseThrow(() -> new ImagingOperationException(ImagingOperationOutcome.RECORD_NOT_FOUND));
        if (study.getState() == ImagingOperationState.LINKED) return;
        if (study.getState() != ImagingOperationState.CONFIRMATION_PENDING
                || study.getConfirmationEventId() == null || study.getStudyRef() == null) {
            throw new ImagingOperationException(ImagingOperationOutcome.STATE_CONFLICT);
        }
        List<String> evidence = objectMapper.convertValue(study.getVerificationEvidence(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        diagnosticEvidence.linkConfirmedStudy(DiagnosticActor.trustedIntegration(),
                new fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.StudyEvidence(
                        study.getConfirmationEventId(), study.getStudyRef(), study.getRequestId(),
                        study.getVisitId(), study.getPatientId(), study.getAttemptId(),
                        fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.StudyVerificationStatus.CONFIRMED_MATCH,
                        true, evidence), "imaging-confirmation-" + study.getConfirmationEventId());
        study.markAcknowledged(Instant.now());
        studies.saveAndFlush(study);
        audit.recordService("FEAT_004", "IMAGING_CONFIRMATION", "ACKNOWLEDGED", study.getId(),
                study.getStudyRef(), study.getRequestId(), study.getAttemptId(), study.getVisitId(),
                study.getPatientId(), study.getConfirmationEventId().toString(),
                Map.of("eventId", study.getConfirmationEventId().toString()));
    }
}
