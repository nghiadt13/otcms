package fu.fa26.sep490.g58.otcms_be.visit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
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
class WalkInQueueT048MigrationPostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-28T12:00:00Z");

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void upgradesV026AndEnforcesQueueExitActorByReason() {
        String schema = "t048_queue_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("create schema " + schema);
        try {
            assertEquals(28, flyway(schema, "026").migrate().migrationsExecuted);
            assertEquals(1, flyway(schema, "027").migrate().migrationsExecuted);
            assertEquals("027", jdbc.queryForObject("select version from " + schema
                    + ".flyway_schema_history where success order by installed_rank desc limit 1", String.class));

            UUID receptionist = account(schema, "RECEPTIONIST");
            UUID doctor = account(schema, "DOCTOR");
            UUID patient = UUID.randomUUID();
            jdbc.update("insert into " + schema + ".patient_profiles "
                            + "(id, account_id, full_name, phone_canonical, phone_owner_type, date_of_birth, gender) "
                            + "values (?, null, 'T048 Patient', ?, 'PATIENT', '1990-01-01', 'OTHER')",
                    patient, "+848" + UUID.randomUUID().toString().replace("-", "").substring(0, 9));

            UUID careStarted = visitWithQueue(schema, patient, receptionist);
            exit(schema, careStarted, doctor, "CARE_STARTED");
            assertHistory(schema, careStarted, doctor, "CARE_STARTED");

            UUID closed = visitWithQueue(schema, patient, receptionist);
            exit(schema, closed, doctor, "VISIT_CLOSED");
            assertHistory(schema, closed, doctor, "VISIT_CLOSED");

            UUID cancelled = visitWithQueue(schema, patient, receptionist);
            exit(schema, cancelled, receptionist, "CANCELLED");
            assertHistory(schema, cancelled, receptionist, "CANCELLED");

            UUID mismatch = visitWithQueue(schema, patient, receptionist);
            assertThrows(DataAccessException.class, () -> exit(schema, mismatch, receptionist, "CARE_STARTED"));
            assertEquals(1, jdbc.queryForObject("select count(*) from " + schema
                    + ".walk_in_reception_queue_entries where visit_id = ? and status = 'WAITING'",
                    Integer.class, mismatch));
        } finally {
            jdbc.execute("drop schema if exists " + schema + " cascade");
        }
    }

    private Flyway flyway(String schema, String target) {
        return Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target(MigrationVersion.fromVersion(target)).load();
    }

    private UUID account(String schema, String type) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into " + schema + ".accounts "
                        + "(id, normalized_email, account_type, status, created_at, updated_at) "
                        + "values (?, ?, ?, 'ACTIVE', ?, ?)",
                id, id + "@t048.test", type, Timestamp.from(NOW), Timestamp.from(NOW));
        return id;
    }

    private UUID visitWithQueue(String schema, UUID patientId, UUID receptionist) {
        UUID visit = UUID.randomUUID();
        new TransactionTemplate(transactionManager).execute(status -> {
            jdbc.execute("set local search_path to " + schema);
            jdbc.update("insert into visits (id, patient_profile_id, source, arrived_at, checked_in_at, "
                    + "created_by_account_id) values (?, ?, 'WALK_IN', ?, ?, ?)", visit, patientId,
                    Timestamp.from(NOW), Timestamp.from(NOW), receptionist);
            jdbc.queryForObject("select set_config('otcms.reception_actor_id', ?, true)", String.class,
                    receptionist.toString());
            jdbc.queryForObject("select set_config('otcms.reception_correlation_id', ?, true)", String.class,
                    "t048-migration");
            jdbc.update("insert into walk_in_reception_queue_entries (visit_id, arrived_at, entered_at) "
                    + "values (?, ?, ?)", visit, Timestamp.from(NOW), Timestamp.from(NOW));
            return null;
        });
        return visit;
    }

    private void exit(String schema, UUID visit, UUID actor, String reason) {
        new TransactionTemplate(transactionManager).execute(status -> {
            jdbc.execute("set local search_path to " + schema);
            jdbc.queryForObject("select set_config('otcms.reception_actor_id', ?, true)", String.class,
                    actor.toString());
            jdbc.queryForObject("select set_config('otcms.reception_correlation_id', ?, true)", String.class,
                    "t048-exit-" + reason);
            jdbc.update("update walk_in_reception_queue_entries set status = 'LEFT_RECEPTION', left_at = ?, "
                    + "exit_reason = ? where visit_id = ? and status = 'WAITING'",
                    Timestamp.from(NOW.plusSeconds(1)), reason, visit);
            return null;
        });
    }

    private void assertHistory(String schema, UUID visit, UUID actor, String reason) {
        assertEquals(1, jdbc.queryForObject("select count(*) from " + schema
                        + ".walk_in_reception_queue_history h join " + schema
                        + ".walk_in_reception_queue_entries q on q.id = h.queue_entry_id "
                        + "where q.visit_id = ? and h.to_status = 'LEFT_RECEPTION' and h.reason = ? "
                        + "and h.actor_account_id = ?", Integer.class, visit, reason, actor));
    }
}
