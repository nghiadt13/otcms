package fu.fa26.sep490.g58.otcms_be.diagnostic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort.ImagingExecution;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort.ExecutionStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl.DiagnosticViewerAuthorizationServiceImpl;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class DiagnosticViewerAuthorizationServiceTest {
    final UUID actor = UUID.randomUUID(), visit = UUID.randomUUID(), patient = UUID.randomUUID();
    final UUID requestId = UUID.randomUUID(), attemptId = UUID.randomUUID(), reportId = UUID.randomUUID();
    final ClinicalServiceOrderExecutionPort requests = mock(ClinicalServiceOrderExecutionPort.class);
    final DiagnosticReportJpaRepository reports = mock(DiagnosticReportJpaRepository.class);
    final VisitDiagnosticPort visits = mock(VisitDiagnosticPort.class);
    final PatientDiagnosticHistoryPort patients = mock(PatientDiagnosticHistoryPort.class);
    final ImagingExecution request = mock(ImagingExecution.class);
    final DiagnosticViewerAuthorizationServiceImpl service;

    @SuppressWarnings("unchecked")
    DiagnosticViewerAuthorizationServiceTest() {
        ObjectProvider<VisitDiagnosticPort> vp = mock(ObjectProvider.class);
        ObjectProvider<PatientDiagnosticHistoryPort> pp = mock(ObjectProvider.class);
        when(vp.getIfAvailable()).thenReturn(visits);
        when(pp.getIfAvailable()).thenReturn(patients);
        service = new DiagnosticViewerAuthorizationServiceImpl(requests, reports, vp, pp, mock(ObjectProvider.class));
        when(requests.findByStudyRef("study")).thenReturn(Optional.of(request));
        when(request.orderId()).thenReturn(requestId);
        when(request.visitId()).thenReturn(visit);
        when(request.patientId()).thenReturn(patient);
        when(request.attemptId()).thenReturn(attemptId);
        when(request.status()).thenReturn(ExecutionStatus.REVIEWED);
    }

    void history(UUID doctor, UUID examination, UUID linkedPatient) {
        when(visits.authorizeImagingHistory(visit, actor)).thenReturn(
                new VisitHistoryAuthorization(visit, linkedPatient, doctor, examination, "CLOSED", true));
    }

    @Test void supersededStudyCannotAuthorizeOrReachHistory() {
        when(requests.findByStudyRef("study")).thenReturn(Optional.empty());
        assertFalse(service.authorize(actor, AccountRole.DOCTOR, "study", "DOCTOR_REVIEW").allowed());
        verifyNoInteractions(visits, patients, reports);
    }

    @Test void doctorRequiresTheirOwnExaminationAndCanonicalPatient() {
        history(actor, UUID.randomUUID(), patient);
        assertTrue(service.authorize(actor, AccountRole.DOCTOR, "study", "DOCTOR_REVIEW").allowed());
        history(UUID.randomUUID(), UUID.randomUUID(), patient);
        assertFalse(service.authorize(actor, AccountRole.DOCTOR, "study", "DOCTOR_REVIEW").allowed());
        history(actor, null, patient);
        assertFalse(service.authorize(actor, AccountRole.DOCTOR, "study", "DOCTOR_REVIEW").allowed());
        history(actor, UUID.randomUUID(), UUID.randomUUID());
        assertFalse(service.authorize(actor, AccountRole.DOCTOR, "study", "DOCTOR_REVIEW").allowed());
    }

    @Test void patientRequiresReleasedCompletedFindingsAndCurrentDependentAuthority() {
        history(null, null, patient);
        when(patients.authorizePatientHistory(actor, patient)).thenReturn(new PatientHistoryAuthorization(patient, "DEPENDENT", true));
        var report = mock(DiagnosticReportJpaEntity.class);
        when(report.getId()).thenReturn(reportId);
        when(report.getReportId()).thenReturn(reportId);
        when(report.getFindingsText()).thenReturn("finding");
        when(report.getValidationStatus()).thenReturn(DiagnosticReportValidationStatus.VALIDATED);
        when(report.getState()).thenReturn(DiagnosticFindingsVersionState.COMPLETED);
        when(report.getStudyRef()).thenReturn("study");
        when(report.getVisitId()).thenReturn(visit);
        when(reports.findFirstByRequestIdAndReleasedAtIsNotNullOrderByReportVersionDesc(requestId)).thenReturn(Optional.of(report));
        assertTrue(service.authorize(actor, AccountRole.PATIENT, "study", "PATIENT_VIEW").allowed());
        verify(reports).findFirstByRequestIdAndReleasedAtIsNotNullOrderByReportVersionDesc(requestId);
        when(report.getState()).thenReturn(DiagnosticFindingsVersionState.DRAFT);
        assertFalse(service.authorize(actor, AccountRole.PATIENT, "study", "PATIENT_VIEW").allowed());
        when(report.getState()).thenReturn(DiagnosticFindingsVersionState.COMPLETED);
        when(patients.authorizePatientHistory(actor, patient)).thenReturn(new PatientHistoryAuthorization(patient, "DEPENDENT", false));
        assertFalse(service.authorize(actor, AccountRole.PATIENT, "study", "PATIENT_VIEW").allowed());
    }

    @Test void unreleasedReportAndMismatchedPurposeFailClosed() {
        history(null, null, patient);
        when(patients.authorizePatientHistory(actor, patient)).thenReturn(new PatientHistoryAuthorization(patient, "OWN", true));
        when(reports.findFirstByRequestIdAndReleasedAtIsNotNullOrderByReportVersionDesc(requestId)).thenReturn(Optional.empty());
        assertFalse(service.authorize(actor, AccountRole.PATIENT, "study", "PATIENT_VIEW").allowed());
        assertFalse(service.authorize(actor, AccountRole.RECEPTIONIST, "study", "PATIENT_VIEW").allowed());
        assertFalse(service.authorize(actor, AccountRole.DOCTOR, "study", "PATIENT_VIEW").allowed());
    }

    @Test void patientOpensTheImagesOfAnIncompleteVisitWithoutFindings() {
        when(request.studyRef()).thenReturn("study");
        when(visits.authorizeImagingHistory(visit, actor)).thenReturn(
                new VisitHistoryAuthorization(visit, patient, null, null, "INCOMPLETE", true));
        when(patients.authorizePatientHistory(actor, patient)).thenReturn(new PatientHistoryAuthorization(patient, "SELF", true));
        assertTrue(service.authorize(actor, AccountRole.PATIENT, "study", "PATIENT_VIEW").allowed());
        verifyNoInteractions(reports);
        // An open visit still needs released findings.
        when(visits.authorizeImagingHistory(visit, actor)).thenReturn(
                new VisitHistoryAuthorization(visit, patient, null, null, "OPEN", true));
        assertFalse(service.authorize(actor, AccountRole.PATIENT, "study", "PATIENT_VIEW").allowed());
        // The study must actually exist: an Incomplete visit does not open an order without a result.
        when(visits.authorizeImagingHistory(visit, actor)).thenReturn(
                new VisitHistoryAuthorization(visit, patient, null, null, "INCOMPLETE", true));
        when(request.status()).thenReturn(ExecutionStatus.PENDING);
        assertFalse(service.authorize(actor, AccountRole.PATIENT, "study", "PATIENT_VIEW").allowed());
    }

    @Test void releasedFindingsWithoutAPdfReportAreEnoughForAClosedVisit() {
        history(null, null, patient);
        when(patients.authorizePatientHistory(actor, patient)).thenReturn(new PatientHistoryAuthorization(patient, "SELF", true));
        var report = mock(DiagnosticReportJpaEntity.class);
        when(report.getId()).thenReturn(reportId);
        when(report.getReportId()).thenReturn(null);
        when(report.getFindingsText()).thenReturn("finding");
        when(report.getState()).thenReturn(DiagnosticFindingsVersionState.COMPLETED);
        when(report.getStudyRef()).thenReturn("study");
        when(report.getVisitId()).thenReturn(visit);
        when(reports.findFirstByRequestIdAndReleasedAtIsNotNullOrderByReportVersionDesc(requestId)).thenReturn(Optional.of(report));
        assertTrue(service.authorize(actor, AccountRole.PATIENT, "study", "PATIENT_VIEW").allowed());
    }
}
