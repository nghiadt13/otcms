package fu.fa26.sep490.g58.otcms_be.diagnostic.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@LocalPostgresTest
class DiagnosticPersistenceT011PostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-04T08:00:00Z");

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void enforcesRequestScopedUniquenessCurrentStudyImmutabilityAndConcurrentStudyLink() throws Exception {
        String schema = "t011_diagnostic_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("create schema " + schema);
        try {
            flyway(schema).migrate();

            UUID requestId = UUID.randomUUID();
            UUID visitId = UUID.randomUUID();
            UUID patientId = UUID.randomUUID();
            UUID doctorId = UUID.randomUUID();
            createRequest(schema, requestId, visitId, patientId, doctorId);

            insertIdempotency(schema, UUID.randomUUID(), doctorId, visitId, "CREATE_REQUEST", "idem-1");
            assertThrows(DataAccessException.class, () -> insertIdempotency(
                    schema, UUID.randomUUID(), doctorId, visitId, "CREATE_REQUEST", "idem-1"));
            insertFirstUseOutbox(schema, UUID.randomUUID(), UUID.randomUUID(), requestId, visitId);
            assertThrows(DataAccessException.class, () -> insertFirstUseOutbox(
                    schema, UUID.randomUUID(), UUID.randomUUID(), requestId, visitId));

            insertAttempt(schema, UUID.randomUUID(), requestId, 1, UUID.randomUUID(), null);
            UUID savedAttemptId = jdbc.queryForObject("select id from " + schema
                    + ".diagnostic_attempts where request_id = ?", UUID.class, requestId);
            int activated = jdbc.update("update " + schema + ".diagnostic_requests "
                    + "set status = 'IN_PROGRESS', current_attempt_id = ?, version = version + 1 where id = ?",
                    savedAttemptId, requestId);
            assertEquals(1, activated);
            assertThrows(DataAccessException.class, () -> jdbc.update("update " + schema
                    + ".diagnostic_requests set status = 'RESULT_AVAILABLE' where id = ?", requestId));
            UUID firstUseEventId = jdbc.queryForObject("select first_use_event_id from " + schema
                    + ".diagnostic_attempts where request_id = ?", UUID.class, requestId);

            UUID competingRequest = UUID.randomUUID();
            createRequest(schema, competingRequest, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
            UUID competingAttempt = UUID.randomUUID();
            insertAttempt(schema, competingAttempt, competingRequest, 1, UUID.randomUUID(), null);
            assertThrows(DataAccessException.class, () -> jdbc.update("update " + schema
                    + ".diagnostic_requests set status = 'IN_PROGRESS', current_attempt_id = ? where id = ?",
                    competingAttempt, requestId));

            int[] concurrentResults = raceStudyLink(schema, requestId, savedAttemptId);
            assertEquals(1, concurrentResults[0] + concurrentResults[1]);
            assertEquals(0, Math.min(concurrentResults[0], concurrentResults[1]));
            assertThrows(DataAccessException.class, () -> jdbc.update("update " + schema
                    + ".diagnostic_requests set status = 'IN_PROGRESS' where id = ?", requestId));

            assertThrows(DataAccessException.class, () -> insertAttempt(
                    schema, UUID.randomUUID(), requestId, 2, UUID.randomUUID(), "another-study"));
            assertEquals(1, jdbc.queryForObject("select count(*) from " + schema
                    + ".diagnostic_attempts where request_id = ? and study_ref is not null", Integer.class, requestId));

            String studyRef = jdbc.queryForObject("select study_ref from " + schema
                    + ".diagnostic_attempts where id = ?", String.class, savedAttemptId);
            assertThrows(DataAccessException.class, () -> insertValidatedReport(
                    schema, UUID.randomUUID(), requestId, UUID.randomUUID(), studyRef, doctorId));
            assertThrows(DataAccessException.class, () -> insertValidatedReport(
                    schema, UUID.randomUUID(), requestId, visitId, "wrong-study", doctorId));
            UUID reportId = UUID.randomUUID();
            insertPendingReport(schema, reportId, requestId, visitId, studyRef, doctorId);
            assertThrows(DataAccessException.class, () -> insertCompletedFindings(
                    schema, UUID.randomUUID(), requestId, visitId, studyRef, reportId, doctorId));
            jdbc.update("update " + schema + ".diagnostic_reports set validation_status = 'VALIDATED', "
                    + "validated_at = ? where id = ?", Timestamp.from(NOW), reportId);
            assertReportInvalidationSerializesWithFindingsCompletion(schema, reportId, requestId, visitId,
                    studyRef, doctorId);
            assertEquals("INVALID", jdbc.queryForObject("select validation_status from " + schema
                    + ".diagnostic_reports where id = ?", String.class, reportId));
            jdbc.update("update " + schema + ".diagnostic_reports set validation_status = 'VALIDATED', "
                    + "validated_at = ? where id = ?", Timestamp.from(NOW), reportId);
            insertCompletedFindings(schema, UUID.randomUUID(), requestId, visitId, studyRef, reportId, doctorId);
            assertThrows(DataAccessException.class, () -> jdbc.update("update " + schema
                    + ".diagnostic_findings_versions set findings_text = 'changed' where request_id = ?",
                    requestId));
            assertThrows(DataAccessException.class, () -> jdbc.update("update " + schema
                    + ".diagnostic_reports set validation_status = 'INVALID' where id = ?", reportId));

            assertEquals(1, jdbc.queryForObject("select count(*) from " + schema
                    + ".diagnostic_attempts where request_id = ? and first_use_event_id = ?",
                    Integer.class, requestId, firstUseEventId));
        } finally {
            jdbc.execute("drop schema if exists " + schema + " cascade");
        }
    }

    private void assertReportInvalidationSerializesWithFindingsCompletion(String schema, UUID reportId,
            UUID requestId, UUID visitId, String studyRef, UUID doctorId) throws Exception {
        CountDownLatch reportLocked = new CountDownLatch(1);
        CountDownLatch releaseInvalidation = new CountDownLatch(1);
        CountDownLatch completionStarted = new CountDownLatch(1);
        AtomicInteger completionBackendPid = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> invalidation = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                jdbc.queryForObject("select id from " + schema + ".diagnostic_reports where id = ? for update",
                        UUID.class, reportId);
                reportLocked.countDown();
                await(releaseInvalidation);
                jdbc.update("update " + schema
                        + ".diagnostic_reports set validation_status = 'INVALID' where id = ?", reportId);
                return null;
            }));
            assertTrue(reportLocked.await(10, TimeUnit.SECONDS), "report row should be locked before completion starts");

            Future<?> completion = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                completionBackendPid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                completionStarted.countDown();
                insertCompletedFindings(schema, UUID.randomUUID(), requestId, visitId, studyRef, reportId, doctorId);
                return null;
            }));
            assertTrue(completionStarted.await(10, TimeUnit.SECONDS), "completion transaction should start");
            awaitDatabaseLockWait(completionBackendPid.get());
            releaseInvalidation.countDown();
            invalidation.get(10, TimeUnit.SECONDS);
            assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> completion.get(10, TimeUnit.SECONDS));
            assertEquals(0, jdbc.queryForObject("select count(*) from " + schema
                    + ".diagnostic_findings_versions where request_id = ? and state = 'COMPLETED'",
                    Integer.class, requestId));
        } finally {
            releaseInvalidation.countDown();
            executor.shutdownNow();
        }
    }

    private void awaitDatabaseLockWait(int backendPid) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Boolean waiting = jdbc.queryForObject("select exists (select 1 from pg_stat_activity "
                    + "where pid = ? and wait_event_type = 'Lock')", Boolean.class, backendPid);
            if (Boolean.TRUE.equals(waiting)) return;
            Thread.sleep(10);
        }
        throw new AssertionError("findings completion did not wait for the report row lock");
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("concurrent report operation did not receive its release signal");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrent report operation was interrupted", exception);
        }
    }

    private Flyway flyway(String schema) {
        return Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target(MigrationVersion.LATEST).load();
    }

    private void createRequest(String schema, UUID requestId, UUID visitId, UUID patientId, UUID doctorId) {
        jdbc.update("insert into " + schema + ".diagnostic_requests "
                        + "(id, visit_id, patient_id, ordering_doctor_id, service_id, room_id, imaging_type, status) "
                        + "values (?, ?, ?, ?, ?, ?, 'MRI', 'PENDING')",
                requestId, visitId, patientId, doctorId, UUID.randomUUID(), UUID.randomUUID());
    }

    private void insertAttempt(String schema, UUID attemptId, UUID requestId, int attemptNumber,
            UUID firstUseEventId, String studyRef) {
        jdbc.update("insert into " + schema + ".diagnostic_attempts "
                        + "(id, request_id, attempt_number, first_use_event_id, first_use_type, source_id, "
                        + "actor_account_id, occurred_at, study_ref) "
                        + "values (?, ?, ?, ?, 'ACQUISITION_STARTED', 't011-test-source', ?, ?, ?)",
                attemptId, requestId, attemptNumber, firstUseEventId, UUID.randomUUID(),
                Timestamp.from(NOW), studyRef);
    }

    private void insertIdempotency(String schema, UUID id, UUID actorId, UUID visitId,
            String operation, String key) {
        jdbc.update("insert into " + schema + ".diagnostic_idempotency_records "
                        + "(id, actor_account_id, visit_id, operation, idempotency_key, payload_fingerprint, "
                        + "result_outcome, result_payload) values (?, ?, ?, ?, ?, ?, 'ALLOWED', '{}'::jsonb)",
                id, actorId, visitId, operation, key, "a".repeat(64));
    }

    private void insertFirstUseOutbox(String schema, UUID id, UUID eventId, UUID requestId, UUID visitId) {
        jdbc.update("insert into " + schema + ".diagnostic_outbox "
                        + "(id, event_id, request_id, visit_id, event_type, payload) "
                        + "values (?, ?, ?, ?, 'FIRST_USE_BILLING_OBLIGATION', '{}'::jsonb)",
                id, eventId, requestId, visitId);
    }

    private int[] raceStudyLink(String schema, UUID requestId, UUID attemptId) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> tryLink(schema, requestId, attemptId,
                    "study-one", ready, start));
            Future<Integer> second = executor.submit(() -> tryLink(schema, requestId, attemptId,
                    "study-two", ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS), "both study-link workers should be ready");
            start.countDown();
            return new int[] {first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)};
        } finally {
            executor.shutdownNow();
        }
    }

    private int tryLink(String schema, UUID requestId, UUID attemptId, String studyRef,
            CountDownLatch ready, CountDownLatch start) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            ready.countDown();
            try {
                start.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("study-link race was interrupted", exception);
            }
            int claimed = jdbc.update("update " + schema + ".diagnostic_attempts set study_ref = ? "
                    + "where id = ? and study_ref is null", studyRef, attemptId);
            if (claimed != 1) return 0;
            int transitioned = jdbc.update("update " + schema + ".diagnostic_requests "
                            + "set status = 'RESULT_AVAILABLE', version = version + 1 "
                            + "where id = ? and status = 'IN_PROGRESS' and version = 1",
                    requestId);
            if (transitioned != 1) throw new IllegalStateException("study link lost optimistic concurrency race");
            return transitioned;
        });
    }

    private void insertPendingReport(String schema, UUID reportId, UUID requestId, UUID visitId,
            String studyRef, UUID doctorId) {
        jdbc.update("insert into " + schema + ".diagnostic_reports "
                        + "(id, request_id, visit_id, study_ref, report_version, storage_ref, content_sha256, "
                        + "uploaded_by_doctor_id) values (?, ?, ?, ?, 1, ?, ?, ?)",
                reportId, requestId, visitId, studyRef, "private/t011/" + reportId,
                "b".repeat(64), doctorId);
    }

    private void insertValidatedReport(String schema, UUID reportId, UUID requestId, UUID visitId,
            String studyRef, UUID doctorId) {
        insertPendingReport(schema, reportId, requestId, visitId, studyRef, doctorId);
        jdbc.update("update " + schema + ".diagnostic_reports set validation_status = 'VALIDATED', "
                + "validated_at = ? where id = ?", Timestamp.from(NOW), reportId);
    }

    private void insertCompletedFindings(String schema, UUID findingsId, UUID requestId, UUID visitId,
            String studyRef, UUID reportId, UUID doctorId) {
        jdbc.update("insert into " + schema + ".diagnostic_findings_versions "
                        + "(id, request_id, visit_id, study_ref, version_number, findings_text, "
                        + "recorded_by_doctor_id, state, required_pdf_report_id, completed_at) "
                        + "values (?, ?, ?, ?, 1, 'T011 completed fixture', ?, 'COMPLETED', ?, ?)",
                findingsId, requestId, visitId, studyRef, doctorId, reportId, Timestamp.from(NOW));
    }
}
