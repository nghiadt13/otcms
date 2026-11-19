package fu.fa26.sep490.g58.otcms_be.diagnostic.repository;

import static org.junit.jupiter.api.Assertions.*;

import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                    .locations("classpath:db/migration").target(MigrationVersion.LATEST).load().migrate();
            UUID order = UUID.randomUUID(), visit = UUID.randomUUID(), patient = UUID.randomUUID();
            UUID doctor = UUID.randomUUID(), attempt = UUID.randomUUID(), firstUse = UUID.randomUUID();
            createOrder(schema, order, visit, patient, doctor);

            assertThrows(DataAccessException.class, () -> jdbc.update("update " + schema
                    + ".imaging_order_executions set execution_status='RESULT_AVAILABLE' where service_order_id=?", order));
            assertEquals(1, jdbc.update("update " + schema + ".imaging_order_executions set "
                    + "execution_status='IN_PROGRESS', attempt_id=?, first_use_event_id=?, first_use_type='ACQUISITION_STARTED', "
                    + "first_use_source_id='t011', first_use_at=?, version=version+1 where service_order_id=?",
                    attempt, firstUse, Timestamp.from(NOW), order));
            UUID competing = UUID.randomUUID();
            createOrder(schema, competing, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
            assertThrows(DataAccessException.class, () -> jdbc.update("update " + schema + ".imaging_order_executions "
                    + "set execution_status='IN_PROGRESS', attempt_id=?, first_use_event_id=? where service_order_id=?",
                    attempt, UUID.randomUUID(), competing));
            assertThrows(DataAccessException.class, () -> jdbc.update("update " + schema + ".imaging_order_executions "
                    + "set execution_status='IN_PROGRESS', attempt_id=?, first_use_event_id=? where service_order_id=?",
                    UUID.randomUUID(), firstUse, competing));

            int[] results = raceStudyLink(schema, order, attempt);
            assertEquals(1, results[0] + results[1]);
            assertEquals(0, Math.min(results[0], results[1]));
            assertThrows(DataAccessException.class, () -> jdbc.update("update " + schema
                    + ".imaging_order_executions set execution_status='IN_PROGRESS' where service_order_id=?", order));
            String study = jdbc.queryForObject("select study_ref from " + schema + ".imaging_order_executions where service_order_id=?",
                    String.class, order);
            assertEquals(firstUse, jdbc.queryForObject("select first_use_event_id from " + schema
                    + ".imaging_order_executions where service_order_id=?", UUID.class, order));

            assertThrows(DataAccessException.class, () -> insertPendingReport(schema, UUID.randomUUID(), order,
                    UUID.randomUUID(), study, doctor));
            assertThrows(DataAccessException.class, () -> insertPendingReport(schema, UUID.randomUUID(), order,
                    visit, "wrong-study", doctor));
            UUID report = UUID.randomUUID();
            insertPendingReport(schema, report, order, visit, study, doctor);
            assertThrows(DataAccessException.class, () -> complete(schema, report));
            jdbc.update("update " + schema + ".diagnostic_reports set validation_status='VALIDATED', validated_at=? where id=?",
                    Timestamp.from(NOW), report);
            assertReportInvalidationSerializesWithFindingsCompletion(schema, report);
            assertEquals("INVALID", jdbc.queryForObject("select validation_status from " + schema
                    + ".diagnostic_reports where id=?", String.class, report));
            jdbc.update("update " + schema + ".diagnostic_reports set validation_status='VALIDATED', validated_at=? where id=?",
                    Timestamp.from(NOW), report);
            assertEquals(1, complete(schema, report));
            assertThrows(DataAccessException.class, () -> jdbc.update("update " + schema
                    + ".diagnostic_reports set findings_text='changed' where id=?", report));
            assertThrows(DataAccessException.class, () -> jdbc.update("update " + schema
                    + ".diagnostic_reports set validation_status='INVALID' where id=?", report));
        } finally {
            jdbc.execute("drop schema if exists " + schema + " cascade");
        }
    }

    private void createOrder(String schema, UUID id, UUID visit, UUID patient, UUID doctor) {
        UUID round = UUID.randomUUID();
        jdbc.update("insert into " + schema + ".clinical_ordering_rounds (id,visit_id,round_number,ordered_by_doctor_id,ordered_at,created_at) "
                + "values (?,?,1,?,?,?)", round, visit, doctor, Timestamp.from(NOW), Timestamp.from(NOW));
        jdbc.update("insert into " + schema + ".clinical_service_orders (id,order_code,ordering_round_id,"
                + "visit_id,patient_id,service_id,service_name_snapshot,service_type,"
                + "requesting_doctor_id,clinical_reason,body_area,price_snapshot,order_status,payment_status,created_at,updated_at,"
                + "room_id) values (?,?,?,?,?,?,'MRI','IMAGING',?,'clinical reason','KNEE',1,'ORDERED','PAID',?,?,?)",
                id, id.toString().replace("-", ""), round, visit, patient, UUID.randomUUID(), doctor,
                Timestamp.from(NOW), Timestamp.from(NOW), UUID.randomUUID());
        jdbc.update("insert into " + schema + ".imaging_order_executions (service_order_id,imaging_type,"
                + "ordering_doctor_account_id,execution_status,created_at,updated_at) values (?,'MRI',?,'PENDING',?,?)",
                id, doctor, Timestamp.from(NOW), Timestamp.from(NOW));
    }

    private int[] raceStudyLink(String schema, UUID order, UUID attempt) throws Exception {
        var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> a = executor.submit(() -> tryLink(schema, order, attempt, "study-one", ready, start));
            Future<Integer> b = executor.submit(() -> tryLink(schema, order, attempt, "study-two", ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            return new int[] {a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)};
        } finally { start.countDown(); executor.shutdownNow(); }
    }

    private int tryLink(String schema, UUID order, UUID attempt, String study, CountDownLatch ready, CountDownLatch start) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            ready.countDown(); await(start);
            return jdbc.update("update " + schema + ".imaging_order_executions set study_ref=?, "
                    + "execution_status='RESULT_AVAILABLE', version=version+1 "
                    + "where service_order_id=? and attempt_id=? and study_ref is null and execution_status='IN_PROGRESS' "
                    + "and version=1", study, order, attempt);
        });
    }

    private void insertPendingReport(String schema, UUID report, UUID order, UUID visit, String study, UUID doctor) {
        jdbc.update("insert into " + schema + ".diagnostic_reports (id,report_id,service_order_id,visit_id,study_ref,"
                + "version_number,report_storage_ref,report_sha256,validation_status,uploaded_by_doctor_id,uploaded_at) "
                + "values (?,?,?,?,?,1,?,?,'PENDING',?,?)", report, report, order, visit, study,
                "private/t011/" + report, "b".repeat(64), doctor, Timestamp.from(NOW));
    }

    private int complete(String schema, UUID report) {
        return jdbc.update("update " + schema + ".diagnostic_reports set state='COMPLETED', "
                + "findings_text='T011 completed fixture', completed_at=? where id=?", Timestamp.from(NOW), report);
    }

    private void assertReportInvalidationSerializesWithFindingsCompletion(String schema, UUID report) throws Exception {
        var locked = new CountDownLatch(1); var release = new CountDownLatch(1); var started = new CountDownLatch(1);
        var pid = new java.util.concurrent.atomic.AtomicInteger(); var executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> invalidation = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                jdbc.queryForObject("select id from " + schema + ".diagnostic_reports where id=? for update", UUID.class, report);
                locked.countDown(); await(release);
                jdbc.update("update " + schema + ".diagnostic_reports set validation_status='INVALID' where id=?", report);
                return null;
            }));
            assertTrue(locked.await(10, TimeUnit.SECONDS));
            Future<?> completion = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                pid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class)); started.countDown(); complete(schema, report); return null;
            }));
            assertTrue(started.await(10, TimeUnit.SECONDS));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < deadline && !Boolean.TRUE.equals(jdbc.queryForObject(
                    "select exists (select 1 from pg_stat_activity where pid=? and wait_event_type='Lock')", Boolean.class, pid.get()))) Thread.sleep(10);
            assertTrue(System.nanoTime() < deadline, "completion must wait for the report row lock");
            release.countDown(); invalidation.get(10, TimeUnit.SECONDS);
            assertThrows(java.util.concurrent.ExecutionException.class, () -> completion.get(10, TimeUnit.SECONDS));
            assertEquals(0, jdbc.queryForObject("select count(*) from " + schema
                    + ".diagnostic_reports where id=? and state='COMPLETED'", Integer.class, report));
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    private void await(CountDownLatch latch) {
        try { if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("timed out waiting for concurrent operation"); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
    }
}
