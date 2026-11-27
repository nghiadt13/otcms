package fu.fa26.sep490.g58.otcms_be.diagnostic.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditEntry;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditLogWriter;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort.ImagingExecution;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort.ExecutionStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.VisitHistoryAuthorization;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.PatientHistoryAuthorization;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.PatientDiagnosticHistoryPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.VisitDiagnosticPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticHistoryPage;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticReportJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticHistoryScope;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticRequestStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticReportJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl.DiagnosticHistoryServiceImpl;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class DiagnosticHistoryT015Test {
    private final ClinicalServiceOrderExecutionPort requests = mock(ClinicalServiceOrderExecutionPort.class);
    private final DiagnosticReportJpaRepository reports = mock(DiagnosticReportJpaRepository.class);
    private final AuditLogWriter historyAudits = mock(AuditLogWriter.class);
    private final ObjectProvider<VisitDiagnosticPort> visitPorts = mock(ObjectProvider.class);
    private final ObjectProvider<PatientDiagnosticHistoryPort> patientPorts = mock(ObjectProvider.class);
    private final VisitDiagnosticPort visitPort = mock(VisitDiagnosticPort.class);
    private final PatientDiagnosticHistoryPort patientPort = mock(PatientDiagnosticHistoryPort.class);
    private final DiagnosticHistoryServiceImpl service = new DiagnosticHistoryServiceImpl(
            requests, reports, historyAudits, visitPorts, patientPorts);

    @BeforeEach
    void configurePorts() {
        when(visitPorts.getIfAvailable()).thenReturn(visitPort);
        when(patientPorts.getIfAvailable()).thenReturn(patientPort);
    }

    @Test
    void doctorHistoryUsesDurableVisitEvidenceEvenForClosedVisit() {
        UUID actorId = UUID.randomUUID();
        UUID visitId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID evidenceId = UUID.randomUUID();
        DiagnosticActor actor = DiagnosticActor.user(actorId, AccountRole.DOCTOR);
        when(visitPort.authorizeImagingHistory(visitId, actorId)).thenReturn(
                new VisitHistoryAuthorization(visitId, patientId, actorId, evidenceId, "CLOSED", true));
        when(requests.page(eq(visitId), eq(patientId), eq(null), eq(null), eq(null), eq(false), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        DiagnosticHistoryPage result = service.getImagingHistory(actor, null, visitId, 0, 20);

        assertEquals(0, result.totalItems());
        verify(requests).page(eq(visitId), eq(patientId), eq(null), eq(null), eq(null), eq(false), any());
        ArgumentCaptor<AuditEntry> audit = ArgumentCaptor.forClass(AuditEntry.class);
        verify(historyAudits).record(audit.capture());
        assertEquals(actorId, audit.getValue().actorAccountId());
        assertEquals("VISIT", audit.getValue().recordType());
        assertEquals(visitId, audit.getValue().entityId());
        assertEquals(0, audit.getValue().metadata().get("returnedCount"));
    }

    @Test
    void doctorWithoutExaminationEvidenceGetsUnavailableAndDoesNotQueryHistory() {
        UUID actorId = UUID.randomUUID();
        UUID visitId = UUID.randomUUID();
        DiagnosticActor actor = DiagnosticActor.user(actorId, AccountRole.DOCTOR);
        when(visitPort.authorizeImagingHistory(visitId, actorId)).thenReturn(
                new VisitHistoryAuthorization(visitId, UUID.randomUUID(), actorId, null, "CLOSED", true));

        DiagnosticOperationException exception = assertThrows(DiagnosticOperationException.class,
                () -> service.getImagingHistory(actor, null, visitId, 0, 20));

        assertEquals(DiagnosticOperationOutcome.RECORD_NOT_FOUND, exception.outcome());
        verifyNoInteractions(requests, historyAudits);
    }

    @Test
    void technicianCannotReadClinicalHistory() {
        DiagnosticActor technician = DiagnosticActor.user(UUID.randomUUID(), AccountRole.IMAGING_TECHNICIAN);

        assertThrows(DiagnosticOperationException.class,
                () -> service.getImagingHistory(technician, UUID.randomUUID(), null, 0, 20));
        verifyNoInteractions(requests, visitPort, patientPort, historyAudits);
    }

    @Test
    void patientDependentHistoryRequiresCurrentPatientOwnerAuthorization() {
        UUID actorId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        DiagnosticActor actor = DiagnosticActor.user(actorId, AccountRole.PATIENT);
        when(patientPort.authorizePatientHistory(actorId, patientId))
                .thenReturn(new PatientHistoryAuthorization(patientId, "APPROVED_DEPENDENT", true));
        when(requests.page(eq(null), eq(patientId), eq(null), eq(null), eq(null), eq(false), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        DiagnosticHistoryPage result = service.getImagingHistory(actor, patientId, null, 0, 20);

        assertEquals(0, result.totalItems());
        verify(patientPort).authorizePatientHistory(actorId, patientId);
        ArgumentCaptor<AuditEntry> audit = ArgumentCaptor.forClass(AuditEntry.class);
        verify(historyAudits).record(audit.capture());
        assertEquals("PATIENT", audit.getValue().recordType());
        assertEquals(patientId, audit.getValue().entityId());
    }

    @Test
    void patientVisitHistoryRequiresVisitPatientMatchAndPatientAuthority() {
        UUID actorId = UUID.randomUUID();
        UUID visitId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        DiagnosticActor actor = DiagnosticActor.user(actorId, AccountRole.PATIENT);
        when(visitPort.authorizeImagingHistory(visitId, actorId)).thenReturn(
                new VisitHistoryAuthorization(visitId, patientId, UUID.randomUUID(), null, "CLOSED", true));
        when(patientPort.authorizePatientHistory(actorId, patientId))
                .thenReturn(new PatientHistoryAuthorization(patientId, "SELF", true));
        when(requests.page(eq(visitId), eq(patientId), eq(null), eq(null), eq(null), eq(false), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        service.getImagingHistory(actor, null, visitId, 0, 20);

        verify(requests).page(eq(visitId), eq(patientId), eq(null), eq(null), eq(null), eq(false), any());
    }

    @Test
    void historyRejectsMissingOrDualScopeAndInvalidPageBounds() {
        DiagnosticActor actor = DiagnosticActor.user(UUID.randomUUID(), AccountRole.PATIENT);

        // A patient with no filter lists their own studies (UC-51); only other roles need an explicit scope.
        DiagnosticActor doctor = DiagnosticActor.user(UUID.randomUUID(), AccountRole.DOCTOR);
        assertThrows(IllegalArgumentException.class, () -> service.getImagingHistory(doctor, null, null, 0, 20));
        assertThrows(IllegalArgumentException.class,
                () -> service.getImagingHistory(actor, UUID.randomUUID(), UUID.randomUUID(), 0, 20));
        assertThrows(IllegalArgumentException.class,
                () -> service.getImagingHistory(actor, UUID.randomUUID(), null, 0, 101));
        verifyNoInteractions(requests);
    }

    @Test
    void historyIncludesOnlyReleasedVersionWithMatchingReport() {
        UUID actorId = UUID.randomUUID();
        UUID visitId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        UUID studyId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-10-01T10:00:00Z");
        DiagnosticActor actor = DiagnosticActor.user(actorId, AccountRole.DOCTOR);
        ImagingExecution request = mock(ImagingExecution.class);
        when(request.orderId()).thenReturn(requestId); when(request.visitId()).thenReturn(visitId);
        when(request.status()).thenReturn(ExecutionStatus.REVIEWED); when(request.studyRef()).thenReturn("study-123");
        DiagnosticReportJpaEntity report = mock(DiagnosticReportJpaEntity.class);
        when(report.getRequestId()).thenReturn(requestId); when(report.getVisitId()).thenReturn(visitId);
        when(report.getStudyRef()).thenReturn("study-123"); when(report.getVersionNumber()).thenReturn(2);
        when(report.getFindingsText()).thenReturn("No acute findings"); when(report.getReportId()).thenReturn(reportId);
        when(report.getCompletedAt()).thenReturn(createdAt); when(report.getReleasedAt()).thenReturn(createdAt);
        when(report.getValidationStatus()).thenReturn(fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticReportValidationStatus.VALIDATED);
        when(visitPort.authorizeImagingHistory(visitId, actorId)).thenReturn(
                new VisitHistoryAuthorization(visitId, patientId, actorId, UUID.randomUUID(), "CLOSED", true));
        when(requests.page(eq(visitId), eq(patientId), eq(null), eq(null), eq(null), eq(false), any()))
                .thenReturn(new PageImpl<>(List.of(request), PageRequest.of(0, 20), 1));
        when(reports.findFirstByRequestIdAndStateOrderByReportVersionDesc(eq(requestId), any()))
                .thenReturn(Optional.of(report));

        DiagnosticHistoryPage result = service.getImagingHistory(actor, null, visitId, 0, 20);

        assertEquals("study-123", result.items().getFirst().studyRef());
        assertEquals("No acute findings", result.items().getFirst().findingsText());
        assertEquals(reportId, result.items().getFirst().reportId());
        assertEquals(2, result.items().getFirst().findingsVersion());

        when(report.getValidationStatus()).thenReturn(fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticReportValidationStatus.INVALID);
        // A treating doctor keeps the linked study ref (to open images) of a REVIEWED request, but an invalid report
        // exposes neither findings nor report id.
        var invalidReportItem = service.getImagingHistory(actor, null, visitId, 0, 20).items().getFirst();
        assertEquals("study-123", invalidReportItem.studyRef());
        org.junit.jupiter.api.Assertions.assertNull(invalidReportItem.findingsText());
        org.junit.jupiter.api.Assertions.assertNull(invalidReportItem.reportId());
        when(report.getValidationStatus()).thenReturn(fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticReportValidationStatus.VALIDATED);
        when(report.getReleasedAt()).thenReturn(null);
        org.junit.jupiter.api.Assertions.assertNull(service.getImagingHistory(actor, null, visitId, 0, 20).items().getFirst().findingsText());
        when(report.getReleasedAt()).thenReturn(createdAt);
        when(request.status()).thenReturn(ExecutionStatus.CANCELLED);
        org.junit.jupiter.api.Assertions.assertNull(service.getImagingHistory(actor, null, visitId, 0, 20).items().getFirst().reportId());
    }

    @Test
    void treatingDoctorHistoryRestoresMatchingImagingDraft() {
        UUID actorId = UUID.randomUUID();
        UUID visitId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        DiagnosticActor actor = DiagnosticActor.user(actorId, AccountRole.DOCTOR);
        ImagingExecution request = mock(ImagingExecution.class);
        when(request.orderId()).thenReturn(orderId);
        when(request.visitId()).thenReturn(visitId);
        when(request.status()).thenReturn(ExecutionStatus.RESULT_AVAILABLE);
        when(request.studyRef()).thenReturn("study-draft-123");
        when(visitPort.authorizeImagingHistory(visitId, actorId)).thenReturn(
                new VisitHistoryAuthorization(visitId, patientId, actorId, UUID.randomUUID(), "IN_PROGRESS", true));
        when(requests.page(eq(visitId), eq(patientId), eq(null), eq(null), eq(null), eq(false), any()))
                .thenReturn(new PageImpl<>(List.of(request), PageRequest.of(0, 20), 1));
        DiagnosticReportJpaEntity draft = new DiagnosticReportJpaEntity(UUID.randomUUID(), orderId, visitId,
                "study-draft-123", 1, "Draft findings", actorId, Instant.parse("2026-11-27T00:00:00Z"));
        when(reports.findFirstByRequestIdAndStateOrderByReportVersionDesc(orderId,
                fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticFindingsVersionState.DRAFT))
                .thenReturn(Optional.of(draft));

        var item = service.getImagingHistory(actor, null, visitId, 0, 20).items().getFirst();

        assertEquals("Draft findings", item.findingsText());
        assertEquals("study-draft-123", item.studyRef());
        org.junit.jupiter.api.Assertions.assertNull(item.findingsVersion());
        org.junit.jupiter.api.Assertions.assertNull(item.reportId());
        verify(reports).findFirstByRequestIdAndStateOrderByReportVersionDesc(orderId,
                fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticFindingsVersionState.DRAFT);
        verify(reports, org.mockito.Mockito.never()).findFirstByRequestIdAndStateOrderByReportVersionDesc(orderId,
                fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticFindingsVersionState.COMPLETED);
    }

    @Test
    void patientHistoryDoesNotReadOrExposeDraftFindings() {
        UUID actorId = UUID.randomUUID();
        UUID visitId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        DiagnosticActor actor = DiagnosticActor.user(actorId, AccountRole.PATIENT);
        ImagingExecution request = mock(ImagingExecution.class);
        when(request.orderId()).thenReturn(orderId);
        when(request.visitId()).thenReturn(visitId);
        when(request.status()).thenReturn(ExecutionStatus.RESULT_AVAILABLE);
        when(request.studyRef()).thenReturn("study-draft-123");
        when(visitPort.authorizeImagingHistory(visitId, actorId)).thenReturn(
                new VisitHistoryAuthorization(visitId, patientId, null, UUID.randomUUID(), "CLOSED", true));
        when(patientPort.authorizePatientHistory(actorId, patientId))
                .thenReturn(new PatientHistoryAuthorization(patientId, "SELF", true));
        when(requests.page(eq(visitId), eq(patientId), eq(null), eq(null), eq(null), eq(false), any()))
                .thenReturn(new PageImpl<>(List.of(request), PageRequest.of(0, 20), 1));
        when(reports.findFirstByRequestIdAndStateOrderByReportVersionDesc(orderId,
                fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticFindingsVersionState.COMPLETED))
                .thenReturn(Optional.empty());

        var item = service.getImagingHistory(actor, null, visitId, 0, 20).items().getFirst();

        org.junit.jupiter.api.Assertions.assertNull(item.findingsText());
        org.junit.jupiter.api.Assertions.assertNull(item.reportId());
        verify(reports, org.mockito.Mockito.never()).findFirstByRequestIdAndStateOrderByReportVersionDesc(orderId,
                fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticFindingsVersionState.DRAFT);
    }

    @Test
    void patientHistoryWithoutFilterUsesOwnProfile() {
        UUID actorId = UUID.randomUUID();
        UUID ownPatientId = UUID.randomUUID();
        DiagnosticActor actor = DiagnosticActor.user(actorId, AccountRole.PATIENT);
        when(patientPort.ownPatientId(actorId)).thenReturn(ownPatientId);
        when(patientPort.authorizePatientHistory(actorId, ownPatientId))
                .thenReturn(new PatientHistoryAuthorization(ownPatientId, "SELF", true));
        when(requests.page(eq(null), eq(ownPatientId), eq(null), eq(null), eq(null), eq(false), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        DiagnosticHistoryPage result = service.getImagingHistory(actor, null, null, 0, 20);

        assertEquals(0, result.totalItems());
        verify(patientPort).authorizePatientHistory(actorId, ownPatientId);
        verify(requests).page(eq(null), eq(ownPatientId), eq(null), eq(null), eq(null), eq(false), any());
        ArgumentCaptor<AuditEntry> audit = ArgumentCaptor.forClass(AuditEntry.class);
        verify(historyAudits).record(audit.capture());
        assertEquals("PATIENT", audit.getValue().recordType());
        assertEquals(ownPatientId, audit.getValue().entityId());
    }

    @Test
    void patientWithoutOwnProfileGetsEmptyHistoryWithoutQueryOrAudit() {
        UUID actorId = UUID.randomUUID();
        DiagnosticActor actor = DiagnosticActor.user(actorId, AccountRole.PATIENT);
        when(patientPort.ownPatientId(actorId)).thenReturn(null);

        DiagnosticHistoryPage result = service.getImagingHistory(actor, null, null, 0, 20);

        assertEquals(List.of(), result.items());
        assertEquals(0, result.totalItems());
        verify(patientPort, never()).authorizePatientHistory(any(), any());
        verifyNoInteractions(requests, historyAudits);
    }

    @Test
    void nonPatientHistoryWithoutScopeIsStillRejectedWithoutProfileFallback() {
        DiagnosticActor doctor = DiagnosticActor.user(UUID.randomUUID(), AccountRole.DOCTOR);

        assertThrows(IllegalArgumentException.class, () -> service.getImagingHistory(doctor, null, null, 0, 20));
        verifyNoInteractions(patientPort, requests, historyAudits);
    }

    @Test
    void patientDependentHistoryOutsideScopeIsUnauthorizedRecord() {
        UUID actorId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        DiagnosticActor actor = DiagnosticActor.user(actorId, AccountRole.PATIENT);
        when(patientPort.authorizePatientHistory(actorId, patientId))
                .thenReturn(new PatientHistoryAuthorization(patientId, null, false));

        DiagnosticOperationException exception = assertThrows(DiagnosticOperationException.class,
                () -> service.getImagingHistory(actor, patientId, null, 0, 20));

        assertEquals(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD, exception.outcome());
        verifyNoInteractions(requests, historyAudits);
    }

    @Test
    void patientVisitHistoryOutsideScopeStaysRecordNotFound() {
        UUID actorId = UUID.randomUUID();
        UUID visitId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        DiagnosticActor actor = DiagnosticActor.user(actorId, AccountRole.PATIENT);
        when(visitPort.authorizeImagingHistory(visitId, actorId)).thenReturn(
                new VisitHistoryAuthorization(visitId, patientId, UUID.randomUUID(), null, "CLOSED", true));
        when(patientPort.authorizePatientHistory(actorId, patientId))
                .thenReturn(new PatientHistoryAuthorization(patientId, null, false));

        DiagnosticOperationException exception = assertThrows(DiagnosticOperationException.class,
                () -> service.getImagingHistory(actor, null, visitId, 0, 20));

        assertEquals(DiagnosticOperationOutcome.RECORD_NOT_FOUND, exception.outcome());
        verifyNoInteractions(requests, historyAudits);
    }
}
