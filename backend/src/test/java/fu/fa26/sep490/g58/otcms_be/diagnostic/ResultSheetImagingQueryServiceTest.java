package fu.fa26.sep490.g58.otcms_be.diagnostic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort.ExecutionStatus;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort.ImagingExecution;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticReportValidationStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticReportJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticReportJpaRepository.ResultSheetReport;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl.ResultSheetImagingQueryServiceImpl;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ResultSheetImagingQueryServiceTest {
    private final ClinicalServiceOrderExecutionPort requests = mock(ClinicalServiceOrderExecutionPort.class);
    private final DiagnosticReportJpaRepository reports = mock(DiagnosticReportJpaRepository.class);
    private final ResultSheetImagingQueryServiceImpl service = new ResultSheetImagingQueryServiceImpl(requests, reports);
    private final UUID patientId = UUID.randomUUID(), visitId = UUID.randomUUID(), orderId = UUID.randomUUID();

    @Test
    void returnsOnlyTheLatestReleasedValidatedMatchingImagingFinding() {
        var row = row(patientId);
        var newest = report("study", true);
        var old = report("study", true);
        when(newest.getFindingsText()).thenReturn("Không gãy xương");
        var otherPatientRow = row(UUID.randomUUID());
        when(requests.findAllByVisit(visitId)).thenReturn(List.of(row, otherPatientRow));
        when(reports.findResultSheetReports(anyCollection(), any())).thenReturn(List.of(newest, old));

        var result = service.findReleased(patientId, visitId);
        assertEquals(1, result.size());
        assertEquals("Không gãy xương", result.getFirst().findingsText());
        assertEquals("X-quang", result.getFirst().imagingType());
    }

    @Test
    void excludesInvalidLatestVersionAndMismatchedStudy() {
        var row = row(patientId);
        when(requests.findAllByVisit(visitId)).thenReturn(List.of(row));
        var invalid = report("study", false);
        var older = report("study", true);
        when(reports.findResultSheetReports(anyCollection(), any())).thenReturn(List.of(invalid, older));
        assertTrue(service.findReleased(patientId, visitId).isEmpty());

        var mismatched = report("other-study", true);
        when(reports.findResultSheetReports(anyCollection(), any())).thenReturn(List.of(mismatched));
        assertTrue(service.findReleased(patientId, visitId).isEmpty());
    }

    @Test
    void keepsReleasedFindingsWithoutPdfButStillRejectsUnvalidatedPdf() {
        var row = row(patientId);
        when(requests.findAllByVisit(visitId)).thenReturn(List.of(row));
        var noPdf = report("study", true);
        when(noPdf.getReportId()).thenReturn(null);
        when(noPdf.getValidationStatus()).thenReturn(null);
        when(noPdf.getFindingsText()).thenReturn("Chỉ có kết luận chữ");
        when(reports.findResultSheetReports(anyCollection(), any())).thenReturn(List.of(noPdf));
        var result = service.findReleased(patientId, visitId);
        assertEquals(1, result.size());
        assertEquals("Chỉ có kết luận chữ", result.getFirst().findingsText());

        var unvalidatedPdf = report("study", true);
        when(unvalidatedPdf.getValidationStatus()).thenReturn(DiagnosticReportValidationStatus.INVALID);
        when(reports.findResultSheetReports(anyCollection(), any())).thenReturn(List.of(unvalidatedPdf));
        assertTrue(service.findReleased(patientId, visitId).isEmpty());
    }

    private ImagingExecution row(UUID patient) {
        var row = mock(ImagingExecution.class);
        when(row.patientId()).thenReturn(patient);
        when(row.orderId()).thenReturn(orderId);
        when(row.status()).thenReturn(ExecutionStatus.REVIEWED);
        when(row.studyRef()).thenReturn("study");
        when(row.imagingType()).thenReturn("X-quang");
        return row;
    }

    private ResultSheetReport report(String study, boolean released) {
        var report = mock(ResultSheetReport.class);
        when(report.getRequestId()).thenReturn(orderId);
        when(report.getVisitId()).thenReturn(visitId);
        when(report.getStudyRef()).thenReturn(study);
        when(report.getCompletedAt()).thenReturn(Instant.EPOCH);
        when(report.getReleasedAt()).thenReturn(released ? Instant.EPOCH : null);
        when(report.getReportId()).thenReturn(UUID.randomUUID());
        when(report.getValidationStatus()).thenReturn(DiagnosticReportValidationStatus.VALIDATED);
        return report;
    }
}
