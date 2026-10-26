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
class AppointmentT015PostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;

    @Test
    void doctorOccupancyUsesHalfOpenRangeAndIncludesBuffer() {
        String schema = "t015_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("create schema " + schema);
        try {
            flyway(schema).migrate();
            Fixture fixture = fixture(schema);
            Instant start = Instant.parse("2026-10-14T10:00:00Z");
            UUID first = appointment(schema, fixture, start, "CONFIRMED", 30, 10);

            assertEquals(Timestamp.from(start.plusSeconds(2400)), jdbc.queryForObject(
                    "select occupied_until from " + schema + ".appointments where id = ?", Timestamp.class, first));
            assertThrows(DataAccessException.class, () -> appointment(schema, fixture,
                    start.plusSeconds(2399), "CONFIRMED", 30, 0));

            // [10:00, 10:40) and [10:40, 11:10) are adjacent and therefore do not overlap.
            UUID adjacent = appointment(schema, fixture, start.plusSeconds(2400), "CONFIRMED", 30, 0);
            assertNotNull(adjacent);

            jdbc.update("update " + schema + ".appointments set status = 'CANCELLED' where id = ?", first);
            UUID rebooked = appointment(schema, fixture, start, "CONFIRMED", 30, 10);
            assertNotNull(rebooked);
        } finally {
            jdbc.execute("drop schema if exists " + schema + " cascade");
        }
    }

    @Test
    void completedAppointmentContinuesToReserveDoctorAndExclusionFailureRollsBack() {
        String schema = "t015_completed_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("create schema " + schema);
        try {
            flyway(schema).migrate();
            Fixture fixture = fixture(schema);
            Instant start = Instant.parse("2026-10-14T12:00:00Z");
            UUID completed = appointment(schema, fixture, start, "CONFIRMED", 30, 10);
            UUID visitId = UUID.randomUUID();
            new TransactionTemplate(new DataSourceTransactionManager(dataSource)).executeWithoutResult(status -> {
                jdbc.execute("set local search_path to " + schema);
                jdbc.update("insert into " + schema + ".visits (id, patient_profile_id, source, originating_appointment_id, arrived_at, checked_in_at, created_by_account_id) values (?, ?, 'SCHEDULED', ?, ?, ?, ?)",
                        visitId, fixture.patientId(), completed, Timestamp.from(start), Timestamp.from(start), fixture.receptionistId());
                jdbc.update("insert into " + schema + ".visit_check_ins (visit_id, receptionist_account_id, reception_type, arrived_at, checked_in_at) values (?, ?, 'APPOINTMENT_CHECK_IN', ?, ?)",
                        visitId, fixture.receptionistId(), Timestamp.from(start), Timestamp.from(start));
                jdbc.update("update " + schema + ".appointments set status = 'COMPLETED', visit_id = ? where id = ?", visitId, completed);
            });

            assertThrows(DataAccessException.class, () -> new TransactionTemplate(new DataSourceTransactionManager(dataSource))
                    .executeWithoutResult(status -> {
                        jdbc.execute("set local search_path to " + schema);
                        jdbc.update("update " + schema + ".appointments set purpose = 'must roll back' where id = ?", completed);
                        appointment(schema, fixture, start.plusSeconds(2399), "CONFIRMED", 30, 0);
                    }));
            assertEquals("T015 purpose", jdbc.queryForObject(
                    "select purpose from " + schema + ".appointments where id = ?", String.class, completed));
            assertEquals("COMPLETED", jdbc.queryForObject(
                    "select status from " + schema + ".appointments where id = ?", String.class, completed));
        } finally {
            jdbc.execute("drop schema if exists " + schema + " cascade");
        }
    }

    @Test
    void checkedInAndInProgressAppointmentsAlsoRemainExcluded() {
        String schema = "t015_active_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("create schema " + schema);
        try {
            flyway(schema).migrate();
            Fixture fixture = fixture(schema);
            Instant start = Instant.parse("2026-10-14T14:00:00Z");
            String[] occupiedStates = {"CHECKED_IN", "IN_PROGRESS"};
            for (int i = 0; i < occupiedStates.length; i++) {
                Instant appointmentStart = start.plusSeconds(i * 7200L);
                UUID id = appointment(schema, fixture, appointmentStart, "CONFIRMED", 30, 10);
                markCheckedIn(schema, fixture, id, appointmentStart, occupiedStates[i]);
                assertThrows(DataAccessException.class, () -> appointment(schema, fixture,
                        appointmentStart.plusSeconds(1800), "CONFIRMED", 15, 0));
            }
        } finally {
            jdbc.execute("drop schema if exists " + schema + " cascade");
        }
    }

    private Flyway flyway(String schema) {
        return Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").load();
    }

    private Fixture fixture(String schema) {
        UUID patientAccount = account(schema, "PATIENT");
        UUID receptionist = account(schema, "RECEPTIONIST");
        UUID doctorAccount = account(schema, "DOCTOR");
        UUID patientId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        jdbc.update("insert into " + schema + ".patient_profiles (id, account_id, full_name, phone_canonical, phone_owner_type, date_of_birth, gender) values (?, ?, 'T015 Patient', ?, 'PATIENT', '1990-01-01', 'OTHER')",
                patientId, patientAccount, "+849" + UUID.randomUUID().toString().replace("-", "").substring(0, 9));
        jdbc.update("insert into " + schema + ".doctor_profiles (id, account_id, full_name, work_contact) values (?, ?, 'T015 Doctor', ?)",
                doctorId, doctorAccount, "t015-" + doctorId);
        jdbc.update("insert into " + schema + ".clinic_services (id, name, duration_minutes, buffer_minutes) values (?, 'T015 service', 30, 10)", serviceId);
        jdbc.update("insert into " + schema + ".doctor_services (doctor_profile_id, service_id) values (?, ?)", doctorId, serviceId);
        return new Fixture(patientId, doctorId, serviceId, patientAccount, receptionist);
    }

    private UUID appointment(String schema, Fixture fixture, Instant start, String state, int duration, int buffer) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into " + schema + ".appointments (id, patient_profile_id, service_id, doctor_profile_id, status, booking_source, purpose, start_time, end_time, duration_minutes, buffer_minutes, created_by_account_id) values (?, ?, ?, ?, ?, 'PATIENT_ONLINE', 'T015 purpose', ?, ?, ?, ?, ?)",
                id, fixture.patientId(), fixture.serviceId(), fixture.doctorId(), state, Timestamp.from(start),
                Timestamp.from(start.plusSeconds(duration * 60L)), duration, buffer, fixture.patientAccountId());
        return id;
    }

    private UUID account(String schema, String type) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into " + schema + ".accounts (id, normalized_email, account_type, status, created_at, updated_at) values (?, ?, ?, 'ACTIVE', now(), now())",
                id, "t015-" + type.toLowerCase() + "-" + id + "@example.test", type);
        return id;
    }

    private void markCheckedIn(String schema, Fixture fixture, UUID appointmentId, Instant start, String state) {
        UUID visitId = UUID.randomUUID();
        new TransactionTemplate(new DataSourceTransactionManager(dataSource)).executeWithoutResult(status -> {
            jdbc.execute("set local search_path to " + schema);
            jdbc.update("insert into " + schema + ".visits (id, patient_profile_id, source, originating_appointment_id, arrived_at, checked_in_at, created_by_account_id) values (?, ?, 'SCHEDULED', ?, ?, ?, ?)",
                    visitId, fixture.patientId(), appointmentId, Timestamp.from(start), Timestamp.from(start), fixture.receptionistId());
            jdbc.update("insert into " + schema + ".visit_check_ins (visit_id, receptionist_account_id, reception_type, arrived_at, checked_in_at) values (?, ?, 'APPOINTMENT_CHECK_IN', ?, ?)",
                    visitId, fixture.receptionistId(), Timestamp.from(start), Timestamp.from(start));
            jdbc.update("update " + schema + ".appointments set status = ?, visit_id = ? where id = ?", state, visitId, appointmentId);
        });
    }

    private static class Fixture {
        private final UUID patientId;
        private final UUID doctorId;
        private final UUID serviceId;
        private final UUID patientAccountId;
        private final UUID receptionistId;

        private Fixture(UUID patientId, UUID doctorId, UUID serviceId, UUID patientAccountId, UUID receptionistId) {
            this.patientId = patientId;
            this.doctorId = doctorId;
            this.serviceId = serviceId;
            this.patientAccountId = patientAccountId;
            this.receptionistId = receptionistId;
        }

        private UUID patientId() { return patientId; }
        private UUID doctorId() { return doctorId; }
        private UUID serviceId() { return serviceId; }
        private UUID patientAccountId() { return patientAccountId; }
        private UUID receptionistId() { return receptionistId; }
    }
}
