package fu.fa26.sep490.g58.otcms_be.diagnostic.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.LaboratoryResultValue;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticReportJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticFindingsVersionState;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticReportJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl.LaboratoryDraftServiceImpl;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-48: a laboratory result is released by the technician's approval alone; no report file is involved. */
class LaboratoryDraftServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");
    private final UUID orderId = UUID.randomUUID();
    private final UUID visitId = UUID.randomUUID();
    private final UUID sampleId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();

    private DiagnosticReportJpaEntity newDraft(Boolean abnormal) {
        var value = new LaboratoryResultValue("Glucose", "5.2", "mmol/L", "3.9-6.1", "", abnormal);
        return DiagnosticReportJpaEntity.laboratoryDraft(orderId, visitId, sampleId, accountId, List.of(value), NOW);
    }

    @Test
    void approvalReleasesCompleteValuesWithoutReport() {
        var draft = newDraft(false);
        draft.approveLaboratoryReport(accountId, NOW);
        assertEquals(DiagnosticFindingsVersionState.COMPLETED, draft.getState());
        assertEquals(NOW, draft.getReleasedAt());
        assertEquals(NOW, draft.getLaboratoryConfirmedAt());
        assertNull(draft.getReportId());
    }

    @Test
    void approvalRejectsValueWithoutAbnormalFlag() {
        var draft = newDraft(null);
        assertThrows(IllegalStateException.class, () -> draft.approveLaboratoryReport(accountId, NOW));
        assertEquals(DiagnosticFindingsVersionState.DRAFT, draft.getState());
    }

    @Test
    void issuedValuesFollowApprovalNotFile() {
        var repository = mock(DiagnosticReportJpaRepository.class);
        var service = new LaboratoryDraftServiceImpl(repository);
        var draft = newDraft(true);
        when(repository.findLaboratoryVersions(orderId)).thenReturn(List.of(draft));
        assertTrue(service.issued(orderId).isEmpty());
        draft.approveLaboratoryReport(accountId, NOW);
        assertEquals(1, service.issued(orderId).size());
    }
}
