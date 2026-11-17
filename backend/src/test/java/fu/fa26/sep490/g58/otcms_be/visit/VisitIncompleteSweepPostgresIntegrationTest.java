package fu.fa26.sep490.g58.otcms_be.visit;

import static org.junit.jupiter.api.Assertions.*;

import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import fu.fa26.sep490.g58.otcms_be.visit.service.impl.VisitLifecycleVisitTransaction;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@LocalPostgresTest
class VisitIncompleteSweepPostgresIntegrationTest {
    private static final String SCHEMA = "p05_incomplete_" + UUID.randomUUID().toString().replace("-", "");

    @DynamicPropertySource
    static void schema(DynamicPropertyRegistry properties) {
        properties.add("spring.flyway.schemas", () -> SCHEMA);
        properties.add("spring.flyway.default-schema", () -> SCHEMA);
        properties.add("spring.datasource.hikari.connection-init-sql", () -> "set search_path to " + SCHEMA + ", public");
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired VisitLifecycleVisitTransaction lifecycle;
    @Autowired fu.fa26.sep490.g58.otcms_be.visit.repository.VisitLifecycleRepository candidates;

    @AfterAll static void dropSchema(@Autowired JdbcTemplate jdbc) {
        jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    }

    @Test void dayEightMarksIncompleteAndNotifiesOnlyOnce() {
        UUID receptionist = account("RECEPTIONIST"), doctorAccount = account("DOCTOR"), patientAccount = account("PATIENT");
        UUID patient = UUID.randomUUID(), doctor = UUID.randomUUID(), visit = UUID.randomUUID();
        Instant first = Instant.parse("2026-11-01T03:00:00Z");
        jdbc.update("insert into patient_profiles (id, account_id, full_name, phone_canonical, phone_owner_type, date_of_birth, gender) values (?, ?, 'Sweep Patient', ?, 'PATIENT', '1990-01-01', 'FEMALE')", patient, patientAccount, "+849" + patient.toString().replace("-", "").substring(0, 9));
        jdbc.update("insert into staff_profiles (is_doctor, id, account_id, display_name, publicly_visible, accepting_new_bookings, in_active_directory) values (true, ?, ?, 'Sweep Doctor', true, true, true)", doctor, doctorAccount);
        jdbc.update("insert into visits (id, patient_profile_id, source, arrived_at, checked_in_at, created_by_account_id) values (?, ?, 'WALK_IN', ?, ?, ?)", visit, patient, Timestamp.from(first), Timestamp.from(first), receptionist);
        jdbc.update("insert into queue_entries (visit_id, stage, status, entered_at, doctor_profile_id, clinic_date, queue_number, queued_at, started_at, awaiting_at) values (?, 'EXAM', 'AWAITING_CONCLUSION', ?, ?, date '2026-11-01', 1, ?, ?, ?)", visit, Timestamp.from(first), doctor, Timestamp.from(first), Timestamp.from(first), Timestamp.from(first));
        assertFalse(lifecycle.markIncompleteIfDue(visit, Instant.parse("2026-11-07T10:00:00Z")));
        assertTrue(lifecycle.markIncompleteIfDue(visit, Instant.parse("2026-11-08T00:15:00Z")));
        assertFalse(lifecycle.markIncompleteIfDue(visit, Instant.parse("2026-11-08T00:15:00Z")));
        assertEquals("INCOMPLETE", jdbc.queryForObject("select status from visits where id = ?", String.class, visit));
        assertEquals(1, jdbc.queryForObject("select count(*) from notifications where recipient_account_id = ? and related_id = ? and type = 'VISIT_INCOMPLETE'", Integer.class, patientAccount, visit));
        jdbc.update("insert into visits (id, patient_profile_id, source, arrived_at, checked_in_at, created_by_account_id) select gen_random_uuid(), ?, 'WALK_IN', ?, ?, ? from generate_series(1, 501)", patient, Timestamp.from(first), Timestamp.from(first), receptionist);
        jdbc.update("insert into queue_entries (visit_id, stage, status, entered_at, doctor_profile_id, clinic_date, queue_number, queued_at, started_at, awaiting_at) select id, 'EXAM', 'AWAITING_CONCLUSION', ?, ?, date '2026-11-01', row_number() over (order by id) + 1, ?, ?, ? from visits where created_by_account_id = ? and status = 'OPEN'", Timestamp.from(first), doctor, Timestamp.from(first), Timestamp.from(first), Timestamp.from(first), receptionist);
        assertEquals(501, candidates.findIncompleteCandidates(java.time.LocalDate.of(2026, 11, 1), java.time.ZoneId.of("Asia/Ho_Chi_Minh")).size());
    }
    private UUID account(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into accounts (id, normalized_email, account_type, status, created_at, updated_at) values (?, ?, ?, 'ACTIVE', now(), now())", id, id + "@example.test", role);
        return id;
    }
}
