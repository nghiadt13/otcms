package fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl;

import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort.ExecutionStatus;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort.ImagingExecution;
import fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.FindingsReleaseEvent;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticReportJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticFindingsVersionState;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticReportValidationStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticReportJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticResultEventQueryService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DiagnosticResultEventQueryServiceImpl implements DiagnosticResultEventQueryService {
    private final ClinicalServiceOrderExecutionPort executions;
    private final DiagnosticReportJpaRepository reports;

    public DiagnosticResultEventQueryServiceImpl(ClinicalServiceOrderExecutionPort executions,
            DiagnosticReportJpaRepository reports) {
        this.executions = executions;
        this.reports = reports;
    }

    @Override
    public ResultEvent requireResultReady(UUID requestId, UUID visitId, UUID eventId) {
        if (requestId == null || visitId == null || eventId == null) throw mismatch();
        ImagingExecution execution = execution(requestId, visitId);
        if ((execution.status() != ExecutionStatus.RESULT_AVAILABLE && execution.status() != ExecutionStatus.REVIEWED)
                || execution.studyRef() == null || execution.studyRef().isBlank()) throw mismatch();
        String hash = hash(String.join("\n", eventId.toString(), requestId.toString(), visitId.toString(),
                execution.patientId().toString(), "IMAGING_RESULT_READY"));
        return new ResultEvent(eventId, requestId, visitId, execution.patientId(), "IMAGING_RESULT_READY", hash);
    }

    @Override
    public ResultEvent requireFindingsRelease(FindingsReleaseEvent release) {
        if (release == null || release.eventId() == null || release.requestId() == null || release.visitId() == null
                || release.patientId() == null || release.findingsVersionId() == null
                || release.studyRef() == null || release.studyRef().isBlank() || release.studyRef().contains("\n")
                || release.studyRef().contains("\r")
                // The PDF report is optional (UC-50 AF2): both report fields are present or both absent.
                || (release.reportId() == null) != (release.reportSha256() == null)
                || (release.reportSha256() != null && !release.reportSha256().matches("[0-9a-f]{64}"))) throw mismatch();
        ImagingExecution execution = execution(release.requestId(), release.visitId());
        if (!execution.patientId().equals(release.patientId())
                || execution.status() != ExecutionStatus.REVIEWED
                || !release.studyRef().equals(execution.studyRef())) throw mismatch();
        DiagnosticReportJpaEntity result = reports.findByIdAndRequestId(release.findingsVersionId(), release.requestId())
                .orElseThrow(this::mismatch);
        if (!release.visitId().equals(result.getVisitId()) || !release.studyRef().equals(result.getStudyRef())
                || result.getVersionNumber() < 1 || !java.util.Objects.equals(release.reportId(), result.getReportId())
                || (release.reportId() != null
                    && (result.getValidationStatus() != DiagnosticReportValidationStatus.VALIDATED
                        || result.getValidatedAt() == null || !release.reportSha256().equals(result.getContentSha256())))
                || (result.getState() == DiagnosticFindingsVersionState.COMPLETED
                    && result.getReleasedAt() == null)) throw mismatch();
        String hash = hash(String.join("\n", release.eventId().toString(), release.requestId().toString(),
                release.visitId().toString(), release.patientId().toString(), release.studyRef(),
                release.findingsVersionId().toString(), java.util.Objects.toString(release.reportId(), "<null>"),
                java.util.Objects.toString(release.reportSha256(), "<null>")));
        return new ResultEvent(release.eventId(), release.requestId(), release.visitId(), release.patientId(),
                "FINDINGS_RELEASE", hash);
    }

    private ImagingExecution execution(UUID requestId, UUID visitId) {
        ImagingExecution execution = executions.find(requestId).orElseThrow(this::mismatch);
        if (!visitId.equals(execution.visitId())) throw mismatch();
        return execution;
    }

    private static String hash(String canonical) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private DiagnosticOperationException mismatch() {
        return new DiagnosticOperationException(DiagnosticOperationOutcome.EVIDENCE_MISMATCH);
    }
}
