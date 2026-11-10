package fu.fa26.sep490.g58.otcms_be.diagnostic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
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
    final DiagnosticRequestJpaRepository requests = mock(DiagnosticRequestJpaRepository.class);
    final DiagnosticReportJpaRepository reports = mock(DiagnosticReportJpaRepository.class);
    final DiagnosticFindingsVersionJpaRepository findings = mock(DiagnosticFindingsVersionJpaRepository.class);
    final VisitDiagnosticPort visits = mock(VisitDiagnosticPort.class);
    final PatientDiagnosticHistoryPort patients = mock(PatientDiagnosticHistoryPort.class);
    final DiagnosticRequestJpaEntity request = mock(DiagnosticRequestJpaEntity.class);
    final DiagnosticViewerAuthorizationServiceImpl service;

    @SuppressWarnings("unchecked")
    DiagnosticViewerAuthorizationServiceTest() {
        ObjectProvider<VisitDiagnosticPort> vp = mock(ObjectProvider.class);
        ObjectProvider<PatientDiagnosticHistoryPort> pp = mock(ObjectProvider.class);
        when(vp.getIfAvailable()).thenReturn(visits);
        when(pp.getIfAvailable()).thenReturn(patients);
        service = new DiagnosticViewerAuthorizationServiceImpl(requests, reports, findings, vp, pp, mock(ObjectProvider.class));
        when(requests.findByCurrentStudyRef("study")).thenReturn(Optional.of(request));
        when(request.getId()).thenReturn(requestId);
        when(request.getVisitId()).thenReturn(visit);
        when(request.getPatientId()).thenReturn(patient);
        when(request.getCurrentAttemptId()).thenReturn(attemptId);
        when(request.getStatus()).thenReturn(DiagnosticRequestStatus.REVIEWED);
    }

    void history(UUID doctor, UUID examination, UUID linkedPatient) {
        when(visits.authorizeImagingHistory(visit, actor)).thenReturn(
                new VisitHistoryAuthorization(visit, linkedPatient, doctor, examination, "CLOSED", true));
    }

    @Test void supersededStudyCannotAuthorizeOrReachHistory() {
        when(requests.findByCurrentStudyRef("study")).thenReturn(Optional.empty());
        assertFalse(service.authorize(actor, AccountRole.DOCTOR, "study", "DOCTOR_REVIEW").allowed());
        verifyNoInteractions(visits, patients, reports, findings);
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
        when(report.getStudyRef()).thenReturn("study");
        when(report.getVisitId()).thenReturn(visit);
        when(reports.findFirstByRequestIdAndReleasedAtIsNotNullOrderByReportVersionDesc(requestId)).thenReturn(Optional.of(report));
        var version = mock(DiagnosticFindingsVersionJpaEntity.class);
        when(version.getId()).thenReturn(UUID.randomUUID());
        when(version.getState()).thenReturn(DiagnosticFindingsVersionState.COMPLETED);
        when(version.getStudyRef()).thenReturn("study");
        when(version.getRequiredPdfReportId()).thenReturn(reportId);
        when(findings.findByRequestIdAndRequiredPdfReportId(requestId, reportId)).thenReturn(Optional.of(version));
        assertTrue(service.authorize(actor, AccountRole.PATIENT, "study", "PATIENT_VIEW").allowed());
        verify(findings).findByRequestIdAndRequiredPdfReportId(requestId, reportId);
        when(version.getState()).thenReturn(DiagnosticFindingsVersionState.DRAFT);
        assertFalse(service.authorize(actor, AccountRole.PATIENT, "study", "PATIENT_VIEW").allowed());
        when(version.getState()).thenReturn(DiagnosticFindingsVersionState.COMPLETED);
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
        verifyNoInteractions(findings);
    }
}
