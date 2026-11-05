package fu.fa26.sep490.g58.otcms_be.diagnostic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticHistoryAccessAuditJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl.DiagnosticPatientHistoryQueryServiceImpl;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DiagnosticPatientHistoryQueryServiceImplTest {
    final DiagnosticRequestJpaRepository requests = mock(DiagnosticRequestJpaRepository.class);
    final DiagnosticHistoryAccessAuditJpaRepository audits = mock(DiagnosticHistoryAccessAuditJpaRepository.class);
    final DiagnosticPatientHistoryQueryServiceImpl service = new DiagnosticPatientHistoryQueryServiceImpl(requests, audits);
    final UUID patient = UUID.randomUUID(), visit = UUID.randomUUID(), currentDoctorAccount = UUID.randomUUID();
    @Test void releasedPatientScopeMapsWithoutHistoricalDoctorRestrictionAndPreservesAudit() {
        var row = mock(DiagnosticRequestJpaRepository.ReleasedHistoryRow.class);
        when(row.getPatientId()).thenReturn(patient); when(row.getVisitId()).thenReturn(visit); when(row.getRequestId()).thenReturn(UUID.randomUUID());
        when(row.getStatus()).thenReturn(DiagnosticRequestStatus.REVIEWED); when(row.getFindingsText()).thenReturn("completed finding");
        when(requests.findReleasedPatientHistory(patient, List.of(visit), List.of(DiagnosticRequestStatus.RESULT_AVAILABLE, DiagnosticRequestStatus.REVIEWED))).thenReturn(List.of(row));
        var result = service.findReleased(patient, List.of(visit), currentDoctorAccount, 0, 20);
        assertEquals("completed finding", result.getFirst().result().findingsText());
        var audit = ArgumentCaptor.forClass(DiagnosticHistoryAccessAuditJpaEntity.class); verify(audits).save(audit.capture());
        assertEquals(currentDoctorAccount, audit.getValue().getActorAccountId()); assertEquals(patient, audit.getValue().getScopeId());
        assertEquals(1, audit.getValue().getReturnedCount());
        verify(requests).findReleasedPatientHistory(patient, List.of(visit), List.of(DiagnosticRequestStatus.RESULT_AVAILABLE, DiagnosticRequestStatus.REVIEWED));
        verifyNoMoreInteractions(requests);
    }
    @Test void emptyVisitPageStillAuditsAndDoesNotQueryDiagnosticTables() {
        assertTrue(service.findReleased(patient, List.of(), currentDoctorAccount, 2, 20).isEmpty());
        verifyNoInteractions(requests); verify(audits).save(any());
    }
    @Test void invalidPatientScopeFailsBeforeAuditAndQuery() {
        assertThrows(IllegalArgumentException.class, () -> service.findReleased(null, List.of(visit), currentDoctorAccount, 0, 20));
        verifyNoInteractions(requests, audits);
    }
}
