package fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl;

import fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.FindingsReleaseEvent;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticRequestJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticOutboxJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticResultEventQueryService;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DiagnosticResultEventQueryServiceImpl implements DiagnosticResultEventQueryService {
    private final DiagnosticRequestJpaRepository requests;
    private final DiagnosticAttemptJpaRepository attempts;
    private final DiagnosticOutboxJpaRepository outbox;
    private final DiagnosticFindingsVersionJpaRepository findings;
    private final DiagnosticReportJpaRepository reports;

    public DiagnosticResultEventQueryServiceImpl(DiagnosticRequestJpaRepository requests,
            DiagnosticAttemptJpaRepository attempts, DiagnosticOutboxJpaRepository outbox,
            DiagnosticFindingsVersionJpaRepository findings, DiagnosticReportJpaRepository reports) {
        this.requests = requests; this.attempts = attempts; this.outbox = outbox;
        this.findings = findings; this.reports = reports;
    }

    @Override public ResultEvent requireResultReady(UUID requestId, UUID visitId, UUID eventId) {
        var event = event(requestId, visitId, eventId, "IMAGING_RESULT_READY");
        var request = request(requestId, visitId);
        currentStudy(request);
        if (!eventId.toString().equals(event.getPayload().get("eventId"))
                || !requestId.toString().equals(event.getPayload().get("requestId"))
                || !visitId.toString().equals(event.getPayload().get("visitId"))) throw mismatch();
        String hash = hash(String.join("\n", eventId.toString(), requestId.toString(), visitId.toString(),
                request.getPatientId().toString(), "IMAGING_RESULT_READY"));
        return new ResultEvent(eventId, requestId, visitId, request.getPatientId(), "IMAGING_RESULT_READY", hash);
    }

    @Override public ResultEvent requireFindingsRelease(FindingsReleaseEvent release) {
        if (release == null || release.patientId() == null || release.findingsVersionId() == null
                || release.reportId() == null || release.studyRef() == null || release.studyRef().isBlank()
                || release.studyRef().contains("\n") || release.studyRef().contains("\r")
                || release.reportSha256() == null || !release.reportSha256().matches("[0-9a-f]{64}")) throw mismatch();
        var event = event(release.requestId(), release.visitId(), release.eventId(), "FINDINGS_RELEASE");
        var request = request(release.requestId(), release.visitId());
        if (!request.getPatientId().equals(release.patientId())
                || !currentStudy(request).equals(release.studyRef()) || request.getStatus() != DiagnosticRequestStatus.REVIEWED) throw mismatch();
        Map<String, Object> expected = Map.of("eventId", release.eventId().toString(), "requestId", release.requestId().toString(),
                "visitId", release.visitId().toString(), "patientId", release.patientId().toString(), "studyRef", release.studyRef(),
                "findingsVersionId", release.findingsVersionId().toString(), "reportId", release.reportId().toString(),
                "reportSha256", release.reportSha256());
        if (!expected.equals(event.getPayload())) throw mismatch();
        var version = findings.findByIdAndRequestId(release.findingsVersionId(), release.requestId()).orElseThrow(this::mismatch);
        var report = reports.findByIdAndRequestId(release.reportId(), release.requestId()).orElseThrow(this::mismatch);
        if (!release.visitId().equals(version.getVisitId()) || !release.studyRef().equals(version.getStudyRef())
                || !release.visitId().equals(report.getVisitId()) || !release.studyRef().equals(report.getStudyRef())
                || version.getVersionNumber() != report.getReportVersion()
                || report.getValidationStatus() != DiagnosticReportValidationStatus.VALIDATED
                || report.getValidatedAt() == null || !release.reportSha256().equals(report.getContentSha256())
                || (version.getState() == DiagnosticFindingsVersionState.COMPLETED
                    && !release.reportId().equals(version.getRequiredPdfReportId()))) throw mismatch();
        String hash = hash(String.join("\n", release.eventId().toString(), release.requestId().toString(),
                release.visitId().toString(), release.patientId().toString(), release.studyRef(),
                release.findingsVersionId().toString(), release.reportId().toString(), release.reportSha256()));
        return new ResultEvent(release.eventId(), release.requestId(), release.visitId(), release.patientId(), "FINDINGS_RELEASE", hash);
    }

    private DiagnosticOutboxJpaEntity event(UUID request, UUID visit, UUID id, String type) {
        if (request == null || visit == null || id == null) throw mismatch();
        var event = outbox.findByEventId(id).orElseThrow(this::mismatch);
        if (!request.equals(event.getRequestId()) || !visit.equals(event.getVisitId()) || !type.equals(event.getEventType())) throw mismatch();
        return event;
    }
    private DiagnosticRequestJpaEntity request(UUID id, UUID visit) {
        var request = requests.findById(id).orElseThrow(this::mismatch);
        if (!visit.equals(request.getVisitId()) || request.getPatientId() == null
                || !List.of(DiagnosticRequestStatus.RESULT_AVAILABLE, DiagnosticRequestStatus.REVIEWED).contains(request.getStatus())) throw mismatch();
        return request;
    }
    private String currentStudy(DiagnosticRequestJpaEntity request) {
        if (request.getCurrentAttemptId() == null) throw mismatch();
        var attempt = attempts.findByRequestIdAndId(request.getId(), request.getCurrentAttemptId()).orElseThrow(this::mismatch);
        if (attempt.getStudyRef() == null || attempt.getStudyRef().isBlank()) throw mismatch();
        return attempt.getStudyRef();
    }
    private static String hash(String canonical) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    private DiagnosticOperationException mismatch() { return new DiagnosticOperationException(DiagnosticOperationOutcome.EVIDENCE_MISMATCH); }
}
