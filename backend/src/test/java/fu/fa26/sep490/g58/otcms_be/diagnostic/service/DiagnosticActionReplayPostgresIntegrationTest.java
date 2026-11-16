package fu.fa26.sep490.g58.otcms_be.diagnostic.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticFindingsReleaseView;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticFindingsView;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticReportUpload;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticRequestView;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.dto.DiagnosticDtos.CancellationCause;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Database-backed replay and idempotency-conflict checks for diagnostic actions. */
@SpringBootTest
@LocalPostgresTest
@Import({DiagnosticRequestT012PostgresIntegrationTest.PortConfiguration.class,
        DiagnosticFindingsT014PostgresIntegrationTest.ReportStorageConfiguration.class})
class DiagnosticActionReplayPostgresIntegrationTest extends DiagnosticFindingsT014PostgresIntegrationTest {

    @Test
    void cancelAndTransferRejectSameKeyWithChangedPayload() {
        DiagnosticRequestView cancelRequest = createRequest();
        requests.cancel(doctor(), cancelRequest.requestId(), "cancel-replay", 0,
                CancellationCause.PATIENT_DECLINED, "Patient declined", "corr-cancel-1");
        DiagnosticOperationException cancelConflict = assertThrows(DiagnosticOperationException.class,
                () -> requests.cancel(doctor(), cancelRequest.requestId(), "cancel-replay", 0,
                        CancellationCause.CLINIC_UNABLE, "Clinic unavailable", "corr-cancel-2"));
        assertEquals(DiagnosticOperationOutcome.IDEMPOTENCY_CONFLICT, cancelConflict.outcome());

        DiagnosticRequestView transferRequest = createRequest();
        UUID roomOne = UUID.randomUUID();
        UUID roomTwo = UUID.randomUUID();
        ports.addRoom(roomOne, "MRI", UUID.randomUUID(), 7);
        ports.addRoom(roomTwo, "MRI", UUID.randomUUID(), 8);
        requests.transferRoom(doctor(), transferRequest.requestId(), "transfer-replay", 0,
                roomOne, "Machine service", "corr-transfer-1");
        DiagnosticOperationException transferConflict = assertThrows(DiagnosticOperationException.class,
                () -> requests.transferRoom(doctor(), transferRequest.requestId(), "transfer-replay", 0,
                        roomTwo, "Different destination", "corr-transfer-2"));
        assertEquals(DiagnosticOperationOutcome.IDEMPOTENCY_CONFLICT, transferConflict.outcome());
    }

    @Test
    void reviewFindingsReportCorrectionAndCompleteRejectChangedReplayBodies() {
        DiagnosticRequestView request = resultAvailable();
        findings.review(doctor(), request.requestId(), "review-replay", request.version(), studyRef, "corr-review-1");
        DiagnosticOperationException reviewConflict = assertThrows(DiagnosticOperationException.class,
                () -> findings.review(doctor(), request.requestId(), "review-replay", request.version(),
                        "different-study", "corr-review-2"));
        assertEquals(DiagnosticOperationOutcome.IDEMPOTENCY_CONFLICT, reviewConflict.outcome());

        long reviewedVersion = request.version() + 1;
        DiagnosticFindingsView draft = findings.saveDraft(doctor(), request.requestId(), "draft-replay",
                reviewedVersion, "No acute finding", "corr-draft-1");
        DiagnosticOperationException draftConflict = assertThrows(DiagnosticOperationException.class,
                () -> findings.saveDraft(doctor(), request.requestId(), "draft-replay", reviewedVersion,
                        "Changed finding", "corr-draft-2"));
        assertEquals(DiagnosticOperationOutcome.IDEMPOTENCY_CONFLICT, draftConflict.outcome());

        DiagnosticReportUpload originalPdf = pdf("%PDF-1.7 original", "application/pdf");
        findings.uploadReport(doctor(), request.requestId(), "report-replay", reviewedVersion,
                originalPdf, "corr-report-1");
        DiagnosticOperationException reportConflict = assertThrows(DiagnosticOperationException.class,
                () -> findings.uploadReport(doctor(), request.requestId(), "report-replay", reviewedVersion,
                        pdf("%PDF-1.7 changed", "application/pdf"), "corr-report-2"));
        assertEquals(DiagnosticOperationOutcome.IDEMPOTENCY_CONFLICT, reportConflict.outcome());

        var report = findings.uploadReport(doctor(), request.requestId(), "report-complete", reviewedVersion,
                pdf("%PDF-1.7 release", "application/pdf"), "corr-report-complete");
        DiagnosticFindingsReleaseView released = findings.complete(doctor(), request.requestId(), "complete-replay",
                reviewedVersion, draft.version(), report.reportId(), studyRef, "corr-complete-1");
        assertEquals("RELEASED", released.releaseState());
        DiagnosticOperationException completeConflict = assertThrows(DiagnosticOperationException.class,
                () -> findings.complete(doctor(), request.requestId(), "complete-replay", reviewedVersion,
                        draft.version(), UUID.randomUUID(), studyRef, "corr-complete-2"));
        assertEquals(DiagnosticOperationOutcome.IDEMPOTENCY_CONFLICT, completeConflict.outcome());

        DiagnosticFindingsView correction = findings.createCorrection(doctor(), request.requestId(), "correction-replay",
                reviewedVersion, "Small effusion", "Added after review", "corr-correction-1");
        DiagnosticOperationException correctionConflict = assertThrows(DiagnosticOperationException.class,
                () -> findings.createCorrection(doctor(), request.requestId(), "correction-replay", reviewedVersion,
                        "Large effusion", "Changed reason", "corr-correction-2"));
        assertEquals(DiagnosticOperationOutcome.IDEMPOTENCY_CONFLICT, correctionConflict.outcome());
        assertEquals(2, correction.version());
    }

    private static DiagnosticReportUpload pdf(String content, String contentType) {
        byte[] bytes = content.getBytes(StandardCharsets.US_ASCII);
        return new DiagnosticReportUpload(() -> new ByteArrayInputStream(bytes), bytes.length, contentType);
    }
}
