package fu.fa26.sep490.g58.otcms_be.diagnostic.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticRequestStatus;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@LocalPostgresTest
@Import({DiagnosticRequestT012PostgresIntegrationTest.PortConfiguration.class,
        DiagnosticFindingsT014PostgresIntegrationTest.ReportStorageConfiguration.class})
class DiagnosticFindingsT014PostgresIntegrationTest {
    protected UUID visitId;
    protected UUID patientId;
    protected UUID doctorId;
    protected UUID serviceId;
    protected UUID roomId;
    protected UUID serviceOrderId;
    protected final String studyRef = "study-" + UUID.randomUUID();

    @Autowired protected DiagnosticRequestService requests;
    @Autowired protected DiagnosticImagingRequestRegistration registration;
    @Autowired protected org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired protected DiagnosticEvidenceService evidence;
    @Autowired protected DiagnosticFindingsService findings;
    @Autowired protected DiagnosticVisitCompletionEvidenceService completion;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected DiagnosticRequestT012PostgresIntegrationTest.PortFakes ports;

    @BeforeEach
    protected void setUp() {
        ports.reset();
        visitId = UUID.randomUUID();
        patientId = UUID.randomUUID();
        doctorId = UUID.randomUUID();
        serviceId = UUID.randomUUID();
        roomId = UUID.randomUUID();
        serviceOrderId = UUID.randomUUID();
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
        assertEquals("RELEASED", firstRelease.releaseState());
        // Preserve the registration link: JDBC reassignment would stale the managed JPA request.
        UUID clinicalOrder = serviceOrderId;
        org.junit.jupiter.api.Assertions.assertTrue(completion.getCompletionEvidence(visitId, patientId, java.util.List.of(clinicalOrder)).allComplete());
        org.junit.jupiter.api.Assertions.assertFalse(completion.getCompletionEvidence(UUID.randomUUID(), patientId, java.util.List.of(clinicalOrder)).allComplete());
        assertEquals(1, jdbc.queryForObject("select count(*) from diagnostic_reports "
                + "where service_order_id = ? and version_number = 1 and state = 'COMPLETED'", Integer.class,
                request.requestId()));

        var originalFindings = jdbc.queryForMap("select * from diagnostic_reports where id = ?",
                firstDraft.findingsVersionId());
        DiagnosticFindingsView correction = findings.createCorrection(doctor(), request.requestId(), "correct-1",
                reviewedVersion, "Small left effusion", "Added after image re-review", "corr-correction");
        assertEquals(2, correction.version());
        assertEquals("PRIVATE", correction.releaseState());
        assertEquals(correction.findingsVersionId(), findings.createCorrection(doctor(), request.requestId(),
                "correct-1", reviewedVersion, "Small left effusion", "Added after image re-review",
                "corr-correction-replay").findingsVersionId());
        org.junit.jupiter.api.Assertions.assertTrue(completion.getCompletionEvidence(visitId, patientId, java.util.List.of(clinicalOrder)).allComplete());
        assertEquals("Added after image re-review", correction.correctionReason());
        assertEquals("COMPLETED", jdbc.queryForObject("select state from diagnostic_reports "
                + "where service_order_id = ? and version_number = 1", String.class, request.requestId()));
        assertNull(jdbc.queryForObject("select completed_at from diagnostic_reports "
                + "where service_order_id = ? and version_number = 2", java.sql.Timestamp.class, request.requestId()));

        DiagnosticReportView correctionReport = upload(request.requestId(), reviewedVersion, "report-2");
        int ownerAcksBefore = ports.findingsReleaseCalls;
        java.util.concurrent.atomic.AtomicBoolean observedFirstAck = new java.util.concurrent.atomic.AtomicBoolean();
        ports.beforeStudyReleaseAck = event -> {
            assertEquals(ownerAcksBefore + 1, ports.findingsReleaseCalls);
            assertEquals(correction.findingsVersionId(), event.findingsVersionId());
            assertEquals(correctionReport.reportId(), event.reportId());
            assertEquals("COMPLETED", jdbc.queryForObject(
                    "select state from diagnostic_reports where id = ?", String.class, correction.findingsVersionId()));
            assertNotNull(jdbc.queryForObject("select released_at from diagnostic_reports where id = ?",
                    java.sql.Timestamp.class, correction.findingsVersionId()));
            assertEquals("COMPLETED", jdbc.queryForObject("select state from diagnostic_reports where id = ?",
                    String.class, firstDraft.findingsVersionId()));
            assertEquals(firstReport.reportId(), jdbc.queryForObject("select report_id from diagnostic_reports where id = ?",
                    UUID.class, firstDraft.findingsVersionId()));
            assertNotNull(jdbc.queryForObject("select released_at from diagnostic_reports where id = ?",
                    java.sql.Timestamp.class, firstDraft.findingsVersionId()));
            observedFirstAck.set(true);
        };
        DiagnosticFindingsReleaseView correctionRelease = findings.complete(doctor(), request.requestId(),
                "complete-2", reviewedVersion, correction.version(), correctionReport.reportId(), studyRef,
                "corr-complete-2");
        assertEquals("RELEASED", correctionRelease.releaseState());
        org.junit.jupiter.api.Assertions.assertTrue(observedFirstAck.get());
        assertReleasedVersion(request.requestId(), firstDraft.findingsVersionId(), firstReport.reportId());
        assertReleasedVersion(request.requestId(), correction.findingsVersionId(), correctionReport.reportId());
        assertEquals(originalFindings, jdbc.queryForMap(
                "select * from diagnostic_reports where id = ?", firstDraft.findingsVersionId()));
        assertEquals("RELEASED", findings.complete(doctor(), request.requestId(), "complete-2", reviewedVersion,
                correction.version(), correctionReport.reportId(), studyRef, "corr-complete-replay").releaseState());
        int ownerAcksAfter = ports.findingsReleaseCalls;
        int studyAcksAfter = ports.studyReleaseCalls;
        assertEquals(ownerAcksAfter, ports.findingsReleaseCalls);
        assertEquals(studyAcksAfter, ports.studyReleaseCalls);

        assertEquals(2, jdbc.queryForObject("select count(*) from diagnostic_reports "
                + "where service_order_id = ? and state = 'COMPLETED'", Integer.class, request.requestId()));
        assertNotNull(jdbc.queryForObject("select released_at from diagnostic_reports where id = ?",
                java.sql.Timestamp.class, correction.findingsVersionId()));
    }

    @Test
    void findingsCompleteWithoutAPdfReport() {
        DiagnosticRequestView request = resultAvailable();
        findings.review(doctor(), request.requestId(), "review-nopdf", request.version(), studyRef, "corr-review");
        long version = request.version() + 1;
        DiagnosticFindingsView draft = findings.saveDraft(doctor(), request.requestId(), "draft-nopdf", version,
                "No acute finding", "corr-draft");
        DiagnosticFindingsReleaseView release = findings.complete(doctor(), request.requestId(), "complete-nopdf",
                version, draft.version(), null, studyRef, "corr-complete");
        assertEquals("RELEASED", release.releaseState());
        assertNull(jdbc.queryForObject("select report_id from diagnostic_reports where id = ?", UUID.class,
                draft.findingsVersionId()));
        assertEquals("COMPLETED", jdbc.queryForObject("select state from diagnostic_reports where id = ?",
                String.class, draft.findingsVersionId()));
        assertNotNull(jdbc.queryForObject("select released_at from diagnostic_reports where id = ?",
                java.sql.Timestamp.class, draft.findingsVersionId()));
        assertTrue(completion.getCompletionEvidence(visitId, patientId, java.util.List.of(serviceOrderId)).allComplete());
        // Replay with the same key stays idempotent.
        assertEquals("RELEASED", findings.complete(doctor(), request.requestId(), "complete-nopdf", version,
                draft.version(), null, studyRef, "corr-replay").releaseState());
    }

    @Test
    void currentResponsibleDoctorWhoDidNotOrderCanRecordFindings() {
        DiagnosticRequestView request = resultAvailable();
        UUID attending = UUID.randomUUID();
        ports.visits.put(visitId, new VisitEvidence(visitId, patientId, attending, true, true, true));
        DiagnosticActor attendingDoctor = DiagnosticActor.user(attending, AccountRole.DOCTOR);
        findings.review(attendingDoctor, request.requestId(), "review-attending", request.version(), studyRef, "corr-review");
        long version = request.version() + 1;
        DiagnosticFindingsView draft = findings.saveDraft(attendingDoctor, request.requestId(), "draft-attending", version,
                "Healed fracture", "corr-draft");
        assertEquals(1, draft.version());
        // A doctor who neither ordered nor is responsible is still refused.
        DiagnosticActor stranger = DiagnosticActor.user(UUID.randomUUID(), AccountRole.DOCTOR);
        assertThrows(DiagnosticOperationException.class, () -> findings.saveDraft(stranger, request.requestId(),
                "draft-stranger", version, "Other", "corr-draft"));
    }

    @Test
    void ownerReleaseFailureLeavesNewFindingsAndPdfPrivateForRetry() {
        DiagnosticRequestView request = resultAvailable();
        findings.review(doctor(), request.requestId(), "review-fail", request.version(), studyRef, "corr-review-fail");
        long version = request.version() + 1;
        DiagnosticFindingsView draft = findings.saveDraft(doctor(), request.requestId(), "draft-fail", version,
                "Preliminary findings", "corr-draft-fail");
        DiagnosticReportView report = upload(request.requestId(), version, "report-fail");
        ports.findingsReleaseFails = true;
        DiagnosticOperationException failure = assertThrows(DiagnosticOperationException.class,
                () -> findings.complete(doctor(), request.requestId(), "complete-fail", version, draft.version(),
                        report.reportId(), studyRef, "corr-complete-fail"));
        assertEquals(DiagnosticOperationOutcome.DEPENDENCY_UNAVAILABLE, failure.outcome());
        assertEquals("DRAFT", jdbc.queryForObject("select state from diagnostic_reports where id = ?",
                String.class, draft.findingsVersionId()));
        assertNull(jdbc.queryForObject("select released_at from diagnostic_reports where id = ?",
                java.sql.Timestamp.class, draft.findingsVersionId()));
    }

    @Test
    void completedResultTriggerAllowsReleaseTransitionAndRejectsContentMutation() {
        DiagnosticRequestView request = resultAvailable();
        findings.review(doctor(), request.requestId(), "review-trigger", request.version(), studyRef,
                "corr-review-trigger");
        long executionVersion = request.version() + 1;
        DiagnosticFindingsView draft = findings.saveDraft(doctor(), request.requestId(), "draft-trigger",
                executionVersion, "Original completed findings", "corr-draft-trigger");
        upload(request.requestId(), executionVersion, "report-trigger");

        java.time.Instant releasedAt = java.time.Instant.now()
                .truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        assertEquals(1, jdbc.update("""
                update diagnostic_reports
                   set state = 'COMPLETED', completed_at = ?, released_at = ?
                 where id = ? and state = 'DRAFT'
                """, java.sql.Timestamp.from(releasedAt), java.sql.Timestamp.from(releasedAt),
                draft.findingsVersionId()));
        assertEquals("COMPLETED", jdbc.queryForObject("select state from diagnostic_reports where id = ?",
                String.class, draft.findingsVersionId()));
        assertNotNull(jdbc.queryForObject("select released_at from diagnostic_reports where id = ?",
                java.sql.Timestamp.class, draft.findingsVersionId()));

        DataAccessException mutationFailure = assertThrows(DataAccessException.class, () -> jdbc.update(
                "update diagnostic_reports set findings_text = 'tampered' where id = ?",
                draft.findingsVersionId()));
        assertTrue(mutationFailure.getMostSpecificCause().getMessage()
                .contains("released or completed results are immutable"));
        assertEquals("Original completed findings", jdbc.queryForObject(
                "select findings_text from diagnostic_reports where id = ?", String.class,
                draft.findingsVersionId()));
    }

    private void assertReleasedVersion(UUID requestId, UUID findingsId, UUID reportId) {
        assertEquals(findingsId, jdbc.queryForObject("select id from diagnostic_reports "
                + "where service_order_id = ? and id = ? and state = 'COMPLETED'",
                UUID.class, requestId, findingsId));
        assertEquals(reportId, jdbc.queryForObject("select report_id from diagnostic_reports "
                + "where service_order_id = ? and id = ? and released_at is not null",
                UUID.class, requestId, findingsId));
        assertEquals(reportId, jdbc.queryForObject("select report_id from diagnostic_reports "
                + "where id = ?", UUID.class, findingsId));
    }

    protected DiagnosticRequestView createRequest() {
        UUID orderId = UUID.randomUUID();
        serviceOrderId = orderId;
        ports.orders.put(orderId, new fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ClinicalImagingOrder(orderId, visitId, serviceId, true, true));
        return DiagnosticRequestT012PostgresIntegrationTest.registerAssigned(jdbc, transactions, registration, ports,
                visitId, roomId, orderId, "Chest", "Persistent cough");
    }

    protected DiagnosticRequestView resultAvailable() {
        DiagnosticRequestView created = createRequest();
        UUID attemptId = UUID.randomUUID();
        Instant now = Instant.now();
        UseEvidence use = new UseEvidence(UUID.randomUUID(), created.requestId(), attemptId,
                UseEvidenceType.ACQUISITION_STARTED, "approved-imaging-source", doctorId,
                now, now, true);
        DiagnosticRequestView inProgress = evidence.recordFirstUse(DiagnosticActor.trustedIntegration(), use, "corr-use");
        assertEquals(DiagnosticRequestStatus.IN_PROGRESS, inProgress.status());
        assertEquals(1L, jdbc.queryForObject("select version from imaging_executions where service_order_id = ?",
                Long.class, created.requestId()));
        assertEquals("IN_PROGRESS", jdbc.queryForObject("select execution_status from imaging_executions where service_order_id = ?",
                String.class, created.requestId()));
        StudyEvidence study = new StudyEvidence(UUID.randomUUID(), studyRef, created.requestId(), visitId,
                patientId, attemptId, StudyVerificationStatus.CONFIRMED_MATCH, true);
        DiagnosticRequestView resultAvailable = evidence.linkConfirmedStudy(DiagnosticActor.trustedIntegration(), study, "corr-study");
        assertEquals(DiagnosticRequestStatus.RESULT_AVAILABLE, resultAvailable.status());
        assertEquals(2L, jdbc.queryForObject("select version from imaging_executions where service_order_id = ?",
                Long.class, created.requestId()));
        assertEquals(studyRef, jdbc.queryForObject("select study_ref from imaging_executions where service_order_id = ?",
                String.class, created.requestId()));
        return resultAvailable;
    }

    protected DiagnosticReportView upload(UUID requestId, long expectedVersion, String key) {
        return findings.uploadReport(doctor(), requestId, key, expectedVersion,
                new DiagnosticReportUpload(() -> new java.io.ByteArrayInputStream(
                        "%PDF-1.7 sample".getBytes(java.nio.charset.StandardCharsets.US_ASCII)),
                        15, "application/pdf"), "corr-" + key);
    }

    protected DiagnosticActor doctor() { return DiagnosticActor.user(doctorId, AccountRole.DOCTOR); }

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
