package fu.fa26.sep490.g58.otcms_be.appointment;

import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@LocalPostgresTest
class AppointmentT013PostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;

    @Test
    void cleanAndUpgradeMigrationPreservesVisitsAndEnforcesAppointmentHistoryLinks() {
        String schema = "t013_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("create schema " + schema);
        try {
            Flyway v7 = flyway(schema, "7");
            assertEquals(9, v7.migrate().migrationsExecuted);

            UUID patientAccount = insertAccount(schema, "PATIENT");
            UUID receptionistAccount = insertAccount(schema, "RECEPTIONIST");
            UUID patientId = UUID.randomUUID();
            UUID walkInVisitId = UUID.randomUUID();
            jdbc.update("insert into " + schema + ".patient_profiles (id, account_id, full_name, phone_canonical, phone_owner_type, date_of_birth, gender) values (?, ?, 'T013 Patient', '+84911110000', 'PATIENT', '1990-01-01', 'OTHER')",
                    patientId, patientAccount);
            Instant arrived = Instant.parse("2026-10-13T10:00:00Z");
            jdbc.update("insert into " + schema + ".clinic_visits (id, patient_profile_id, source, arrived_at, checked_in_at, created_by_account_id) values (?, ?, 'WALK_IN', ?, ?, ?)",
                    walkInVisitId, patientId, Timestamp.from(arrived), Timestamp.from(arrived), receptionistAccount);

            assertEquals(4, flyway(schema, "11").migrate().migrationsExecuted);
            assertEquals(1, jdbc.queryForObject("select count(*) from " + schema + ".clinic_visits where id = ? and patient_profile_id = ?", Integer.class, walkInVisitId, patientId));
            for (String table : new String[]{"appointments", "appointment_audits", "appointment_command_replays"}) {
                assertEquals(1, jdbc.queryForObject("select count(*) from information_schema.tables where table_schema = ? and table_name = ?", Integer.class, schema, table));
            }

            UUID doctorAccount = insertAccount(schema, "DOCTOR");
            UUID doctorId = UUID.randomUUID();
            UUID serviceId = UUID.randomUUID();
            jdbc.update("insert into " + schema + ".doctor_profiles (id, account_id, full_name, work_contact) values (?, ?, 'T013 Doctor', 't013-doctor')",
                    doctorId, doctorAccount);
            jdbc.update("insert into " + schema + ".clinic_services (id, name, duration_minutes, buffer_minutes) values (?, 'T013 service', 30, 10)", serviceId);
            jdbc.update("insert into " + schema + ".doctor_service_assignments (doctor_profile_id, service_id) values (?, ?)", doctorId, serviceId);

            UUID appointmentId = UUID.randomUUID();
            Instant startsAt = Instant.parse("2026-10-14T10:00:00Z");
            insertAppointment(schema, appointmentId, patientId, serviceId, doctorId, patientAccount, startsAt, "PATIENT_ONLINE", null, null, null);

            UUID scheduledVisitId = UUID.randomUUID();
            jdbcTransaction().executeWithoutResult(status -> {
                jdbc.execute("set local search_path to " + schema);
                jdbc.update("insert into " + schema + ".clinic_visits (id, patient_profile_id, source, originating_appointment_id, arrived_at, checked_in_at, created_by_account_id) values (?, ?, 'SCHEDULED', ?, ?, ?, ?)",
                        scheduledVisitId, patientId, appointmentId, Timestamp.from(startsAt), Timestamp.from(startsAt), receptionistAccount);
                jdbc.update("insert into " + schema + ".visit_initial_receptions (visit_id, receptionist_account_id, reception_type, arrived_at, checked_in_at) values (?, ?, 'APPOINTMENT_CHECK_IN', ?, ?)",
                        scheduledVisitId, receptionistAccount, Timestamp.from(startsAt), Timestamp.from(startsAt));
                jdbc.update("update " + schema + ".appointments set status = 'CHECKED_IN', visit_id = ?, updated_at = now(), version = version + 1 where id = ?",
                        scheduledVisitId, appointmentId);
            });

            assertThrows(RuntimeException.class, () -> jdbcTransaction().executeWithoutResult(status -> {
                jdbc.execute("set local search_path to " + schema);
                jdbc.update("delete from " + schema + ".visit_initial_receptions where visit_id = ?", scheduledVisitId);
            }));
            assertEquals(1, jdbc.queryForObject("select count(*) from " + schema + ".visit_initial_receptions where visit_id = ? and reception_type = 'APPOINTMENT_CHECK_IN'", Integer.class, scheduledVisitId));

            UUID reassignedWalkInVisitId = UUID.randomUUID();
            jdbc.update("insert into " + schema + ".clinic_visits (id, patient_profile_id, source, arrived_at, checked_in_at, created_by_account_id) values (?, ?, 'WALK_IN', ?, ?, ?)",
                    reassignedWalkInVisitId, patientId, Timestamp.from(arrived), Timestamp.from(arrived), receptionistAccount);
            assertThrows(RuntimeException.class, () -> jdbcTransaction().executeWithoutResult(status -> {
                jdbc.execute("set local search_path to " + schema);
                jdbc.update("update " + schema + ".visit_initial_receptions set visit_id = ?, reception_type = 'WALK_IN' where visit_id = ?",
                        reassignedWalkInVisitId, scheduledVisitId);
            }));
            assertEquals(1, jdbc.queryForObject("select count(*) from " + schema + ".visit_initial_receptions where visit_id = ? and reception_type = 'APPOINTMENT_CHECK_IN'", Integer.class, scheduledVisitId));

            UUID auditId = UUID.randomUUID();
            jdbc.update("insert into " + schema + ".appointment_audits (id, appointment_id, actor_account_id, actor_type, action, old_status, new_status, old_doctor_profile_id, new_doctor_profile_id, old_start_time, new_start_time, old_end_time, new_end_time, linked_visit_id, occurred_at, correlation_id) values (?, ?, ?, 'RECEPTIONIST', 'STATUS_CHANGED', 'CONFIRMED', 'CHECKED_IN', ?, ?, ?, ?, ?, ?, ?, now(), 't013-check-in')",
                    auditId, appointmentId, receptionistAccount, doctorId, doctorId, Timestamp.from(startsAt), Timestamp.from(startsAt),
                    Timestamp.from(startsAt.plusSeconds(1800)), Timestamp.from(startsAt.plusSeconds(1800)), scheduledVisitId);
            assertThrows(DataAccessException.class, () -> jdbc.update("update " + schema + ".appointment_audits set reason = 'rewrite' where id = ?", auditId));

            String fingerprint = "a".repeat(64);
            jdbc.update("insert into " + schema + ".appointment_command_replays (actor_account_id, appointment_id, command, idempotency_key, request_fingerprint, result_visit_id, result_checked_in_at) values (?, ?, 'CHECK_IN', 't013-key', ?, ?, ?)",
                    receptionistAccount, appointmentId, fingerprint, scheduledVisitId, Timestamp.from(startsAt));
            assertThrows(DataAccessException.class, () -> jdbc.update("insert into " + schema + ".appointment_command_replays (actor_account_id, appointment_id, command, idempotency_key, request_fingerprint, result_visit_id, result_checked_in_at) values (?, ?, 'CHECK_IN', 't013-key', ?, ?, ?)",
                    receptionistAccount, appointmentId, fingerprint, scheduledVisitId, Timestamp.from(startsAt)));

            UUID otherPatientAccount = insertAccount(schema, "OTHER_PATIENT");
            UUID otherPatientId = UUID.randomUUID();
            jdbc.update("insert into " + schema + ".patient_profiles (id, account_id, full_name, phone_canonical, phone_owner_type, date_of_birth, gender) values (?, ?, 'T013 Other Patient', '+84911110001', 'PATIENT', '1991-01-01', 'OTHER')",
                    otherPatientId, otherPatientAccount);
            assertThrows(DataAccessException.class, () -> jdbc.update("insert into " + schema + ".clinic_visits (id, patient_profile_id, source, prior_appointment_id, arrived_at, checked_in_at, created_by_account_id) values (?, ?, 'WALK_IN', ?, ?, ?, ?)",
                    UUID.randomUUID(), otherPatientId, appointmentId, Timestamp.from(arrived), Timestamp.from(arrived), receptionistAccount));
        } finally {
            jdbc.execute("drop schema if exists " + schema + " cascade");
        }
    }

    @Test
    void checkedInStateRequiresLinkedVisitAndInitialReception() {
        String schema = "t013_checkin_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("create schema " + schema);
        try {
            assertEquals(13, flyway(schema, "11").migrate().migrationsExecuted);
            UUID patientAccount = insertAccount(schema, "CHECKIN_PATIENT");
            UUID doctorAccount = insertAccount(schema, "CHECKIN_DOCTOR");
            UUID patientId = UUID.randomUUID();
            UUID doctorId = UUID.randomUUID();
            UUID serviceId = UUID.randomUUID();
            jdbc.update("insert into " + schema + ".patient_profiles (id, account_id, full_name, phone_canonical, phone_owner_type, date_of_birth, gender) values (?, ?, 'T013 Patient', '+84911110002', 'PATIENT', '1990-01-01', 'OTHER')",
                    patientId, patientAccount);
            jdbc.update("insert into " + schema + ".doctor_profiles (id, account_id, full_name, work_contact) values (?, ?, 'T013 Doctor', 't013-checkin-doctor')",
                    doctorId, doctorAccount);
            jdbc.update("insert into " + schema + ".clinic_services (id, name, duration_minutes) values (?, 'T013 check-in service', 30)", serviceId);
            jdbc.update("insert into " + schema + ".doctor_service_assignments (doctor_profile_id, service_id) values (?, ?)", doctorId, serviceId);
            UUID appointmentId = UUID.randomUUID();
            Instant startsAt = Instant.parse("2026-10-14T12:00:00Z");
            insertAppointment(schema, appointmentId, patientId, serviceId, doctorId, patientAccount, startsAt, "PATIENT_ONLINE", null, null, null);
            assertThrows(RuntimeException.class, () -> jdbcTransaction().executeWithoutResult(status -> {
                jdbc.execute("set local search_path to " + schema);
                jdbc.update("update " + schema + ".appointments set status = 'CHECKED_IN' where id = ?", appointmentId);
            }));
            assertEquals("CONFIRMED", jdbc.queryForObject("select status from " + schema + ".appointments where id = ?", String.class, appointmentId));

            UUID visitId = UUID.randomUUID();
            assertThrows(RuntimeException.class, () -> jdbcTransaction().executeWithoutResult(status -> {
                jdbc.execute("set local search_path to " + schema);
                jdbc.update("insert into " + schema + ".clinic_visits (id, patient_profile_id, source, originating_appointment_id, arrived_at, checked_in_at, created_by_account_id) values (?, ?, 'SCHEDULED', ?, ?, ?, ?)",
                        visitId, patientId, appointmentId, Timestamp.from(startsAt), Timestamp.from(startsAt), patientAccount);
                jdbc.update("update " + schema + ".appointments set status = 'CHECKED_IN', visit_id = ? where id = ?", visitId, appointmentId);
            }));
            assertEquals("CONFIRMED", jdbc.queryForObject("select status from " + schema + ".appointments where id = ?", String.class, appointmentId));
        } finally {
            jdbc.execute("drop schema if exists " + schema + " cascade");
        }
    }

    private Flyway flyway(String schema, String target) {
        var configuration = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).locations("classpath:db/migration");
        if (target != null) configuration.target(target);
        return configuration.load();
    }

    private TransactionTemplate jdbcTransaction() {
        return new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    private UUID insertAccount(String schema, String suffix) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into " + schema + ".accounts (id, normalized_email, account_type, status, created_at, updated_at) values (?, ?, ?, 'ACTIVE', now(), now())",
                id, "t013-" + suffix.toLowerCase() + "-" + id + "@example.test", suffix.contains("PATIENT") ? "PATIENT" : suffix.contains("DOCTOR") ? "DOCTOR" : "RECEPTIONIST");
        return id;
    }

    private void insertAppointment(String schema, UUID appointmentId, UUID patientId, UUID serviceId, UUID doctorId,
                                   UUID creatorId, Instant startsAt, String source, UUID sourceVisitId,
                                   UUID sourceAppointmentId, UUID orderingDoctorId) {
        jdbc.update("insert into " + schema + ".appointments (id, patient_profile_id, service_id, doctor_profile_id, booking_source, purpose, start_time, end_time, duration_minutes, buffer_minutes, source_visit_id, source_appointment_id, ordering_doctor_profile_id, created_by_account_id) values (?, ?, ?, ?, ?, 'T013 purpose', ?, ?, 30, 10, ?, ?, ?, ?)",
                appointmentId, patientId, serviceId, doctorId, source, Timestamp.from(startsAt), Timestamp.from(startsAt.plusSeconds(1800)),
                sourceVisitId, sourceAppointmentId, orderingDoctorId, creatorId);
    }
}
