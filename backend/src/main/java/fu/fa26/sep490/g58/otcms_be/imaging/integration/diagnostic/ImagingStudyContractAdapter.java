package fu.fa26.sep490.g58.otcms_be.imaging.integration.diagnostic;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.FindingsReleaseEvent;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ReleaseAcknowledgement;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.StudyEvidence;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.StudyVerificationStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ViewAuthorization;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.ImagingStudyPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticViewerAuthorizationService;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingStudyJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationState;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingReconciliationClassification;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingStudyRepository;
import fu.fa26.sep490.g58.otcms_be.imaging.service.ImagingReconciliationService;
import fu.fa26.sep490.g58.otcms_be.imaging.service.impl.ImagingAuditPersistenceService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implements the public imaging contract with imaging_studies as the sole workflow owner. */
@Service
@Profile("!test")
public class ImagingStudyContractAdapter implements ImagingStudyPort {
    private final ImagingStudyRepository studies;
    private final ImagingAuditPersistenceService audit;
    private final DiagnosticViewerAuthorizationService viewerAuthorization;
    private final ImagingReconciliationService reconciliation;

    public ImagingStudyContractAdapter(ImagingStudyRepository studies, ImagingAuditPersistenceService audit,
            DiagnosticViewerAuthorizationService viewerAuthorization, ImagingReconciliationService reconciliation) {
        this.studies = studies;
        this.audit = audit;
        this.viewerAuthorization = viewerAuthorization;
        this.reconciliation = reconciliation;
    }

    @Override
    @Transactional(readOnly = true)
    public StudyEvidence confirmIngestedStudy(UUID eventId, String studyRef, UUID requestId,
            UUID visitId, UUID patientId, UUID attemptId, List<String> verificationEvidence) {
        ImagingStudyJpaEntity study = studies.findByConfirmationEventId(eventId)
                .orElseThrow(() -> new ImagingOperationException(ImagingOperationOutcome.EVIDENCE_MISMATCH));
        if (!studyRef.equals(study.getStudyRef()) || !requestId.equals(study.getRequestId())
                || !visitId.equals(study.getVisitId()) || !patientId.equals(study.getPatientId())
                || !attemptId.equals(study.getAttemptId()) || !verificationEvidence.equals(study.getVerificationEvidence())
                || !"CONFIRMED_MATCH".equals(study.getVerificationStatus())
                || (study.getState() != ImagingOperationState.CONFIRMATION_PENDING
                && study.getState() != ImagingOperationState.LINKED)) {
            throw new ImagingOperationException(ImagingOperationOutcome.EVIDENCE_MISMATCH);
        }
        return new StudyEvidence(eventId, studyRef, requestId, visitId, patientId, attemptId,
                StudyVerificationStatus.CONFIRMED_MATCH, true, verificationEvidence);
    }

    @Override
    public ViewAuthorization authorizeStudyView(String studyRef, UUID actorId, String purpose) {
        AccountRole role = switch (purpose == null ? "" : purpose) {
            case "DOCTOR_REVIEW" -> AccountRole.DOCTOR;
            case "PATIENT_VIEW" -> AccountRole.PATIENT;
            case "TECHNICIAN_ASSIGNED" -> AccountRole.IMAGING_TECHNICIAN;
            default -> null;
        };
        if (role == null) return new ViewAuthorization(false, null, null);
        return viewerAuthorization.authorize(actorId, role, studyRef, purpose);
    }

    @Override
    @Transactional
    public ReleaseAcknowledgement acceptFindingsRelease(FindingsReleaseEvent event) {
        if (event == null || event.eventId() == null || event.requestId() == null || event.visitId() == null
                || event.patientId() == null || event.studyRef() == null || event.findingsVersionId() == null
                || event.reportId() == null || event.reportSha256() == null
                || !event.reportSha256().matches("[0-9a-f]{64}")) {
            throw new ImagingOperationException(ImagingOperationOutcome.EVIDENCE_MISMATCH);
        }
        String payloadHash = hashRelease(event);
        ImagingStudyJpaEntity study = studies.findByIdForUpdate(findStudyId(event.studyRef()))
                .orElseThrow(() -> new ImagingOperationException(ImagingOperationOutcome.EVIDENCE_MISMATCH));
        if (!event.requestId().equals(study.getRequestId()) || !event.visitId().equals(study.getVisitId())
                || !event.patientId().equals(study.getPatientId())) {
            reconciliation.openEvent(event.eventId(), "RELEASE", ImagingReconciliationClassification.CONFLICT,
                    payloadHash, "RELEASE_OWNER_MISMATCH");
            throw new ImagingOperationException(ImagingOperationOutcome.EVIDENCE_MISMATCH);
        }
        if (study.getReleaseEventId() != null) {
            if (event.eventId().equals(study.getReleaseEventId())
                    && payloadHash.equals(trim(study.getReleasePayloadSha256()))) {
                recordReleaseOutcome(event, "IDEMPOTENT_REPLAY", "RELEASE_EVENT_REPLAY", payloadHash);
                return new ReleaseAcknowledgement(event.eventId(), event.requestId(), payloadHash, true);
            }
            reconciliation.openEvent(event.eventId(), "RELEASE", ImagingReconciliationClassification.CONFLICT,
                    payloadHash, "RELEASE_EVENT_CONFLICT");
            throw new ImagingOperationException(ImagingOperationOutcome.IDEMPOTENCY_CONFLICT);
        }
        if (study.getState() != ImagingOperationState.LINKED) {
            reconciliation.openEvent(event.eventId(), "RELEASE", ImagingReconciliationClassification.RETRYABLE,
                    payloadHash, "RELEASE_BEFORE_LINKED");
            throw new ImagingOperationException(ImagingOperationOutcome.EVIDENCE_MISMATCH);
        }
        study.acceptFindingsRelease(event.eventId(), payloadHash, event.findingsVersionId(), event.reportId(),
                event.reportSha256(), Instant.now());
        studies.saveAndFlush(study);
        recordReleaseOutcome(event, "ACCEPTED", null, payloadHash);
        return new ReleaseAcknowledgement(event.eventId(), event.requestId(), payloadHash, true);
    }

    private UUID findStudyId(String studyRef) {
        return studies.findByStudyRef(studyRef).map(ImagingStudyJpaEntity::getId)
                .orElseThrow(() -> new ImagingOperationException(ImagingOperationOutcome.EVIDENCE_MISMATCH));
    }

    private void recordReleaseOutcome(FindingsReleaseEvent event, String outcome, String reasonCode, String payloadHash) {
        Map<String, Object> metadata = reasonCode == null
                ? Map.of("eventId", event.eventId().toString(), "payloadSha256", payloadHash)
                : Map.of("eventId", event.eventId().toString(), "reasonCode", reasonCode,
                        "payloadSha256", payloadHash);
        audit.recordService("FEAT_003", "FINDINGS_RELEASE", outcome, null, event.studyRef(),
                event.requestId(), null, event.visitId(), event.patientId(), event.eventId().toString(), metadata);
    }

    private static String hashRelease(FindingsReleaseEvent event) {
        return hash(String.join("\n", safe(event.eventId()), safe(event.requestId()), safe(event.visitId()),
                safe(event.patientId()), safe(event.studyRef()), safe(event.findingsVersionId()), safe(event.reportId()),
                safe(event.reportSha256())));
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String trim(String value) { return value == null ? null : value.trim(); }
    private static String safe(Object value) { return value == null ? "<null>" : value.toString(); }
}
