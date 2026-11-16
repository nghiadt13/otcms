package fu.fa26.sep490.g58.otcms_be.common.audit;

import static org.junit.jupiter.api.Assertions.*;

import fu.fa26.sep490.g58.otcms_be.common.audit.repository.AuditLogRepository;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@LocalPostgresTest
class AuditLogWriterIntegrationTest {
    @Autowired AuditLogWriter writer;
    @Autowired AuditLogRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void preservesSnapshotTimeActorAndCorrelationAndRedactsNestedJson() {
        String source = source();
        UUID actor = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-11-13T00:00:00Z");
        String correlation = UUID.randomUUID().toString();
        try (var ignored = MDC.putCloseable(CorrelationIdFilter.MDC_KEY, correlation)) {
            tx().executeWithoutResult(status -> writer.record(entry(source).actorAccountId(actor).occurredAt(occurredAt)
                    .beforeState(Map.of("status", "ACTIVE", "password", "synthetic-secret", "password_hash", "synthetic-hash"))
                    .afterState(Map.of("nested", List.of(Map.of("otp", "123456", "nationalId", "synthetic-id",
                                    "otp_code", "123456", "national_id", "synthetic-id")),
                            "enabled", true, "price", 125))
                    .metadata(Map.of("authorization", "Bearer synthetic-token", "safe", "retained"))
                    .clientIp("127.0.0.1").userAgent("phase01-test").identifierHmac("hashed-id").outcome("SUCCESS").build()));
        }
        var row = jdbc.queryForMap("select *, before_state->>'password' masked_password, "
                + "after_state->'nested'->0->>'otp' masked_otp, after_state->'nested'->0->>'nationalId' masked_id, "
                + "before_state->>'password_hash' masked_hash, after_state->'nested'->0->>'otp_code' masked_otp_code, "
                + "after_state->'nested'->0->>'national_id' masked_national_id, "
                + "metadata->>'authorization' masked_auth, after_state->>'enabled' enabled "
                + "from audit_logs where source=?", source);
        assertEquals(actor, row.get("actor_account_id"));
        assertEquals("ACCOUNT", row.get("actor_type"));
        assertEquals(occurredAt, ((java.sql.Timestamp) row.get("occurred_at")).toInstant());
        assertEquals(correlation, row.get("correlation_id"));
        for (String column : List.of("masked_password", "masked_otp", "masked_id", "masked_auth",
                "masked_hash", "masked_otp_code", "masked_national_id")) {
            assertEquals("[REDACTED]", row.get(column));
        }
        assertEquals("true", row.get("enabled"));
        assertFalse(row.get("before_state").toString().contains("synthetic-secret"));
    }

    @Test
    void requiresAnExistingTransactionForBothMethods() {
        var entry = entry(source()).dedupKey(UUID.randomUUID().toString()).build();
        assertThrows(IllegalTransactionStateException.class, () -> writer.record(entry));
        assertThrows(IllegalTransactionStateException.class, () -> writer.recordIfAbsent(entry));
    }

    @Test
    void rollsBackAuditWithBusinessMutation() {
        String source = source();
        UUID actor = UUID.randomUUID();
        tx().executeWithoutResult(status -> {
            jdbc.update("insert into accounts(id, normalized_email, account_type, status, created_at, updated_at) "
                    + "values (?, ?, 'ADMIN', 'ACTIVE', now(), now())", actor, actor + "@phase01.test");
            writer.record(entry(source).actorAccountId(actor).build());
            status.setRollbackOnly();
        });
        assertEquals(0L, count(source));
        assertEquals(0L, jdbc.queryForObject("select count(*) from accounts where id=?", Long.class, actor));
    }

    @Test
    void auditInsertFailureAlsoRollsBackTheBusinessMutation() {
        String source = source();
        UUID actor = UUID.randomUUID();
        assertThrows(DataAccessException.class, () -> tx().executeWithoutResult(status -> {
            jdbc.update("insert into accounts(id, normalized_email, account_type, status, created_at, updated_at) "
                    + "values (?, ?, 'ADMIN', 'ACTIVE', now(), now())", actor, actor + "@phase01.test");
            // A required audit action is absent: the database rejects it and the business transaction rolls back.
            writer.record(AuditEntry.builder(source, "ROOM", null, "ROOM").actorAccountId(actor).build());
        }));
        assertEquals(0L, count(source));
        assertEquals(0L, jdbc.queryForObject("select count(*) from accounts where id=?", Long.class, actor));
    }

    @Test
    void deduplicatesAndRejectsMissingDeduplicationKeys() {
        String source = source();
        var entry = entry(source).dedupKey(UUID.randomUUID().toString()).build();
        tx().executeWithoutResult(status -> {
            assertTrue(writer.recordIfAbsent(entry));
            assertFalse(writer.recordIfAbsent(entry));
            assertThrows(IllegalArgumentException.class, () -> writer.recordIfAbsent(entry(source).build()));
            // The validation exception marks the transaction rollback-only; check valid inserts separately below.
            status.setRollbackOnly();
        });
        tx().executeWithoutResult(status -> {
            assertTrue(writer.recordIfAbsent(entry));
            assertFalse(writer.recordIfAbsent(entry));
            writer.record(entry(source).build());
            writer.record(entry(source).build());
        });
        assertEquals(3L, count(source));
    }

    @Test
    void concurrentDuplicateWritesInsertExactlyOnce() throws Exception {
        String source = source();
        var entry = entry(source).dedupKey(UUID.randomUUID().toString()).build();
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var jobs = List.of(pool.submit(() -> concurrentRecord(entry, ready, start)),
                    pool.submit(() -> concurrentRecord(entry, ready, start)));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            int inserted = 0;
            for (var job : jobs) if (job.get(10, TimeUnit.SECONDS)) inserted++;
            assertEquals(1, inserted);
        }
        assertEquals(1L, count(source));
    }

    @Test
    void updateDeleteAndTruncateAreBlockedByDatabase() {
        String source = source();
        tx().executeWithoutResult(status -> writer.record(entry(source).build()));
        for (String sql : List.of("update audit_logs set action='CHANGED' where source=?",
                "delete from audit_logs where source=?", "truncate audit_logs")) {
            DataAccessException exception = assertThrows(DataAccessException.class, () -> {
                if (sql.contains("?")) jdbc.update(sql, source);
                else jdbc.execute(sql);
            });
            assertInstanceOf(SQLException.class, exception.getMostSpecificCause());
            assertEquals("55000", ((SQLException) exception.getMostSpecificCause()).getSQLState());
        }
        assertEquals(1L, count(source));
    }

    @Test
    void rateLimitCountFiltersOutcomeAndCutoff() {
        String source = source();
        Instant cutoff = Instant.parse("2026-11-13T01:00:00Z");
        tx().executeWithoutResult(status -> {
            for (Instant time : List.of(cutoff.minusSeconds(1), cutoff, cutoff.plusSeconds(1))) {
                writer.record(entry(source).identifierHmac("hash").outcome("ATTEMPT").occurredAt(time).build());
            }
            writer.record(entry(source).identifierHmac("hash").outcome("FAILURE").occurredAt(cutoff.plusSeconds(1)).build());
            writer.record(entry(source).identifierHmac("other").outcome("ATTEMPT").occurredAt(cutoff.plusSeconds(1)).build());
        });
        assertEquals(1L, repository.countBySourceAndActionAndOutcomeAndIdentifierHmacAndOccurredAtAfter(
                source, "CREATE_ROOM", "ATTEMPT", "hash", cutoff));
    }

    private boolean concurrentRecord(AuditEntry entry, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent write did not start");
        return Boolean.TRUE.equals(tx().execute(status -> writer.recordIfAbsent(entry)));
    }

    private TransactionTemplate tx() { return new TransactionTemplate(transactionManager); }
    private String source() { return "phase01_" + UUID.randomUUID(); }
    private long count(String source) {
        return jdbc.queryForObject("select count(*) from audit_logs where source=?", Long.class, source);
    }
    private AuditEntry.AuditEntryBuilder entry(String source) {
        return AuditEntry.builder(source, "ROOM", "CREATE_ROOM", "ROOM").visibleInActivityLog(false);
    }
}
