package fu.fa26.sep490.g58.otcms_be.diagnostic.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.FindingsReleaseEvent;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ReportValidationStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.StoredReport;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.StudyEvidence;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.StudyVerificationStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.UseEvidence;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.UseEvidenceType;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.VisitEvidence;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.ReportStoragePort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticFindingsReleaseView;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticFindingsView;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticReportView;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticReportUpload;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticRequestView;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@LocalPostgresTest
@Transactional
@Import({DiagnosticRequestT012PostgresIntegrationTest.PortConfiguration.class,
        DiagnosticFindingsT014PostgresIntegrationTest.ReportStorageConfiguration.class})
class DiagnosticFindingsT014PostgresIntegrationTest {
    private UUID visitId;
    private UUID patientId;
    private UUID doctorId;
    private UUID serviceId;
    private UUID roomId;
    private final String studyRef = "study-" + UUID.randomUUID();

    @Autowired DiagnosticRequestService requests;
    @Autowired DiagnosticEvidenceService evidence;
    @Autowired DiagnosticFindingsService findings;
    @Autowired JdbcTemplate jdbc;
    @Autowired DiagnosticRequestT012PostgresIntegrationTest.PortFakes ports;

    @BeforeEach
    void setUp() {
        ports.reset();
        visitId = UUID.randomUUID();
        patientId = UUID.randomUUID();
        doctorId = UUID.randomUUID();
        serviceId = UUID.randomUUID();
        roomId = UUID.randomUUID();
        ports.visits.put(visitId, new VisitEvidence(visitId, patientId, doctorId, true, true, true));
        ports.service = new fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ImagingService(
                serviceId, "MRI", true);
        ports.addRoom(roomId, "MRI", UUID.randomUUID(), 5);
    }

    @Test
    void correctionCreatesNewImmutableVersionAndOnlyChangesVisibleVersionAfterBothAcks() {
        DiagnosticRequestView request = resultAvailable();
        findings.review(doctor(), request.requestId(), "review-1", request.version(), studyRef, "corr-review");
        long reviewedVersion = request.version() + 1;

        DiagnosticFindingsView firstDraft = findings.saveDraft(doctor(), request.requestId(), "draft-1",
                reviewedVersion, "No acute finding", "corr-draft");
        DiagnosticReportView firstReport = upload(request.requestId(), reviewedVersion, "report-1");
        DiagnosticFindingsReleaseView firstRelease = findings.complete(doctor(), request.requestId(), "complete-1",
                reviewedVersion, firstDraft.version(), firstReport.reportId(), studyRef, "corr-complete-1");
        assertEquals("RELEASE_PENDING", firstRelease.releaseState());

        DiagnosticEvidenceService.DispatchSummary firstDispatch = evidence.dispatchDueOutbox(100,
                new DiagnosticEvidenceService.RetryPolicy(3, 1, 8));
        assertEquals(3, firstDispatch.delivered());
        assertEquals(1, jdbc.queryForObject("select count(*) from diagnostic_findings_versions "
                + "where request_id = ? and version_number = 1 and state = 'COMPLETED'", Integer.class,
                request.requestId()));

        DiagnosticFindingsView correction = findings.createCorrection(doctor(), request.requestId(), "correct-1",
                reviewedVersion, "Small left effusion", "Added after image re-review", "corr-correction");
        assertEquals(2, correction.version());
        assertEquals("Added after image re-review", correction.correctionReason());
        assertEquals("COMPLETED", jdbc.queryForObject("select state from diagnostic_findings_versions "
                + "where request_id = ? and version_number = 1", String.class, request.requestId()));
        assertNull(jdbc.queryForObject("select completed_at from diagnostic_findings_versions "
                + "where request_id = ? and version_number = 2", java.sql.Timestamp.class, request.requestId()));

        DiagnosticReportView correctionReport = upload(request.requestId(), reviewedVersion, "report-2");
        DiagnosticFindingsReleaseView correctionRelease = findings.complete(doctor(), request.requestId(),
                "complete-2", reviewedVersion, correction.version(), correctionReport.reportId(), studyRef,
                "corr-complete-2");
        assertEquals("RELEASE_PENDING", correctionRelease.releaseState());
        evidence.dispatchDueOutbox(100, new DiagnosticEvidenceService.RetryPolicy(3, 1, 8));

        assertEquals(2, jdbc.queryForObject("select count(*) from diagnostic_findings_versions "
                + "where request_id = ? and state = 'COMPLETED'", Integer.class, request.requestId()));
        assertNotNull(jdbc.queryForObject("select released_at from diagnostic_reports where id = ?",
                java.sql.Timestamp.class, correctionReport.reportId()));
    }

    @Test
    void ownerReleaseFailureLeavesNewFindingsAndPdfPrivateForRetry() {
        DiagnosticRequestView request = resultAvailable();
        findings.review(doctor(), request.requestId(), "review-fail", request.version(), studyRef, "corr-review-fail");
        long version = request.version() + 1;
        DiagnosticFindingsView draft = findings.saveDraft(doctor(), request.requestId(), "draft-fail", version,
                "Preliminary findings", "corr-draft-fail");
        DiagnosticReportView report = upload(request.requestId(), version, "report-fail");
        findings.complete(doctor(), request.requestId(), "complete-fail", version, draft.version(), report.reportId(),
                studyRef, "corr-complete-fail");

        ports.findingsReleaseFails = true;
        DiagnosticEvidenceService.DispatchSummary dispatch = evidence.dispatchDueOutbox(100,
                new DiagnosticEvidenceService.RetryPolicy(3, 1, 8));

        assertEquals(1, dispatch.retryScheduled());
        assertEquals("DRAFT", jdbc.queryForObject("select state from diagnostic_findings_versions where id = ?",
                String.class, draft.findingsVersionId()));
        assertNull(jdbc.queryForObject("select released_at from diagnostic_reports where id = ?",
                java.sql.Timestamp.class, report.reportId()));
        assertEquals("PENDING", jdbc.queryForObject("select status from diagnostic_outbox "
                + "where event_type = 'FINDINGS_RELEASE' and request_id = ?", String.class, request.requestId()));
    }

    private DiagnosticRequestView resultAvailable() {
        DiagnosticRequestView created = requests.create(doctor(), visitId, "create-request", serviceId,
                "MRI", "Chest", "Persistent cough", roomId, "corr-create");
        UUID attemptId = UUID.randomUUID();
        Instant now = Instant.now();
        UseEvidence use = new UseEvidence(UUID.randomUUID(), created.requestId(), attemptId,
                UseEvidenceType.ACQUISITION_STARTED, "approved-imaging-source", doctorId,
                now, now, true);
        evidence.recordFirstUse(DiagnosticActor.trustedIntegration(), use, "corr-use");
        StudyEvidence study = new StudyEvidence(UUID.randomUUID(), studyRef, created.requestId(), visitId,
                patientId, attemptId, StudyVerificationStatus.CONFIRMED_MATCH, true);
        return evidence.linkConfirmedStudy(DiagnosticActor.trustedIntegration(), study, "corr-study");
    }

    private DiagnosticReportView upload(UUID requestId, long expectedVersion, String key) {
        return findings.uploadReport(doctor(), requestId, key, expectedVersion,
                new DiagnosticReportUpload(() -> new java.io.ByteArrayInputStream(
                        "%PDF-1.7 sample".getBytes(java.nio.charset.StandardCharsets.US_ASCII)),
                        15, "application/pdf"), "corr-" + key);
    }

    private DiagnosticActor doctor() { return DiagnosticActor.user(doctorId, AccountRole.DOCTOR); }

    @TestConfiguration(proxyBeanMethods = false)
    static class ReportStorageConfiguration {
        @Bean ReportStoragePort t014ReportStoragePort() { return new FakeReportStorage(); }
    }

    static class FakeReportStorage implements ReportStoragePort {
        @Override
        public StoredReport storePrivateReport(UUID requestId, UUID visitId, String studyRef,
                int reportVersion, InputStream content, long contentLength) {
            try {
                content.readAllBytes();
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
            UUID id = UUID.randomUUID();
            return new StoredReport(id, requestId, visitId, studyRef, reportVersion,
                    "private-report/" + id, ReportValidationStatus.VALIDATED);
        }

        @Override public ReportValidationStatus validateStoredReport(UUID reportId) {
            return ReportValidationStatus.VALIDATED;
        }

        @Override public InputStream openReleasedReport(UUID reportId, UUID actorId) {
            throw new UnsupportedOperationException("T014 does not expose report viewing");
        }
    }
}
