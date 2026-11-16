package fu.fa26.sep490.g58.otcms_be.diagnostic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort.ImagingExecution;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort.ExecutionStatus;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditEntry;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditLogWriter;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticReportJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticFindingsVersionState;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticReportValidationStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticReportJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl.DiagnosticPatientHistoryQueryServiceImpl;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DiagnosticPatientHistoryQueryServiceImplTest {
    final ClinicalServiceOrderExecutionPort requests = mock(ClinicalServiceOrderExecutionPort.class);
    final DiagnosticReportJpaRepository reports = mock(DiagnosticReportJpaRepository.class);
    final AuditLogWriter audits = mock(AuditLogWriter.class);
    final DiagnosticPatientHistoryQueryServiceImpl service = new DiagnosticPatientHistoryQueryServiceImpl(requests, reports, audits);
    final UUID patient = UUID.randomUUID(), visit = UUID.randomUUID(), currentDoctorAccount = UUID.randomUUID();

    @Test void releasedPatientScopeMapsWithoutHistoricalDoctorRestrictionAndPreservesAudit() {
        UUID id = UUID.randomUUID();
        var row = mock(ImagingExecution.class);
        when(row.patientId()).thenReturn(patient); when(row.visitId()).thenReturn(visit); when(row.orderId()).thenReturn(id);
        when(row.status()).thenReturn(ExecutionStatus.REVIEWED); when(row.studyRef()).thenReturn("study");
        var report = mock(DiagnosticReportJpaEntity.class);
        when(report.getRequestId()).thenReturn(id); when(report.getVisitId()).thenReturn(visit);
        when(report.getStudyRef()).thenReturn("study"); when(report.getReportId()).thenReturn(UUID.randomUUID());
        when(report.getCompletedAt()).thenReturn(Instant.now()); when(report.getReleasedAt()).thenReturn(Instant.now());
        when(report.getValidationStatus()).thenReturn(DiagnosticReportValidationStatus.VALIDATED);
        when(report.getFindingsText()).thenReturn("completed finding");
        when(requests.findAllByPatient(patient)).thenReturn(List.of(row));
        when(reports.findAllByRequestIdInAndState(List.of(id), DiagnosticFindingsVersionState.COMPLETED)).thenReturn(List.of(report));
        var result = service.findReleased(patient, List.of(visit), currentDoctorAccount, 0, 20);
        assertEquals("completed finding", result.getFirst().result().findingsText());
        var audit = ArgumentCaptor.forClass(AuditEntry.class); verify(audits).record(audit.capture());
        assertEquals(currentDoctorAccount, audit.getValue().actorAccountId()); assertEquals(patient, audit.getValue().entityId());
        assertEquals(1, ((Map<?, ?>) audit.getValue().metadata()).get("returnedCount"));
        verify(requests).findAllByPatient(patient);
        verifyNoMoreInteractions(requests);
    }

    @Test void emptyVisitPageStillAuditsAndDoesNotQueryDiagnosticTables() {
        assertTrue(service.findReleased(patient, List.of(), currentDoctorAccount, 2, 20).isEmpty());
        verifyNoInteractions(requests, reports); verify(audits).record(any());
    }

    @Test void invalidPatientScopeFailsBeforeAuditAndQuery() {
        assertThrows(IllegalArgumentException.class, () -> service.findReleased(null, List.of(visit), currentDoctorAccount, 0, 20));
        verifyNoInteractions(requests, reports, audits);
    }
}
