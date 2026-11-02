package fu.fa26.sep490.g58.otcms_be.visit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.WalkInReceptionQueueEntryResponse;
import fu.fa26.sep490.g58.otcms_be.visit.service.CreateWalkInReceptionQueueEntryCommand;
import fu.fa26.sep490.g58.otcms_be.visit.service.WalkInReceptionQueueService;
import fu.fa26.sep490.g58.otcms_be.visit.repository.WalkInReceptionQueueEntryJpaRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@LocalPostgresTest
class WalkInReceptionQueueT045PostgresIntegrationTest {
    private static final String PREFIX = "t045-";

    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired WalkInReceptionQueueService queue;
    @Autowired WalkInReceptionQueueEntryJpaRepository queueEntries;
    @Autowired PlatformTransactionManager transactionManager;

    private UUID receptionistAccountId;
    private UUID patientAccountId;
    private UUID patientProfileId;
    private UUID visitId;
    private Instant arrivedAt;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        receptionistAccountId = account(AccountType.RECEPTIONIST, suffix + "-receptionist");
        patientAccountId = account(AccountType.PATIENT, suffix + "-patient");
        patientProfileId = UUID.randomUUID();
        jdbc.update("insert into patient_profiles (id, account_id, full_name, phone_canonical, phone_owner_type, "
                        + "date_of_birth, gender) values (?, ?, ?, ?, 'PATIENT', '1990-01-01', 'OTHER')",
                patientProfileId, patientAccountId, "T045 Patient", "+848" + suffix.replace("-", "").substring(0, 9));
        visitId = UUID.randomUUID();
        Instant candidateArrival = Instant.now().minusSeconds(60);
        arrivedAt = Instant.ofEpochSecond(candidateArrival.getEpochSecond(), candidateArrival.getNano() / 1_000 * 1_000);
        jdbc.update("insert into visits (id, patient_profile_id, source, arrived_at, checked_in_at, "
                        + "created_by_account_id) values (?, ?, 'WALK_IN', ?, ?, ?)",
                visitId, patientProfileId, Timestamp.from(arrivedAt), Timestamp.from(arrivedAt), receptionistAccountId);
    }

    @AfterEach
    void cleanUp(TestInfo testInfo) {
        SecurityContextHolder.clearContext();
        if (testInfo.getTestMethod().orElseThrow().getName().equals("rejectsQueueRowsForScheduledVisits")) return;
        if (visitId != null) {
            jdbc.execute("alter table walk_in_reception_queue_history disable trigger tr_walk_in_reception_queue_history_immutable");
            jdbc.execute("alter table walk_in_reception_queue_history disable trigger tr_walk_in_reception_queue_history_no_truncate");
            jdbc.update("delete from walk_in_reception_queue_history where queue_entry_id in "
                    + "(select id from walk_in_reception_queue_entries where visit_id = ?)", visitId);
            jdbc.update("delete from walk_in_reception_queue_entries where visit_id = ?", visitId);
            jdbc.update("delete from visits where id = ?", visitId);
            jdbc.execute("alter table walk_in_reception_queue_history enable trigger tr_walk_in_reception_queue_history_immutable");
            jdbc.execute("alter table walk_in_reception_queue_history enable trigger tr_walk_in_reception_queue_history_no_truncate");
        }
        if (patientProfileId != null) jdbc.update("delete from patient_profiles where id = ?", patientProfileId);
        if (patientAccountId != null) jdbc.update("delete from accounts where id = ?", patientAccountId);
        if (receptionistAccountId != null) jdbc.update("delete from accounts where id = ?", receptionistAccountId);
    }

    @Test
    void createsQueueEntryAndImmutableInitialHistoryAndAllowsANewEntryAfterExit() {
        var first = queue.createWaitingEntry(command(visitId, AccountRole.RECEPTIONIST));
        assertEquals(visitId, first.visitId());
        assertEquals(arrivedAt, first.arrivedAt());
        assertEquals("WAITING", first.status());
        assertEquals(1, jdbc.queryForObject("select count(*) from walk_in_reception_queue_history "
                + "where queue_entry_id = ? and from_status is null and to_status = 'WAITING' "
                + "and actor_account_id = ?", Integer.class, first.id(), receptionistAccountId));
        VisitOperationException duplicate = assertThrows(VisitOperationException.class,
                () -> queue.createWaitingEntry(command(visitId, AccountRole.RECEPTIONIST)));
        assertEquals("QUEUE_ENTRY_ALREADY_ACTIVE", duplicate.code());

        Instant leftAt = Instant.now();
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "update walk_in_reception_queue_entries set status = 'LEFT_RECEPTION', left_at = ?, "
                        + "exit_reason = 'CANCELLED' where id = ?", Timestamp.from(leftAt), first.id()));
        assertEquals(1, jdbc.queryForObject("select count(*) from walk_in_reception_queue_history "
                + "where queue_entry_id = ?", Integer.class, first.id()));
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            queueEntries.setAuditActor(receptionistAccountId.toString());
            queueEntries.setAuditCorrelation(PREFIX + "exit");
            jdbc.update("update walk_in_reception_queue_entries set status = 'LEFT_RECEPTION', left_at = ?, "
                    + "exit_reason = 'CANCELLED' where id = ?", Timestamp.from(leftAt), first.id());
        });
        assertEquals(2, jdbc.queryForObject("select count(*) from walk_in_reception_queue_history "
                + "where queue_entry_id = ?", Integer.class, first.id()));

        var second = queue.createWaitingEntry(command(visitId, AccountRole.RECEPTIONIST));
        assertTrue(!first.id().equals(second.id()));
        assertEquals(2, jdbc.queryForObject("select count(*) from walk_in_reception_queue_entries where visit_id = ?",
                Integer.class, visitId));
        assertEquals(1, jdbc.queryForObject("select count(*) from walk_in_reception_queue_entries "
                + "where visit_id = ? and status = 'WAITING'", Integer.class, visitId));
    }

    @Test
    void rejectsUnauthorizedRoleAndProtectsVisitSourceArrivalAndHistory() {
        SecurityContextHolder.clearContext();
        assertThrows(VisitOperationException.class, () -> queue.createWaitingEntry(
                new CreateWalkInReceptionQueueEntryCommand(visitId,
                        new AuthenticatedAccount(receptionistAccountId, AccountRole.RECEPTIONIST, UUID.randomUUID()),
                        PREFIX + "missing-principal")));
        assertThrows(VisitOperationException.class,
                () -> queue.createWaitingEntry(command(visitId, AccountRole.PATIENT)));
        AuthenticatedAccount actual = new AuthenticatedAccount(receptionistAccountId, AccountRole.RECEPTIONIST,
                UUID.randomUUID());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actual, null, java.util.List.of()));
        assertThrows(VisitOperationException.class, () -> queue.createWaitingEntry(
                new CreateWalkInReceptionQueueEntryCommand(visitId,
                        new AuthenticatedAccount(patientAccountId, AccountRole.RECEPTIONIST, UUID.randomUUID()),
                        PREFIX + "forged")));
        jdbc.update("update accounts set status = 'DISABLED' where id = ?", receptionistAccountId);
        assertThrows(VisitOperationException.class,
                () -> queue.createWaitingEntry(command(visitId, AccountRole.RECEPTIONIST)));
        jdbc.update("update accounts set status = 'ACTIVE' where id = ?", receptionistAccountId);
        assertEquals(0, jdbc.queryForObject("select count(*) from walk_in_reception_queue_entries where visit_id = ?",
                Integer.class, visitId));

        var entry = queue.createWaitingEntry(command(visitId, AccountRole.RECEPTIONIST));
        assertThrows(DataAccessException.class, () -> jdbc.update("update visits set source = 'SCHEDULED' where id = ?", visitId));
        assertThrows(DataAccessException.class, () -> jdbc.update("update visits set arrived_at = ? where id = ?",
                Timestamp.from(arrivedAt.plusSeconds(1)), visitId));
        assertThrows(DataAccessException.class,
                () -> jdbc.update("update walk_in_reception_queue_history set reason = 'CANCELLED' where queue_entry_id = ?", entry.id()));
        assertThrows(DataAccessException.class,
                () -> jdbc.update("delete from walk_in_reception_queue_history where queue_entry_id = ?", entry.id()));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "insert into walk_in_reception_queue_history (queue_entry_id, to_status) values (?, 'WAITING')",
                entry.id()));
        assertThrows(DataAccessException.class, () -> jdbc.execute("truncate walk_in_reception_queue_history"));
        assertThrows(DataAccessException.class,
                () -> jdbc.update("delete from walk_in_reception_queue_entries where id = ?", entry.id()));
        assertThrows(DataAccessException.class, () -> jdbc.update("delete from visits where id = ?", visitId));
    }

    @Test
    void rejectsInvalidInitialAndExitStatesAndProvidesFifoIndexes() {
        assertThrows(DataAccessException.class, () -> jdbc.update("insert into walk_in_reception_queue_entries "
                        + "(visit_id, arrived_at, status, left_at, exit_reason) values (?, ?, 'LEFT_RECEPTION', now(), 'CANCELLED')",
                visitId, Timestamp.from(arrivedAt)));
        assertThrows(DataAccessException.class, () -> jdbc.update("insert into walk_in_reception_queue_entries "
                        + "(visit_id, arrived_at, status, left_at, exit_reason) values (?, ?, 'LEFT_RECEPTION', now(), 'INVALID')",
                visitId, Timestamp.from(arrivedAt)));
        assertEquals(1, jdbc.queryForObject("select count(*) from pg_indexes where schemaname = 'public' "
                + "and indexname = 'ix_walk_in_reception_queue_fifo'", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from pg_indexes where schemaname = 'public' "
                + "and indexname = 'uk_walk_in_reception_queue_active_visit'", Integer.class));
    }

    @Test
    void serializesQueueCreationAgainstVisitClosure() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch visitClosedButUncommitted = new CountDownLatch(1);
        CountDownLatch releaseCloser = new CountDownLatch(1);
        CountDownLatch queueCallStarted = new CountDownLatch(1);
        Future<?> closer = executor.submit(() -> new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> {
                    jdbc.update("update visits set status = 'CLOSED' where id = ?", visitId);
                    visitClosedButUncommitted.countDown();
                    try {
                        if (!releaseCloser.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("timed out waiting to release Visit closure");
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Visit closure interrupted", exception);
                    }
                }));
        try {
            assertTrue(visitClosedButUncommitted.await(5, TimeUnit.SECONDS));
            Future<WalkInReceptionQueueEntryResponse> queueAttempt = executor.submit(() -> {
                CreateWalkInReceptionQueueEntryCommand request = command(visitId, AccountRole.RECEPTIONIST);
                queueCallStarted.countDown();
                return queue.createWaitingEntry(request);
            });
            assertTrue(queueCallStarted.await(5, TimeUnit.SECONDS));
            Thread.sleep(150);
            assertTrue(!queueAttempt.isDone(), "queue creation must wait for the Visit row lock");

            releaseCloser.countDown();
            closer.get(5, TimeUnit.SECONDS);
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> queueAttempt.get(5, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof VisitOperationException);
            assertEquals("VISIT_NOT_QUEUEABLE", ((VisitOperationException) failure.getCause()).code());
            assertEquals(0, jdbc.queryForObject("select count(*) from walk_in_reception_queue_entries "
                    + "where visit_id = ?", Integer.class, visitId));
        } finally {
            releaseCloser.countDown();
            SecurityContextHolder.clearContext();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    @Transactional
    void rejectsQueueRowsForScheduledVisits() {
        UUID doctorAccountId = account(AccountType.DOCTOR, UUID.randomUUID() + "-doctor");
        UUID doctorProfileId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        UUID scheduledVisitId = UUID.randomUUID();
        Instant checkInAt = Instant.now();
        jdbc.update("insert into doctor_profiles (id, account_id, full_name) values (?, ?, 'T045 Doctor')",
                doctorProfileId, doctorAccountId);
        jdbc.update("insert into clinic_services (id, name, duration_minutes, buffer_minutes) "
                + "values (?, 'T045 Service', 30, 0)", serviceId);
        jdbc.update("insert into doctor_services (doctor_profile_id, service_id, active) values (?, ?, true)",
                doctorProfileId, serviceId);
        jdbc.update("insert into visits (id, patient_profile_id, source, arrived_at, checked_in_at, "
                        + "created_by_account_id) values (?, ?, 'WALK_IN', ?, ?, ?)",
                scheduledVisitId, patientProfileId, Timestamp.from(checkInAt), Timestamp.from(checkInAt), receptionistAccountId);
        jdbc.update("insert into appointments (id, patient_profile_id, service_id, doctor_profile_id, status, "
                        + "booking_source, purpose, start_time, end_time, duration_minutes, buffer_minutes, visit_id, "
                        + "created_by_account_id) values (?, ?, ?, ?, 'CHECKED_IN', 'RECEPTIONIST_ASSISTED', 'T045 test', "
                        + "?, ?, 30, 0, ?, ?)",
                appointmentId, patientProfileId, serviceId, doctorProfileId, Timestamp.from(checkInAt),
                Timestamp.from(checkInAt.plusSeconds(1800)), scheduledVisitId, receptionistAccountId);
        jdbc.update("update visits set source = 'SCHEDULED', originating_appointment_id = ? where id = ?",
                appointmentId, scheduledVisitId);
        jdbc.update("insert into visit_check_ins (visit_id, receptionist_account_id, reception_type, arrived_at, "
                        + "checked_in_at) values (?, ?, 'APPOINTMENT_CHECK_IN', ?, ?)",
                scheduledVisitId, receptionistAccountId, Timestamp.from(checkInAt), Timestamp.from(checkInAt));

        jdbc.execute("savepoint t045_scheduled_queue_rejection");
        assertThrows(DataAccessException.class, () -> jdbc.update("insert into walk_in_reception_queue_entries "
                        + "(visit_id, arrived_at) values (?, ?)", scheduledVisitId, Timestamp.from(checkInAt)));
        jdbc.execute("rollback to savepoint t045_scheduled_queue_rejection");
        jdbc.execute("release savepoint t045_scheduled_queue_rejection");
    }

    @Test
    void migrationUpgradesFromV022AndPreservesExistingVisitRows() {
        String cleanSchema = "t045_clean_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("create schema " + cleanSchema);
        try {
            Flyway clean = Flyway.configure().dataSource(dataSource).schemas(cleanSchema).defaultSchema(cleanSchema)
                    .locations("classpath:db/migration").target(MigrationVersion.fromVersion("025")).load();
            assertEquals(27, clean.migrate().migrationsExecuted);
            assertEquals("025", jdbc.queryForObject("select version from " + cleanSchema
                    + ".flyway_schema_history where success order by installed_rank desc limit 1", String.class));
            assertEquals(1, jdbc.queryForObject("select count(*) from information_schema.tables "
                    + "where table_schema = ? and table_name = 'walk_in_reception_queue_history'", Integer.class, cleanSchema));
        } finally {
            jdbc.execute("drop schema if exists " + cleanSchema + " cascade");
        }

        String schema = "t045_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("create schema " + schema);
        try {
            Flyway before = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                    .locations("classpath:db/migration").target(MigrationVersion.fromVersion("022")).load();
            assertEquals(24, before.migrate().migrationsExecuted);
            UUID actorId = UUID.randomUUID();
            UUID patientId = UUID.randomUUID();
            UUID oldVisitId = UUID.randomUUID();
            Instant oldArrival = Instant.parse("2026-10-20T09:00:00Z");
            jdbc.update("insert into " + schema + ".accounts (id, normalized_email, account_type, status, created_at, updated_at) "
                            + "values (?, ?, 'RECEPTIONIST', 'ACTIVE', now(), now())",
                    actorId, actorId + "@t045-upgrade.test");
            jdbc.update("insert into " + schema + ".patient_profiles (id, full_name, phone_canonical, phone_owner_type, "
                            + "date_of_birth, gender) values (?, 'T045 Upgrade Patient', '+848123456789', 'PATIENT', '1990-01-01', 'OTHER')",
                    patientId);
            jdbc.update("insert into " + schema + ".visits (id, patient_profile_id, source, arrived_at, checked_in_at, "
                            + "created_by_account_id) values (?, ?, 'WALK_IN', ?, ?, ?)",
                    oldVisitId, patientId, Timestamp.from(oldArrival), Timestamp.from(oldArrival), actorId);

            Flyway after = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                    .locations("classpath:db/migration").target(MigrationVersion.fromVersion("023")).load();
            assertEquals(1, after.migrate().migrationsExecuted);
            assertEquals(1, jdbc.queryForObject("select count(*) from " + schema + ".visits where id = ? and arrived_at = ?",
                    Integer.class, oldVisitId, Timestamp.from(oldArrival)));
            assertEquals(1, jdbc.queryForObject("select count(*) from information_schema.tables "
                    + "where table_schema = ? and table_name = 'walk_in_reception_queue_entries'", Integer.class, schema));
            assertEquals("023", jdbc.queryForObject("select version from " + schema
                    + ".flyway_schema_history where success order by installed_rank desc limit 1", String.class));
            jdbc.execute("drop trigger tr_walk_in_reception_queue_entry_lifecycle on " + schema
                    + ".walk_in_reception_queue_entries");
            jdbc.execute("drop function " + schema + ".protect_walk_in_reception_queue_entry_lifecycle()");
            jdbc.execute("create or replace function " + schema
                    + ".enforce_walk_in_reception_queue_visit() returns trigger language plpgsql as $$ "
                    + "begin if new.arrived_at is distinct from "
                    + "(select arrived_at from " + schema + ".visits where id = new.visit_id) then "
                    + "raise exception 'arrival mismatch'; end if; return new; end; $$");
            jdbc.execute("drop trigger tr_walk_in_reception_queue_visit_guard on " + schema
                    + ".walk_in_reception_queue_entries");
            jdbc.execute("create trigger tr_walk_in_reception_queue_visit_guard "
                    + "before insert or update of visit_id, arrived_at on " + schema
                    + ".walk_in_reception_queue_entries for each row execute function " + schema
                    + ".enforce_walk_in_reception_queue_visit()");
            Flyway finalUpgrade = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                    .locations("classpath:db/migration").target(MigrationVersion.fromVersion("025")).load();
            assertEquals(2, finalUpgrade.migrate().migrationsExecuted);
            assertEquals("025", jdbc.queryForObject("select version from " + schema
                    + ".flyway_schema_history where success order by installed_rank desc limit 1", String.class));
            assertEquals(1, jdbc.queryForObject("select count(*) from pg_trigger where tgrelid = '" + schema
                    + ".walk_in_reception_queue_entries'::regclass "
                    + "and tgname = 'tr_walk_in_reception_queue_entry_lifecycle'", Integer.class));
            assertTrue(jdbc.queryForObject("select pg_get_functiondef('" + schema
                    + ".enforce_walk_in_reception_queue_visit()'::regprocedure)", String.class)
                    .contains("FOR UPDATE"));
        } finally {
            jdbc.execute("drop schema if exists " + schema + " cascade");
        }
    }

    private CreateWalkInReceptionQueueEntryCommand command(UUID targetVisitId, AccountRole role) {
        AuthenticatedAccount actor = new AuthenticatedAccount(receptionistAccountId, role, UUID.randomUUID());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor, null, java.util.List.of()));
        return new CreateWalkInReceptionQueueEntryCommand(targetVisitId, actor, PREFIX + "case");
    }

    private UUID account(AccountType type, String suffix) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into accounts (id, normalized_email, account_type, status, created_at, updated_at) "
                        + "values (?, ?, ?, 'ACTIVE', now(), now())",
                id, PREFIX + suffix + "@example.test", type.name());
        return id;
    }
}
