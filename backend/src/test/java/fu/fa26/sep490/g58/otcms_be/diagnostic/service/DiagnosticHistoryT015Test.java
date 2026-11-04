package fu.fa26.sep490.g58.otcms_be.diagnostic.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.VisitHistoryAuthorization;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.PatientHistoryAuthorization;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.PatientDiagnosticHistoryPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.VisitDiagnosticPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticHistoryPage;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticHistoryAccessAuditJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticFindingsVersionJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticReportJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticRequestJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticHistoryScope;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticRequestStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticFindingsVersionJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticHistoryAccessAuditJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticReportJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticRequestJpaRepository;
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
    private final DiagnosticRequestJpaRepository requests = mock(DiagnosticRequestJpaRepository.class);
    private final DiagnosticFindingsVersionJpaRepository findings = mock(DiagnosticFindingsVersionJpaRepository.class);
    private final DiagnosticReportJpaRepository reports = mock(DiagnosticReportJpaRepository.class);
    private final DiagnosticHistoryAccessAuditJpaRepository historyAudits =
            mock(DiagnosticHistoryAccessAuditJpaRepository.class);
    private final ObjectProvider<VisitDiagnosticPort> visitPorts = mock(ObjectProvider.class);
    private final ObjectProvider<PatientDiagnosticHistoryPort> patientPorts = mock(ObjectProvider.class);
    private final VisitDiagnosticPort visitPort = mock(VisitDiagnosticPort.class);
    private final PatientDiagnosticHistoryPort patientPort = mock(PatientDiagnosticHistoryPort.class);
    private final DiagnosticHistoryServiceImpl service = new DiagnosticHistoryServiceImpl(
            requests, findings, reports, historyAudits, visitPorts, patientPorts);

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
        when(requests.findAllByVisitIdAndPatientIdOrderByCreatedAtDescIdDesc(eq(visitId), eq(patientId), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        DiagnosticHistoryPage result = service.getImagingHistory(actor, null, visitId, 0, 20);

        assertEquals(0, result.totalItems());
        verify(requests).findAllByVisitIdAndPatientIdOrderByCreatedAtDescIdDesc(eq(visitId), eq(patientId), any());
        ArgumentCaptor<DiagnosticHistoryAccessAuditJpaEntity> audit =
                ArgumentCaptor.forClass(DiagnosticHistoryAccessAuditJpaEntity.class);
        verify(historyAudits).save(audit.capture());
        assertEquals(actorId, audit.getValue().getActorAccountId());
        assertEquals(DiagnosticHistoryScope.VISIT, audit.getValue().getScopeType());
        assertEquals(visitId, audit.getValue().getScopeId());
        assertEquals(0, audit.getValue().getReturnedCount());
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
        when(requests.findAllByPatientIdOrderByCreatedAtDescIdDesc(eq(patientId), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        DiagnosticHistoryPage result = service.getImagingHistory(actor, patientId, null, 0, 20);

        assertEquals(0, result.totalItems());
        verify(patientPort).authorizePatientHistory(actorId, patientId);
        ArgumentCaptor<DiagnosticHistoryAccessAuditJpaEntity> audit =
                ArgumentCaptor.forClass(DiagnosticHistoryAccessAuditJpaEntity.class);
        verify(historyAudits).save(audit.capture());
        assertEquals(DiagnosticHistoryScope.PATIENT, audit.getValue().getScopeType());
        assertEquals(patientId, audit.getValue().getScopeId());
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
        when(requests.findAllByVisitIdAndPatientIdOrderByCreatedAtDescIdDesc(eq(visitId), eq(patientId), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        service.getImagingHistory(actor, null, visitId, 0, 20);

        verify(requests).findAllByVisitIdAndPatientIdOrderByCreatedAtDescIdDesc(eq(visitId), eq(patientId), any());
    }

    @Test
    void historyRejectsMissingOrDualScopeAndInvalidPageBounds() {
        DiagnosticActor actor = DiagnosticActor.user(UUID.randomUUID(), AccountRole.PATIENT);

        assertThrows(IllegalArgumentException.class, () -> service.getImagingHistory(actor, null, null, 0, 20));
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
        DiagnosticRequestJpaEntity request = new DiagnosticRequestJpaEntity(requestId, visitId, patientId, actorId,
                serviceId, roomId, "MRI", "BRAIN", "Headache", DiagnosticRequestStatus.REVIEWED,
                null, null, UUID.randomUUID());
        request.setCreatedAt(createdAt);
        DiagnosticFindingsVersionJpaEntity version = new DiagnosticFindingsVersionJpaEntity(UUID.randomUUID(),
                requestId, visitId, "study-123", 2, "No acute findings", actorId, createdAt,
                fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticFindingsVersionState.COMPLETED,
                reportId, createdAt);
        DiagnosticReportJpaEntity report = new DiagnosticReportJpaEntity(reportId, requestId, visitId,
                "study-123", 2, "private/object/key", "a".repeat(64),
                fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticReportValidationStatus.VALIDATED,
                actorId, createdAt, createdAt);
        when(visitPort.authorizeImagingHistory(visitId, actorId)).thenReturn(
                new VisitHistoryAuthorization(visitId, patientId, actorId, UUID.randomUUID(), "CLOSED", true));
        when(requests.findAllByVisitIdAndPatientIdOrderByCreatedAtDescIdDesc(eq(visitId), eq(patientId), any()))
                .thenReturn(new PageImpl<>(List.of(request), PageRequest.of(0, 20), 1));
        when(findings.findFirstByRequestIdAndStateOrderByVersionNumberDesc(eq(requestId), any()))
                .thenReturn(Optional.of(version));
        when(reports.findFirstByRequestIdAndReleasedAtIsNotNullOrderByReportVersionDesc(requestId))
                .thenReturn(Optional.of(report));

        DiagnosticHistoryPage result = service.getImagingHistory(actor, null, visitId, 0, 20);

        assertEquals("study-123", result.items().getFirst().studyRef());
        assertEquals("No acute findings", result.items().getFirst().findingsText());
        assertEquals(reportId, result.items().getFirst().reportId());
        assertEquals(2, result.items().getFirst().findingsVersion());
    }
}
