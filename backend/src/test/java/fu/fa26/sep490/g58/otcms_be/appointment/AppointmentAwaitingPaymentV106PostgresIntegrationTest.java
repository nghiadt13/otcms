package fu.fa26.sep490.g58.otcms_be.appointment;

import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V106 (AWAITING_PAYMENT / PAYMENT_EXPIRED): upgrade path from V105 with legacy online rows, plus the
 * database-level guarantees the booking hold relies on (deadline CHECK, doctor slot EXCLUDE, release on expiry).
 */
@SpringBootTest
@LocalPostgresTest
class AppointmentAwaitingPaymentV106PostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;

    @Test
    void v106MovesOnlyUnpaidFutureOnlinePaidServiceRowsAndIsIdempotent() {
        String schema = newSchema("t106_up_");
        try {
            assertTrue(flyway(schema, "105").migrate().migrationsExecuted > 0);
            Fixture f = fixture(schema, "up", true);
            UUID freeService = service(schema, "free", 0);
            assign(schema, f.doctorId, freeService);
            Instant day = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);

            UUID legacyUnpaid = appointment(schema, f, f.serviceId, "PATIENT_ONLINE", "CONFIRMED", day, null);
            UUID tooSoon = appointment(schema, f, f.serviceId, "PATIENT_ONLINE", "CONFIRMED",
                    Instant.now().plus(5, ChronoUnit.MINUTES), null);
            UUID byReceptionist = appointment(schema, f, f.serviceId, "RECEPTIONIST_ASSISTED", "CONFIRMED",
                    day.plus(1, ChronoUnit.HOURS), null);
            UUID alreadyPaid = appointment(schema, f, f.serviceId, "PATIENT_ONLINE", "CONFIRMED",
                    day.plus(2, ChronoUnit.HOURS), null);
            paidInvoice(schema, f, alreadyPaid);
            UUID freeOnline = appointment(schema, f, freeService, "PATIENT_ONLINE", "CONFIRMED",
                    day.plus(3, ChronoUnit.HOURS), null);
            UUID alreadyCancelled = appointment(schema, f, f.serviceId, "PATIENT_ONLINE", "CANCELLED", day, null);

            flyway(schema, null).migrate();

            assertEquals("AWAITING_PAYMENT", status(schema, legacyUnpaid));
            Instant deadline = jdbc.queryForObject("select payment_deadline from " + schema + ".appointments where id = ?",
                    Timestamp.class, legacyUnpaid).toInstant();
            assertTrue(Duration.between(Instant.now(), deadline).abs().compareTo(Duration.ofMinutes(16)) < 0);
            assertTrue(deadline.isAfter(Instant.now().plus(10, ChronoUnit.MINUTES)));
            // V106 wrote appointment_audits; V114 carried the row into audit_logs (source 'appointment_audits').
            Map<String, Object> audit = jdbc.queryForMap("select actor_type, action, before_state->>'status' as old_status, "
                    + "after_state->>'status' as new_status from " + schema
                    + ".audit_logs where source = 'appointment_audits' and entity_id = ?", legacyUnpaid);
            assertEquals("SYSTEM", audit.get("actor_type"));
            assertEquals("STATUS_CHANGED", audit.get("action"));
            assertEquals("CONFIRMED", audit.get("old_status"));
            assertEquals("AWAITING_PAYMENT", audit.get("new_status"));

            for (UUID untouched : List.of(tooSoon, byReceptionist, alreadyPaid, freeOnline)) {
                assertEquals("CONFIRMED", status(schema, untouched));
            }
            assertEquals("CANCELLED", status(schema, alreadyCancelled));
            assertNull(jdbc.queryForObject("select payment_deadline from " + schema + ".appointments where id = ?",
                    Timestamp.class, byReceptionist));
            assertEquals(1, jdbc.queryForObject("select count(*) from " + schema + ".audit_logs where source = 'appointment_audits' and correlation_id = 'V106-awaiting-payment'",
                    Integer.class));
        } finally {
            dropSchema(schema);
        }
    }

    @Test
    void awaitingPaymentRequiresDeadlineAndHoldsTheDoctorSlotUntilReleased() {
        String schema = newSchema("t106_rules_");
        try {
            flyway(schema, null).migrate();
            Fixture f = fixture(schema, "rules", false);
            Instant start = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);

            assertThrows(DataAccessException.class, () -> appointment(schema, f, f.serviceId, "PATIENT_ONLINE",
                    "AWAITING_PAYMENT", start, null), "AWAITING_PAYMENT without payment_deadline must violate the CHECK");

            UUID hold = appointment(schema, f, f.serviceId, "PATIENT_ONLINE", "AWAITING_PAYMENT", start,
                    Instant.now().plus(15, ChronoUnit.MINUTES));
            assertThrows(DataAccessException.class, () -> appointment(schema, f, f.serviceId, "PATIENT_ONLINE",
                    "AWAITING_PAYMENT", start, Instant.now().plus(15, ChronoUnit.MINUTES)),
                    "second hold on the same doctor/time must violate the EXCLUDE constraint");
            assertThrows(DataAccessException.class, () -> appointment(schema, f, f.serviceId, "RECEPTIONIST_ASSISTED",
                    "CONFIRMED", start.plus(10, ChronoUnit.MINUTES), null),
                    "a confirmed booking must not overlap a live hold");

            jdbc.update("update " + schema + ".appointments set status = 'PAYMENT_EXPIRED', updated_at = now(), version = version + 1 where id = ?", hold);
            UUID rebooked = appointment(schema, f, f.serviceId, "PATIENT_ONLINE", "CONFIRMED", start, null);
            assertEquals("CONFIRMED", status(schema, rebooked));

            UUID audit = UUID.randomUUID();
            jdbc.update("insert into " + schema + ".audit_logs (id, source, category, action, record_type, entity_id, record_label, "
                            + "actor_account_id, actor_type, before_state, after_state, metadata, occurred_at, correlation_id) "
                            + "values (?, 'appointment_audits', 'APPOINTMENT', 'STATUS_CHANGED', 'APPOINTMENT', ?, ?, ?, 'SYSTEM', "
                            + "jsonb_build_object('status', 'AWAITING_PAYMENT'), jsonb_build_object('status', 'PAYMENT_EXPIRED'), "
                            + "jsonb_build_object('reason', 'Het thoi gian thanh toan'), now(), 't106')",
                    audit, hold, hold.toString(), f.patientAccount);
            assertEquals(1, jdbc.queryForObject("select count(*) from " + schema + ".audit_logs where id = ?", Integer.class, audit));
        } finally {
            dropSchema(schema);
        }
    }

    @Test
    void awaitingPaymentRowCannotCarryAVisit() {
        String schema = newSchema("t106_visit_");
        try {
            flyway(schema, null).migrate();
            Fixture f = fixture(schema, "visit", false);
            Instant start = Instant.now().plus(4, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
            UUID hold = appointment(schema, f, f.serviceId, "PATIENT_ONLINE", "AWAITING_PAYMENT", start,
                    Instant.now().plus(15, ChronoUnit.MINUTES));
            assertThrows(DataAccessException.class, () -> jdbc.update("update " + schema
                    + ".appointments set visit_id = ? where id = ?", UUID.randomUUID(), hold));
        } finally {
            dropSchema(schema);
        }
    }

    // ---- fixtures ----

    private record Fixture(UUID patientAccount, UUID patientId, UUID doctorId, UUID serviceId) {}

    private Fixture fixture(String schema, String tag, boolean legacyDoctorTable) {
        UUID patientAccount = account(schema, "PATIENT", tag);
        UUID doctorAccount = account(schema, "DOCTOR", tag);
        UUID patientId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();
        jdbc.update("insert into " + schema + ".patient_profiles (id, account_id, full_name, phone_canonical, phone_owner_type, date_of_birth, gender) values (?, ?, 'T106 Patient', ?, 'PATIENT', '1990-01-01', 'OTHER')",
                patientId, patientAccount, "+8491" + String.format("%07d", Math.abs(tag.hashCode()) % 10_000_000));
        if (legacyDoctorTable) {
            // Schema still at V105: doctor_profiles exists; V110 copies it into staff_profiles during the upgrade.
            jdbc.update("insert into " + schema + ".doctor_profiles (id, account_id, full_name, work_contact) values (?, ?, 'T106 Doctor', ?)",
                    doctorId, doctorAccount, "t106-" + tag);
        } else {
            jdbc.update("insert into " + schema + ".staff_profiles (is_doctor, id, account_id, display_name, work_contact) values (true, ?, ?, 'T106 Doctor', ?)",
                    doctorId, doctorAccount, "t106-" + tag);
        }
        UUID serviceId = service(schema, "paid", 200000);
        assign(schema, doctorId, serviceId);
        return new Fixture(patientAccount, patientId, doctorId, serviceId);
    }

    private UUID account(String schema, String type, String tag) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into " + schema + ".accounts (id, normalized_email, account_type, status, created_at, updated_at) values (?, ?, ?, 'ACTIVE', now(), now())",
                id, "t106-" + tag + "-" + id + "@example.test", type);
        return id;
    }

    private UUID service(String schema, String name, int price) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into " + schema + ".clinic_services (id, name, duration_minutes, buffer_minutes, price) values (?, ?, 30, 0, ?)",
                id, "T106 " + name + " " + id, price);
        return id;
    }

    private void assign(String schema, UUID doctorId, UUID serviceId) {
        jdbc.update("insert into " + schema + ".doctor_services (doctor_profile_id, service_id) values (?, ?)", doctorId, serviceId);
    }

    private UUID appointment(String schema, Fixture f, UUID serviceId, String source, String status, Instant start, Instant deadline) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into " + schema + ".appointments (id, patient_profile_id, service_id, doctor_profile_id, status, booking_source, purpose, "
                        + "start_time, end_time, duration_minutes, buffer_minutes, created_by_account_id" + (deadline == null ? "" : ", payment_deadline") + ") "
                        + "values (?, ?, ?, ?, ?, ?, 'T106 purpose', ?, ?, 30, 0, ?" + (deadline == null ? "" : ", ?") + ")",
                deadline == null
                        ? new Object[]{id, f.patientId, serviceId, f.doctorId, status, source, Timestamp.from(start),
                                Timestamp.from(start.plusSeconds(1800)), f.patientAccount}
                        : new Object[]{id, f.patientId, serviceId, f.doctorId, status, source, Timestamp.from(start),
                                Timestamp.from(start.plusSeconds(1800)), f.patientAccount, Timestamp.from(deadline)});
        return id;
    }

    private void paidInvoice(String schema, Fixture f, UUID appointmentId) {
        jdbc.update("insert into " + schema + ".invoices (id, invoice_number, patient_profile_id, appointment_id, source_type, source_fingerprint, status, created_by_account_id) "
                        + "values (?, ?, ?, ?, 'APPOINTMENT', ?, 'PAID', ?)",
                UUID.randomUUID(), "T106-" + appointmentId.toString().substring(0, 8), f.patientId, appointmentId, "a".repeat(64), f.patientAccount);
    }

    private String status(String schema, UUID id) {
        return jdbc.queryForObject("select status from " + schema + ".appointments where id = ?", String.class, id);
    }

    private Flyway flyway(String schema, String target) {
        var configuration = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration");
        if (target != null) configuration.target(target);
        return configuration.load();
    }

    private String newSchema(String prefix) {
        String schema = prefix + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("create schema " + schema);
        return schema;
    }

    private void dropSchema(String schema) {
        jdbc.execute("drop schema if exists " + schema + " cascade");
    }
}
