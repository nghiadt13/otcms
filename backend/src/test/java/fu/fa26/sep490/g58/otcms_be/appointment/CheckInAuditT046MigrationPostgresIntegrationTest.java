package fu.fa26.sep490.g58.otcms_be.appointment;

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

@SpringBootTest
@LocalPostgresTest
class CheckInAuditT046MigrationPostgresIntegrationTest {
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    @Test
    void cleanAndUpgradeMigrationRetainsUnknownHistoryAndGuardsNewAttestation() {
        String schema = "t046_audit_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("create schema " + schema);
        try {
            Flyway before = flyway(schema, "025");
            assertEquals(27, before.migrate().migrationsExecuted);
            UUID patientAccount = account(schema, "PATIENT");
            UUID receptionistAccount = account(schema, "RECEPTIONIST");
            UUID doctorAccount = account(schema, "DOCTOR");
            UUID patientId = UUID.randomUUID();
            UUID doctorId = UUID.randomUUID();
            UUID serviceId = UUID.randomUUID();
            UUID appointmentId = UUID.randomUUID();
            Instant startsAt = Instant.parse("2026-11-01T10:00:00Z");
            jdbc.update("insert into " + schema + ".patient_profiles "
                            + "(id, account_id, full_name, phone_canonical, phone_owner_type, date_of_birth, gender) "
                            + "values (?, ?, 'T046 Patient', '+84911112222', 'PATIENT', '1990-01-01', 'OTHER')",
                    patientId, patientAccount);
            jdbc.update("insert into " + schema + ".doctor_profiles (id, account_id, full_name, work_contact) "
                            + "values (?, ?, 'T046 Doctor', 't046-doctor')", doctorId, doctorAccount);
            jdbc.update("insert into " + schema + ".clinic_services (id, name, duration_minutes) "
                    + "values (?, 'T046 service', 30)", serviceId);
            jdbc.update("insert into " + schema + ".doctor_services (doctor_profile_id, service_id) values (?, ?)",
                    doctorId, serviceId);
            jdbc.update("insert into " + schema + ".appointments "
                            + "(id, patient_profile_id, service_id, doctor_profile_id, status, booking_source, purpose, "
                            + "start_time, end_time, duration_minutes, buffer_minutes, created_by_account_id) "
                            + "values (?, ?, ?, ?, 'CONFIRMED', 'PATIENT_ONLINE', 'T046 migration fixture', ?, ?, 30, 0, ?)",
                    appointmentId, patientId, serviceId, doctorId, Timestamp.from(startsAt),
                    Timestamp.from(startsAt.plusSeconds(1800)), patientAccount);
            jdbc.update("insert into " + schema + ".appointment_audits "
                            + "(appointment_id, actor_account_id, actor_type, action, old_status, new_status, "
                            + "old_doctor_profile_id, new_doctor_profile_id, old_start_time, new_start_time, "
                            + "old_end_time, new_end_time, occurred_at, correlation_id) "
                            + "values (?, ?, 'RECEPTIONIST', 'STATUS_CHANGED', 'CONFIRMED', 'CHECKED_IN', "
                            + "?, ?, ?, ?, ?, ?, ?, 't046-historical')",
                    appointmentId, receptionistAccount, doctorId, doctorId, Timestamp.from(startsAt),
                    Timestamp.from(startsAt), Timestamp.from(startsAt.plusSeconds(1800)),
                    Timestamp.from(startsAt.plusSeconds(1800)), Timestamp.from(startsAt));

            Flyway after = flyway(schema, "026");
            assertEquals(1, after.migrate().migrationsExecuted);
            assertEquals("026", jdbc.queryForObject("select version from " + schema
                    + ".flyway_schema_history where success order by installed_rank desc limit 1", String.class));
            assertEquals(1, jdbc.queryForObject("select count(*) from " + schema
                            + ".appointment_audits where appointment_id = ? and verified_patient_profile_id is null",
                    Integer.class, appointmentId));

            String insertAudit = "insert into " + schema + ".appointment_audits "
                    + "(appointment_id, actor_account_id, actor_type, action, old_status, new_status, "
                    + "old_doctor_profile_id, new_doctor_profile_id, old_start_time, new_start_time, "
                    + "old_end_time, new_end_time, occurred_at, correlation_id, verified_patient_profile_id) "
                    + "values (?, ?, 'RECEPTIONIST', 'STATUS_CHANGED', 'CONFIRMED', 'CHECKED_IN', ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            Object[] params = {appointmentId, receptionistAccount, doctorId, doctorId, Timestamp.from(startsAt),
                    Timestamp.from(startsAt), Timestamp.from(startsAt.plusSeconds(1800)),
                    Timestamp.from(startsAt.plusSeconds(1800)), Timestamp.from(startsAt), "t046-valid", patientId};
            Object[] missingAttestation = params.clone();
            missingAttestation[9] = "t046-missing";
            missingAttestation[10] = null;
            assertThrows(DataAccessException.class, () -> jdbc.update(insertAudit, missingAttestation));
            jdbc.update(insertAudit, params);
            assertEquals(1, jdbc.queryForObject("select count(*) from " + schema
                            + ".appointment_audits where correlation_id = 't046-valid' and verified_patient_profile_id = ?",
                    Integer.class, patientId));
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
        Instant now = Instant.parse("2026-10-28T00:00:00Z");
        jdbc.update("insert into " + schema + ".accounts "
                        + "(id, normalized_email, account_type, status, created_at, updated_at) "
                        + "values (?, ?, ?, 'ACTIVE', ?, ?)",
                id, id + "@t046.test", type, Timestamp.from(now), Timestamp.from(now));
        return id;
    }
}
