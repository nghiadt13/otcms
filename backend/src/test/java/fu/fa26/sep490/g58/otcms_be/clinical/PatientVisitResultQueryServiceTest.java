package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentReviewContextService;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory;
import fu.fa26.sep490.g58.otcms_be.clinical.model.DiagnosisType;
import fu.fa26.sep490.g58.otcms_be.clinical.model.dto.PatientVisitResultDtos.ResultSheet;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalExaminationHistoryQueryService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalExaminationHistoryQueryService.CompletedRecord;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalMedicalDocumentHistoryQueryService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalMedicalDocumentHistoryQueryService.DocumentSummary;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalPrescriptionHistoryQueryService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalPrescriptionHistoryQueryService.IssuedPrescriptionSummary;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalPrescriptionService.PrescriptionLine;
import fu.fa26.sep490.g58.otcms_be.clinical.service.impl.PatientVisitResultQueryServiceImpl;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticReleasedReportService;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticReleasedReportService.ReleasedReport;
import fu.fa26.sep490.g58.otcms_be.patient.model.dto.DependentDtos.Items;
import fu.fa26.sep490.g58.otcms_be.patient.model.dto.DependentDtos.ProfileItem;
import fu.fa26.sep490.g58.otcms_be.patient.service.DependentProfileService;
import fu.fa26.sep490.g58.otcms_be.patient.service.PatientProfileAccessService;
import fu.fa26.sep490.g58.otcms_be.visit.service.PatientPortalVisitHistoryQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.service.PatientPortalVisitHistoryQueryService.PatientVisit;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** BR-35 / UC-41: what the patient portal exposes per visit status, and the result sheet version list. */
class PatientVisitResultQueryServiceTest {
    final UUID account = UUID.randomUUID(), patient = UUID.randomUUID(), visit = UUID.randomUUID();
    final Instant at = Instant.parse("2026-11-05T02:00:00Z");
    final PatientProfileAccessService access = mock(PatientProfileAccessService.class);
    final DependentProfileService dependents = mock(DependentProfileService.class);
    final PatientProfileDirectory profiles = mock(PatientProfileDirectory.class);
    final PatientPortalVisitHistoryQueryService visits = mock(PatientPortalVisitHistoryQueryService.class);
    final ClinicalExaminationHistoryQueryService examinations = mock(ClinicalExaminationHistoryQueryService.class);
    final ClinicalPrescriptionHistoryQueryService prescriptions = mock(ClinicalPrescriptionHistoryQueryService.class);
    final ClinicalMedicalDocumentHistoryQueryService documents = mock(ClinicalMedicalDocumentHistoryQueryService.class);
    final DiagnosticReleasedReportService reports = mock(DiagnosticReleasedReportService.class);
    final AppointmentReviewContextService appointments = mock(AppointmentReviewContextService.class);
    final PatientVisitResultQueryServiceImpl service = new PatientVisitResultQueryServiceImpl(access, dependents, profiles,
            visits, examinations, prescriptions, documents, reports,
            mock(fu.fa26.sep490.g58.otcms_be.clinical.repository.ServiceOrderRepository.class),
            mock(fu.fa26.sep490.g58.otcms_be.diagnostic.service.LaboratoryDraftService.class), appointments);

    @BeforeEach void setup() {
        when(access.accessibleProfileIds(account)).thenReturn(Set.of(patient));
        when(dependents.list(account)).thenReturn(new Items<>(List.of(new ProfileItem(patient, "Nguyễn Thị Ánh",
                LocalDate.of(1990, 1, 1), "FEMALE", "SELF", "CONFIRMED", null))));
        when(profiles.findProfiles(any())).thenReturn(List.of(new PatientProfileDirectory.ProfileRecord(patient, account,
                "Nguyễn Thị Ánh", "+84900000000", "PATIENT", LocalDate.of(1990, 1, 1), "FEMALE")));
        when(visits.findPreviousVisit(any())).thenReturn(Optional.empty());
        when(visits.findNextVisit(any())).thenReturn(Optional.empty());
        var image = new ReleasedReport(UUID.randomUUID(), visit, 1, "CURRENT", "X-quang", "Gối", "study-1", "Không gãy xương", at);
        when(reports.listForPatientVisit(any(), eq(visit))).thenReturn(List.of(image));
        var exam = new ClinicalExaminationHistoryQueryService.Examination("Đau gối", "Đau", "Sưng", null, null, null,
                List.of(new ClinicalExaminationHistoryQueryService.Diagnosis("M25.5", DiagnosisType.PRIMARY)), null, null);
        var advice = new ClinicalExaminationHistoryQueryService.Advice("Chườm lạnh", null, null, null, null, null);
        when(examinations.findCompletedByVisitIds(List.of(visit))).thenReturn(Map.of(visit, new CompletedRecord(visit, exam, advice)));
        when(prescriptions.findIssuedByPatientAndVisitIds(patient, List.of(visit))).thenReturn(List.of(new IssuedPrescriptionSummary(
                UUID.randomUUID(), visit, patient, UUID.randomUUID(), at, 2, List.of(new PrescriptionLine(1, "Ibuprofen", "400mg", "2/ngày", "Sau ăn")))));
    }

    private void visitWithStatus(String status) {
        when(visits.findVisit(eq(visit), any())).thenReturn(Optional.of(new PatientVisit(visit, patient, at, status, "WALK_IN", null)));
    }

    @Test void closedVisitShowsEverythingAndListsEveryResultSheetVersion() {
        visitWithStatus("CLOSED");
        UUID current = UUID.randomUUID(), previous = UUID.randomUUID();
        when(documents.findReleased(patient, List.of(visit))).thenReturn(List.of(
                new DocumentSummary(current, visit, patient, "CLINIC_EXAMINATION_REPORT", "Phiếu kết quả khám", "application/pdf", at.plusSeconds(60), 2, "ACTIVE"),
                new DocumentSummary(previous, visit, patient, "CLINIC_EXAMINATION_REPORT", "Phiếu kết quả khám", "application/pdf", at, 1, "REPLACED")));

        var result = service.get(account, visit);

        assertEquals("COMPLETED", result.status());
        assertNotNull(result.examination());
        assertNotNull(result.prescriptions());
        assertTrue(result.visibleBlocks().containsAll(List.of("EXAMINATION", "IMAGING", "PRESCRIPTIONS", "RESULT_SHEET")));
        List<ResultSheet> sheets = result.resultSheets().stream().filter(s -> s.title().startsWith("Phiếu")).toList();
        assertEquals(List.of(2L, 1L), sheets.stream().map(ResultSheet::version).toList());
        assertEquals(List.of("CURRENT", "REPLACED"), sheets.stream().map(ResultSheet::status).toList());
    }

    @Test void incompleteVisitOnlyShowsImagingAndNeverExaminationPrescriptionOrSheets() {
        visitWithStatus("INCOMPLETE");

        var result = service.get(account, visit);

        assertEquals("INCOMPLETE", result.status());
        assertNull(result.examination());
        assertNull(result.prescriptions());
        assertFalse(result.visibleBlocks().contains("EXAMINATION"));
        assertFalse(result.visibleBlocks().contains("PRESCRIPTIONS"));
        assertNotNull(result.imaging());
        verifyNoInteractions(prescriptions, documents);
        verify(examinations, never()).findCompletedByVisitIds(any());
    }
}
