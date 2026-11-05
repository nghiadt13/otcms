package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import fu.fa26.sep490.g58.otcms_be.auth.service.DoctorAccountAccessService;
import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.service.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalExaminationHistoryQueryService.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalPrescriptionHistoryQueryService.IssuedPrescriptionSummary;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalMedicalDocumentHistoryQueryService.DocumentSummary;
import fu.fa26.sep490.g58.otcms_be.clinical.service.impl.ClinicalPastMedicalRecordsServiceImpl;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticHistoryItem;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticRequestStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticPatientHistoryQueryService;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticPatientHistoryQueryService.HistoricalImagingResult;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitPatientHistoryQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitPatientHistoryQueryService.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ClinicalPastMedicalRecordsServiceImplTest {
    DoctorAccountAccessService accounts = mock(DoctorAccountAccessService.class);
    VisitClinicalContextPort contexts = mock(VisitClinicalContextPort.class);
    VisitPatientHistoryQueryService visits = mock(VisitPatientHistoryQueryService.class);
    ClinicalExaminationHistoryQueryService exams = mock(ClinicalExaminationHistoryQueryService.class);
    ClinicalPrescriptionHistoryQueryService rx = mock(ClinicalPrescriptionHistoryQueryService.class);
    ClinicalMedicalDocumentHistoryQueryService docs = mock(ClinicalMedicalDocumentHistoryQueryService.class);
    DiagnosticPatientHistoryQueryService images = mock(DiagnosticPatientHistoryQueryService.class);
    ClinicalPastMedicalRecordsService service = new ClinicalPastMedicalRecordsServiceImpl(accounts, contexts, visits, exams, rx, docs, images);
    UUID current = UUID.randomUUID(), patient = UUID.randomUUID(), doctor = UUID.randomUUID(), account = UUID.randomUUID();
    UUID old = UUID.randomUUID(), older = UUID.randomUUID();
    @BeforeEach void setup() {
        when(accounts.isActiveDoctorAccount(account)).thenReturn(true);
        when(contexts.getVisitContext(current)).thenReturn(new VisitClinicalContextPort.ClinicalVisitContext(current, patient, doctor, true));
        when(visits.findPastVisits(patient, current, 0, 20)).thenReturn(new HistoryPage(List.of(), 0));
    }
    void page() {
        when(visits.findPastVisits(patient, current, 0, 20)).thenReturn(new HistoryPage(List.of(
                new HistoricalVisit(old, patient, Instant.EPOCH.plusSeconds(10), "CLOSED"),
                new HistoricalVisit(older, patient, Instant.EPOCH, "CLOSED")), 7));
    }
    void rejects(String code) {
        assertEquals(code, assertThrows(ClinicalOperationException.class,
                () -> service.getPastMedicalRecords(current, doctor, account, 0, 20)).code());
    }
    @Test void emptyHistoryIsAnEmptyPageAndAuditsAuthorizedDiagnosticAccess() {
        var response = service.getPastMedicalRecords(current, doctor, account, 0, 20);
        assertEquals(current, response.currentVisitId()); assertEquals(patient, response.patientProfileId());
        assertTrue(response.items().isEmpty()); assertEquals(0, response.total());
        verify(images).findReleased(patient, Set.of(), account, 0, 20);
    }
    @Test void wrongDoctorCannotCallAnyHistoryOwner() {
        when(contexts.getVisitContext(current)).thenReturn(new VisitClinicalContextPort.ClinicalVisitContext(current, patient, UUID.randomUUID(), true));
        rejects("MSG-40"); verifyNoInteractions(visits, exams, rx, docs, images);
    }
    @Test void inactiveDoctorCannotRead() { when(accounts.isActiveDoctorAccount(account)).thenReturn(false); rejects("MSG-40"); verifyNoInteractions(contexts, visits); }
    @Test void missingVisitIsUnavailable() { when(contexts.getVisitContext(current)).thenReturn(null); rejects("MSG-41"); verifyNoInteractions(visits); }
    @Test void noCurrentCareIsConflict() {
        when(contexts.getVisitContext(current)).thenReturn(new VisitClinicalContextPort.ClinicalVisitContext(current, patient, doctor, false));
        rejects("MSG-42"); verifyNoInteractions(visits);
    }
    @ParameterizedTest @CsvSource({"-1,20", "0,0", "0,51"})
    void invalidPageFailsBeforeDataLookup(int page, int size) {
        assertEquals("MSG-39", assertThrows(ClinicalOperationException.class,
                () -> service.getPastMedicalRecords(current, doctor, account, page, size)).code()); verifyNoInteractions(accounts, contexts);
    }
    @Test void nullCurrentVisitRejected() {
        assertEquals("MSG-39", assertThrows(ClinicalOperationException.class,
                () -> service.getPastMedicalRecords(null, doctor, account, 0, 20)).code());
    }
    @Test void completedExaminationAndAdviceRemainAttachedToCorrectVisit() {
        page(); var examination = new Examination("reason", "symptom", "finding", "knee", "RIGHT", null, List.of());
        var advice = new Advice("rest", null, null, "warning", "review", true);
        when(exams.findCompletedByVisitIds(any())).thenReturn(Map.of(old, new CompletedRecord(old, examination, advice)));
        var result = service.getPastMedicalRecords(current, doctor, account, 0, 20);
        assertEquals(7, result.total()); assertEquals(List.of(old, older), result.items().stream().map(i -> i.visitId()).toList());
        assertEquals(examination, result.items().getFirst().examination()); assertEquals(advice, result.items().getFirst().advice());
        assertNull(result.items().get(1).examination()); assertNull(result.items().get(1).advice());
        assertThrows(UnsupportedOperationException.class, () -> result.items().clear());
        verify(exams, times(1)).findCompletedByVisitIds(Set.of(old, older));
    }
    @Test void issuedPrescriptionAndMetadataAndOtherDoctorsReleasedImagingComposeWithoutHistoricalAuthorization() {
        page(); UUID previousDoctor = UUID.randomUUID();
        var prescription = new IssuedPrescriptionSummary(UUID.randomUUID(), old, patient, previousDoctor, Instant.EPOCH, 2,
                List.of(new ClinicalPrescriptionService.PrescriptionLine(1, "medicine", "dose", "frequency", "usage")));
        var document = new DocumentSummary(UUID.randomUUID(), older, patient, "OUTSIDE_REPORT", "External blood report", "application/pdf", Instant.EPOCH);
        var image = new DiagnosticHistoryItem(UUID.randomUUID(), old, "MRI", DiagnosticRequestStatus.REVIEWED, "study-reference", 1, "completed", UUID.randomUUID());
        when(rx.findIssuedByPatientAndVisitIds(eq(patient), any())).thenReturn(List.of(prescription));
        when(docs.findReleased(eq(patient), any())).thenReturn(List.of(document));
        when(images.findReleased(eq(patient), any(), eq(account), eq(0), eq(20))).thenReturn(List.of(new HistoricalImagingResult(patient, image)));
        var result = service.getPastMedicalRecords(current, doctor, account, 0, 20);
        assertEquals(prescription, result.items().getFirst().prescription()); assertNull(result.items().get(1).prescription());
        assertEquals(List.of(document), result.items().get(1).documents()); assertTrue(result.items().getFirst().documents().isEmpty());
        assertEquals(List.of(image), result.items().getFirst().imagingResults());
        verify(rx, never()).findIssuedByPatient(any()); verify(rx, never()).findIssuedByVisit(any());
    }
    @ParameterizedTest @CsvSource({"OPEN,false", "CLOSED,true"})
    void ownerCannotReturnActiveOrCurrentVisit(String status, boolean useCurrent) {
        when(visits.findPastVisits(patient, current, 0, 20)).thenReturn(new HistoryPage(List.of(
                new HistoricalVisit(useCurrent ? current : old, patient, Instant.EPOCH, status)), 1));
        rejects("DEPENDENCY_UNAVAILABLE"); verifyNoInteractions(exams, rx, docs, images);
    }
    @Test void anotherPatientVisitIsRejectedFailClosed() {
        when(visits.findPastVisits(patient, current, 0, 20)).thenReturn(new HistoryPage(List.of(
                new HistoricalVisit(old, UUID.randomUUID(), Instant.EPOCH, "CLOSED")), 1)); rejects("DEPENDENCY_UNAVAILABLE");
    }
    @Test void anotherPatientsDocumentCannotLeak() {
        page(); when(docs.findReleased(eq(patient), any())).thenReturn(List.of(new DocumentSummary(UUID.randomUUID(), old,
                UUID.randomUUID(), "OUTSIDE_REPORT", "secret", "application/pdf", Instant.EPOCH))); rejects("DEPENDENCY_UNAVAILABLE");
    }
    @Test void anotherPatientsImagingCannotLeak() {
        page(); when(images.findReleased(eq(patient), any(), any(), anyInt(), anyInt())).thenReturn(List.of(
                new HistoricalImagingResult(UUID.randomUUID(), new DiagnosticHistoryItem(UUID.randomUUID(), old, "CT", DiagnosticRequestStatus.REVIEWED,
                        "reference", 1, "secret", UUID.randomUUID())))); rejects("DEPENDENCY_UNAVAILABLE");
    }
    @Test void infrastructureFailureDoesNotBecomeEmptyHistory() {
        when(visits.findPastVisits(patient, current, 0, 20)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("unavailable"));
        assertThrows(org.springframework.dao.DataAccessException.class, () -> service.getPastMedicalRecords(current, doctor, account, 0, 20));
    }
}
