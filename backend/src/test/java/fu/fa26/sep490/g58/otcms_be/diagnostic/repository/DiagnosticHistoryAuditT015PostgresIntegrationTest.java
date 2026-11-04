package fu.fa26.sep490.g58.otcms_be.diagnostic.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticHistoryAccessAuditJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticHistoryScope;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@LocalPostgresTest
@Transactional
class DiagnosticHistoryAuditT015PostgresIntegrationTest {
    @Autowired DiagnosticHistoryAccessAuditJpaRepository audits;
    @Autowired JdbcTemplate jdbc;

    @Test
    void persistsPatientScopedAuditForAnEmptyHistoryPage() {
        UUID actorId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        DiagnosticHistoryAccessAuditJpaEntity saved = audits.saveAndFlush(
                new DiagnosticHistoryAccessAuditJpaEntity(UUID.randomUUID(), actorId,
                        DiagnosticHistoryScope.PATIENT, patientId, 0, 20, 0));

        DiagnosticHistoryAccessAuditJpaEntity reloaded = audits.findById(saved.getId()).orElseThrow();

        assertEquals(actorId, reloaded.getActorAccountId());
        assertEquals(DiagnosticHistoryScope.PATIENT, reloaded.getScopeType());
        assertEquals(patientId, reloaded.getScopeId());
        assertEquals(0, reloaded.getReturnedCount());
        assertNotNull(jdbc.queryForObject("select recorded_at from diagnostic_history_access_audits where id = ?",
                java.time.Instant.class, saved.getId()));
    }
}
