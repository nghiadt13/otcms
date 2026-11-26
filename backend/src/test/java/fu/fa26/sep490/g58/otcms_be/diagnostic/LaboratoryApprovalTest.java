package fu.fa26.sep490.g58.otcms_be.diagnostic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.LaboratoryResultValue;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticReportJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticFindingsVersionState;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LaboratoryApprovalTest {
    private final UUID order = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();
    private final UUID sample = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-11-17T12:00:00Z");

    private DiagnosticReportJpaEntity draft(String unit) {
        return DiagnosticReportJpaEntity.laboratoryDraft(
                order,
                UUID.randomUUID(),
                sample,
                actor,
                List.of(new LaboratoryResultValue("CRP", "2", unit, "0–5", null, false)),
                now);
    }

    @Test
    void rejectsMissingUnitWhenApproving() {
        DiagnosticReportJpaEntity incomplete = draft("");

        assertThrows(IllegalStateException.class, () -> incomplete.approveLaboratoryReport(actor, now));
    }

    @Test
    void approvedResultBecomesImmutable() {
        DiagnosticReportJpaEntity result = draft("mg/L");

        result.approveLaboratoryReport(actor, now);

        assertEquals(DiagnosticFindingsVersionState.COMPLETED, result.getState());
        assertThrows(IllegalStateException.class,
                () -> result.reviseLaboratoryDraft(sample, actor, List.of(), now));
    }
}
