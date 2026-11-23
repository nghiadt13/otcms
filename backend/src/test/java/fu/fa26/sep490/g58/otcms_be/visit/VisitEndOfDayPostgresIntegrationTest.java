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
class VisitEndOfDayPostgresIntegrationTest {
    private static final String SCHEMA = "p05_endday_" + UUID.randomUUID().toString().replace("-", "");
    private static final Instant FIRST = Instant.parse("2026-11-01T03:00:00Z"), END = Instant.parse("2026-11-01T16:30:00Z");
    @DynamicPropertySource static void schema(DynamicPropertyRegistry properties) {
        properties.add("spring.flyway.schemas", () -> SCHEMA);
        properties.add("spring.flyway.default-schema", () -> SCHEMA);
        properties.add("spring.datasource.hikari.connection-init-sql", () -> "set search_path to " + SCHEMA + ", public");
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired VisitLifecycleVisitTransaction lifecycle;
    @AfterAll static void dropSchema(@Autowired JdbcTemplate jdbc) { jdbc.execute("drop schema if exists " + SCHEMA + " cascade"); }

    @Test void waitingVisitLeavesAndClosesOpenServiceAndReceptionEntries() {
        UUID visit = fixture("WAITING", false, true);
        assertTrue(lifecycle.closeEndOfDay(visit, END));
        assertVisit(visit, "CLOSED", "CANCELLED");
        assertEquals("CANCELLED", status(visit, "SERVICE"));
        assertEquals("LEFT_RECEPTION", status(visit, "RECEPTION"));
        assertEquals("VISIT_CLOSED", jdbc.queryForObject("select exit_reason from queue_entries where visit_id = ? and stage = 'RECEPTION'", String.class, visit));
    }
    @Test void examinationWithSavedRecordAwaitsConclusionAndKeepsVisitOpen() {
        UUID visit = fixture("IN_EXAMINATION", true, false);
        assertTrue(lifecycle.closeEndOfDay(visit, END));
        assertVisit(visit, "OPEN", "AWAITING_CONCLUSION");
    }
    @Test void examinationWithoutSavedRecordLeavesAndClosesVisit() {
        UUID visit = fixture("IN_EXAMINATION", false, false);
        assertTrue(lifecycle.closeEndOfDay(visit, END));
        assertVisit(visit, "CLOSED", "CANCELLED");
    }
    private void assertVisit(UUID visit, String visitStatus, String examStatus) {
        assertEquals(visitStatus, jdbc.queryForObject("select status from visits where id = ?", String.class, visit));
        assertEquals(examStatus, status(visit, "EXAM"));
    }
    private String status(UUID visit, String stage) {
        return jdbc.queryForObject("select status from queue_entries where visit_id = ? and stage = ?", String.class, visit, stage);
    }
    private UUID fixture(String examStatus, boolean saved, boolean queues) {
        UUID receptionist = account("RECEPTIONIST"), doctorAccount = account("DOCTOR"), patientAccount = account("PATIENT");
        UUID patient = UUID.randomUUID(), doctor = UUID.randomUUID(), visit = UUID.randomUUID();
        jdbc.update("insert into patient_profiles (id, account_id, full_name, phone_canonical, phone_owner_type, date_of_birth, gender) values (?, ?, 'Endday Patient', ?, 'PATIENT', '1990-01-01', 'FEMALE')", patient, patientAccount, "+849" + patient.toString().replace("-", "").substring(0, 9));
        jdbc.update("insert into staff_profiles (is_doctor, id, account_id, display_name, publicly_visible, accepting_new_bookings, in_active_directory) values (true, ?, ?, 'Endday Doctor', true, true, true)", doctor, doctorAccount);
        jdbc.update("insert into visits (id, patient_profile_id, source, arrived_at, checked_in_at, created_by_account_id) values (?, ?, 'WALK_IN', ?, ?, ?)", visit, patient, Timestamp.from(FIRST), Timestamp.from(FIRST), receptionist);
        jdbc.update("insert into queue_entries (visit_id, stage, status, entered_at, doctor_profile_id, clinic_date, queue_number, queued_at, started_at) values (?, 'EXAM', ?, ?, ?, date '2026-11-01', 1, ?, ?)", visit, examStatus, Timestamp.from(FIRST), doctor, Timestamp.from(FIRST), examStatus.equals("IN_EXAMINATION") ? Timestamp.from(FIRST) : null);
        if (saved) jdbc.update("insert into examinations (id, visit_id, status, created_at, updated_at) values (?, ?, 'DRAFT', ?, ?)", UUID.randomUUID(), visit, Timestamp.from(FIRST), Timestamp.from(FIRST));
        if (queues) {
            UUID order = UUID.randomUUID(), room = UUID.randomUUID(), round = UUID.randomUUID();
            jdbc.update("insert into rooms (id, room_code, display_label, active) values (?, ?, 'Service Room', true)", room, "S-" + room.toString().substring(0, 8));
            jdbc.update("insert into service_order_rounds (id, visit_id, round_number, ordered_by_doctor_id, ordered_at, created_at) values (?, ?, 1, ?, ?, ?)", round, visit, doctor, Timestamp.from(FIRST), Timestamp.from(FIRST));
            jdbc.update("insert into service_orders (id, order_code, ordering_round_id, visit_id, patient_id, service_id, service_name_snapshot, service_type, requesting_doctor_id, clinical_reason, price_snapshot, order_status, payment_status, created_at, updated_at) values (?, ?, ?, ?, ?, ?, 'Other service', 'OTHER', ?, 'Pain', 100, 'ORDERED', 'PENDING_PAYMENT', ?, ?)", order, "O-" + order.toString().substring(0, 8), round, visit, patient, UUID.randomUUID(), doctor, Timestamp.from(FIRST), Timestamp.from(FIRST));
            jdbc.update("insert into queue_entries (visit_id, stage, status, entered_at, room_id, service_order_id) values (?, 'SERVICE', 'WAITING', ?, ?, ?)", visit, Timestamp.from(FIRST), room, order);
            jdbc.update("insert into queue_entries (visit_id, stage, status, entered_at, arrived_at) values (?, 'RECEPTION', 'WAITING', ?, ?)", visit, Timestamp.from(FIRST), Timestamp.from(FIRST));
        }
        return visit;
    }
    private UUID account(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into accounts (id, normalized_email, account_type, status, created_at, updated_at) values (?, ?, ?, 'ACTIVE', now(), now())", id, id + "@example.test", role);
        return id;
    }
}
