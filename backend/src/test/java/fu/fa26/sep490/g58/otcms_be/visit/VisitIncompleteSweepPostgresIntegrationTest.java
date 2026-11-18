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
    @Test void dayEightCancelsUnperformedOrdersVoidsUnpaidAndKeepsPaidWithoutRefund() {
        UUID receptionist = account("RECEPTIONIST"), doctorAccount = account("DOCTOR"), patientAccount = account("PATIENT");
        UUID patient = UUID.randomUUID(), doctor = UUID.randomUUID(), visit = UUID.randomUUID(), invoice = UUID.randomUUID();
        UUID unpaidOrder = UUID.randomUUID(), paidOrder = UUID.randomUUID(), unpaidItem = UUID.randomUUID(), paidItem = UUID.randomUUID(), payment = UUID.randomUUID();
        Instant first = Instant.parse("2026-11-01T03:00:00Z");
        jdbc.update("insert into patient_profiles (id, account_id, full_name, phone_canonical, phone_owner_type, date_of_birth, gender) values (?, ?, 'Order Patient', ?, 'PATIENT', '1990-01-01', 'FEMALE')", patient, patientAccount, "+849" + patient.toString().replace("-", "").substring(0, 9));
        jdbc.update("insert into staff_profiles (is_doctor, id, account_id, display_name, publicly_visible, accepting_new_bookings, in_active_directory) values (true, ?, ?, 'Order Doctor', true, true, true)", doctor, doctorAccount);
        jdbc.update("insert into visits (id, patient_profile_id, source, arrived_at, checked_in_at, created_by_account_id) values (?, ?, 'WALK_IN', ?, ?, ?)", visit, patient, Timestamp.from(first), Timestamp.from(first), receptionist);
        jdbc.update("insert into queue_entries (visit_id, stage, status, entered_at, doctor_profile_id, clinic_date, queue_number, queued_at, started_at, awaiting_at) values (?, 'EXAM', 'AWAITING_CONCLUSION', ?, ?, date '2026-11-01', 1, ?, ?, ?)", visit, Timestamp.from(first), doctor, Timestamp.from(first), Timestamp.from(first), Timestamp.from(first));
        UUID round = UUID.randomUUID(), service = UUID.randomUUID();
        for (UUID order : java.util.List.of(unpaidOrder, paidOrder)) {
            jdbc.update("insert into clinical_service_orders (id, order_code, ordering_round_id, round_number, ordered_at, round_head, visit_id, patient_id, service_id, service_name_snapshot, service_type, requesting_doctor_id, clinical_reason, price_snapshot, order_status, payment_status, created_at, updated_at) values (?, ?, ?, 1, now(), ?, ?, ?, ?, 'Other service', 'OTHER', ?, 'Pain', 100, 'ORDERED', ?, now(), now())", order, "O-" + order.toString().substring(0, 8), round, order.equals(unpaidOrder), visit, patient, service, doctor, order.equals(paidOrder) ? "PAID" : "PENDING_PAYMENT");
        }
        jdbc.update("insert into invoices (id, invoice_number, patient_profile_id, visit_id, source_type, source_fingerprint, created_by_account_id) values (?, ?, ?, ?, 'VISIT', ?, ?)", invoice, invoice.toString(), patient, visit, "a".repeat(64), receptionist);
        jdbc.update("insert into payment_attempts (id, invoice_id, channel, counter_method, amount, currency_code, status, created_by_account_id, idempotency_key, request_fingerprint, confirmed_at) values (?, ?, 'COUNTER', 'CASH', 100, 'VND', 'CONFIRMED', ?, ?, ?, now())", payment, invoice, receptionist, payment.toString(), "0".repeat(64));
        jdbc.update("insert into invoice_items (id, invoice_id, line_number, description_snapshot, quantity, unit_price_snapshot, service_order_id) values (?, ?, 1, 'Unpaid', 1, 100, ?)", unpaidItem, invoice, unpaidOrder);
        jdbc.update("insert into invoice_items (id, invoice_id, line_number, description_snapshot, quantity, unit_price_snapshot, service_order_id, payment_attempt_id) values (?, ?, 2, 'Paid', 1, 100, ?, ?)", paidItem, invoice, paidOrder, payment);

        assertTrue(lifecycle.markIncompleteIfDue(visit, Instant.parse("2026-11-08T00:15:00Z")));
        assertEquals(2, jdbc.queryForObject("select count(*) from clinical_service_orders where id in (?, ?) and order_status = 'CANCELLED'", Integer.class, unpaidOrder, paidOrder));
        assertNotNull(jdbc.queryForObject("select voided_at from invoice_items where id = ?", Timestamp.class, unpaidItem));
        assertNull(jdbc.queryForObject("select voided_at from invoice_items where id = ?", Timestamp.class, paidItem));
        assertNull(jdbc.queryForObject("select refund_status from invoice_items where id = ?", String.class, paidItem));
        assertEquals(payment, jdbc.queryForObject("select payment_attempt_id from invoice_items where id = ?", UUID.class, paidItem));
    }
    private UUID account(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into accounts (id, normalized_email, account_type, status, created_at, updated_at) values (?, ?, ?, 'ACTIVE', now(), now())", id, id + "@example.test", role);
        return id;
    }
}

