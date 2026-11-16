package fu.fa26.sep490.g58.otcms_be.diagnostic.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticPatientHistoryQueryService;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.util.UUID;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@LocalPostgresTest
@Transactional
class DiagnosticHistoryAuditT015PostgresIntegrationTest {
    @Autowired DiagnosticPatientHistoryQueryService history;
    @Autowired JdbcTemplate jdbc;

    @Test
    void persistsPatientScopedAuditForAnEmptyHistoryPage() {
        UUID actorId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        assertEquals(List.of(), history.findReleased(patientId, List.of(), actorId, 0, 20));
        var audit = jdbc.queryForMap("select actor_account_id, record_type, entity_id, occurred_at, "
                + "metadata->>'returnedCount' as returned_count from audit_logs "
                + "where source='diagnostic_history_access_audits' and action='VIEW_HISTORY' and entity_id=?", patientId);
        assertEquals(actorId, audit.get("actor_account_id"));
        assertEquals("PATIENT", audit.get("record_type"));
        assertEquals(patientId, audit.get("entity_id"));
        assertEquals("0", audit.get("returned_count"));
        assertNotNull(audit.get("occurred_at"));
    }
}
