package fu.fa26.sep490.g58.otcms_be.imaging.integration.diagnostic;

import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.FindingsReleaseEvent;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ReleaseAcknowledgement;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.StudyEvidence;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.StudyVerificationStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ViewAuthorization;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.ImagingStudyPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticViewerAuthorizationService;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingReleaseInboxJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingIngestOperationRepository;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingConfirmationOutboxRepository;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingReleaseInboxRepository;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingStudyRepository;
import fu.fa26.sep490.g58.otcms_be.imaging.service.impl.ImagingAuditPersistenceService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

/** Implements the FEAT-003 public imaging port using FEAT-004-owned persistence only. */
@Service
@Profile("!test")
public class ImagingStudyContractAdapter implements ImagingStudyPort {
    private final ImagingStudyRepository studies;
    private final ImagingIngestOperationRepository operations;
    private final ImagingReleaseInboxRepository releases;
    private final ImagingAuditPersistenceService audit;
    private final ImagingConfirmationOutboxRepository confirmations;
    private final DiagnosticViewerAuthorizationService viewerAuthorization;

    public ImagingStudyContractAdapter(ImagingStudyRepository studies,
            ImagingIngestOperationRepository operations, ImagingReleaseInboxRepository releases,
            ImagingAuditPersistenceService audit, ImagingConfirmationOutboxRepository confirmations,
            DiagnosticViewerAuthorizationService viewerAuthorization) {
        this.studies = studies;
        this.operations = operations;
        this.releases = releases;
        this.audit = audit;
        this.confirmations = confirmations;
        this.viewerAuthorization = viewerAuthorization;
    }

    @Override
    @Transactional(readOnly = true)
    public StudyEvidence confirmIngestedStudy(UUID eventId, String studyRef, UUID requestId,
            UUID visitId, UUID patientId, UUID attemptId, List<String> verificationEvidence) {
        var study = studies.findById(studyRef)
                .orElseThrow(() -> new ImagingOperationException(ImagingOperationOutcome.EVIDENCE_MISMATCH));
        var outbox = confirmations.findByEventId(eventId)
                .orElseThrow(() -> new ImagingOperationException(ImagingOperationOutcome.EVIDENCE_MISMATCH));
        if (!outbox.getStudyRef().equals(studyRef) || !outbox.getRequestId().equals(requestId)
                || !outbox.getVisitId().equals(visitId) || !outbox.getPatientId().equals(patientId)
                || !outbox.getAttemptId().equals(attemptId)) {
            throw new ImagingOperationException(ImagingOperationOutcome.EVIDENCE_MISMATCH);
        }
        if (!study.getRequestId().equals(requestId) || !study.getVisitId().equals(visitId)
                || !study.getPatientId().equals(patientId) || !study.getAttemptId().equals(attemptId)
                || !"CONFIRMED_MATCH".equals(study.getVerificationStatus())) {
            throw new ImagingOperationException(ImagingOperationOutcome.EVIDENCE_MISMATCH);
        }
        var operation = operations.findById(study.getIngestOperationId())
                .orElseThrow(() -> new ImagingOperationException(ImagingOperationOutcome.RECONCILIATION_REQUIRED));
        var record = operation.toModel();
        if (!record.verificationEvidence().equals(verificationEvidence)
                || record.state() == fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationState.QUARANTINED) {
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
        var study = studies.findById(event.studyRef())
                .orElseThrow(() -> new ImagingOperationException(ImagingOperationOutcome.EVIDENCE_MISMATCH));
        if (!study.getRequestId().equals(event.requestId()) || !study.getVisitId().equals(event.visitId())
                || !study.getPatientId().equals(event.patientId())) {
            throw new ImagingOperationException(ImagingOperationOutcome.EVIDENCE_MISMATCH);
        }
        var ingest = operations.findById(study.getIngestOperationId())
                .orElseThrow(() -> new ImagingOperationException(ImagingOperationOutcome.RECONCILIATION_REQUIRED));
        if (ingest.getState() != fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationState.LINKED) {
            throw new ImagingOperationException(ImagingOperationOutcome.EVIDENCE_MISMATCH);
        }
        String payloadHash = hash(String.join("\n", event.eventId().toString(), event.requestId().toString(),
                event.visitId().toString(), event.patientId().toString(), event.studyRef(), event.findingsVersionId().toString(),
                event.reportId().toString(), event.reportSha256()));
        var existing = releases.findByEventId(event.eventId());
        if (existing.isPresent()) {
            if (!existing.get().getPayloadSha256().equals(payloadHash)) {
                throw new ImagingOperationException(ImagingOperationOutcome.IDEMPOTENCY_CONFLICT);
            }
            return new ReleaseAcknowledgement(event.eventId(), event.requestId(), payloadHash, true);
        }
        audit.recordService("FEAT_003", "FINDINGS_RELEASE", "ACCEPTED", null, event.studyRef(),
                event.requestId(), null, event.visitId(), event.patientId(), event.eventId().toString(),
                java.util.Map.of("eventId", event.eventId().toString(), "payloadSha256", payloadHash));
        releases.saveAndFlush(new ImagingReleaseInboxJpaEntity(UUID.randomUUID(), event.eventId(),
                event.requestId(), event.visitId(), event.patientId(), event.studyRef(),
                event.findingsVersionId(), event.reportId(), event.reportSha256(), payloadHash,
                "ACCEPTED", Instant.now()));
        return new ReleaseAcknowledgement(event.eventId(), event.requestId(), payloadHash, true);
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
