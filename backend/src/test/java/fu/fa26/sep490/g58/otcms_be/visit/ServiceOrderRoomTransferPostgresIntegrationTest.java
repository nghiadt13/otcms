package fu.fa26.sep490.g58.otcms_be.visit;

import static org.junit.jupiter.api.Assertions.*;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.service.ServiceOrderRoomTransferService;
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
class ServiceOrderRoomTransferPostgresIntegrationTest {
    private static final String SCHEMA = "p05_room_" + UUID.randomUUID().toString().replace("-", "");
    @DynamicPropertySource static void schema(DynamicPropertyRegistry properties) {
        properties.add("spring.flyway.schemas", () -> SCHEMA);
        properties.add("spring.flyway.default-schema", () -> SCHEMA);
        properties.add("spring.datasource.hikari.connection-init-sql", () -> "set search_path to " + SCHEMA + ", public");
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired ServiceOrderRoomTransferService transfers;
    @AfterAll static void dropSchema(@Autowired JdbcTemplate jdbc) { jdbc.execute("drop schema if exists " + SCHEMA + " cascade"); }

    @Test void waitingServiceEntryMovesWithOrderToNewRoom() {
        Fixture f = fixture("WAITING");
        transfers.transfer(f.order, f.newRoom, "Room changed", f.actor(), "room-test");
        assertEquals(f.newRoom, jdbc.queryForObject("select room_id from service_orders where id = ?", UUID.class, f.order));
        assertEquals(f.newRoom, jdbc.queryForObject("select room_id from queue_entries where service_order_id = ?", UUID.class, f.order));
        assertEquals("WAITING", jdbc.queryForObject("select status from queue_entries where service_order_id = ?", String.class, f.order));
    }
    @Test void calledServiceEntryRejectsTransferWithMsg42() {
        Fixture f = fixture("CALLED");
        assertEquals("MSG-42", assertThrows(VisitOperationException.class, () -> transfers.transfer(f.order, f.newRoom, "Room changed", f.actor(), "room-test")).code());
        assertEquals(f.oldRoom, jdbc.queryForObject("select room_id from service_orders where id = ?", UUID.class, f.order));
        assertEquals(f.oldRoom, jdbc.queryForObject("select room_id from queue_entries where service_order_id = ?", UUID.class, f.order));
    }
    @Test void transferDoesNotMoveUnlinkedCareAssignmentReservation() {
        Fixture f = fixture("WAITING");
        UUID assignment = UUID.randomUUID(), reservation = UUID.randomUUID();
        jdbc.update("insert into visit_assignments (id, visit_id, doctor_profile_id, starts_at, ends_at, status, assigned_at, step_code, expected_duration_minutes) values (?, ?, ?, now(), now() + interval '30 minutes', 'IN_PROGRESS', now(), 'CONSULTATION', 30)", assignment, f.visit, f.doctor);
        jdbc.update("insert into room_reservations (id, visit_id, assignment_id, room_id, step_code, capability_code, starts_at, expected_ends_at, reserved_until, cleanup_buffer_minutes, status, assigned_at) values (?, ?, ?, ?, 'CONSULTATION', 'EXAM_ROOM', now(), now() + interval '30 minutes', now() + interval '30 minutes', 0, 'IN_PROGRESS', now())", reservation, f.visit, assignment, f.oldRoom);
        transfers.transfer(f.order, f.newRoom, "Room changed", f.actor(), "room-test");
        assertEquals(f.newRoom, jdbc.queryForObject("select room_id from service_orders where id = ?", UUID.class, f.order));
        assertEquals(f.oldRoom, jdbc.queryForObject("select room_id from room_reservations where id = ?", UUID.class, reservation));
    }
    private Fixture fixture(String status) {
        UUID receptionist = account("RECEPTIONIST"), doctorAccount = account("DOCTOR"), patientAccount = account("PATIENT");
        UUID patient = UUID.randomUUID(), doctor = UUID.randomUUID(), visit = UUID.randomUUID(), order = UUID.randomUUID(), service = UUID.randomUUID(), oldRoom = UUID.randomUUID(), newRoom = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("insert into patient_profiles (id, account_id, full_name, phone_canonical, phone_owner_type, date_of_birth, gender) values (?, ?, 'Room Patient', ?, 'PATIENT', '1990-01-01', 'FEMALE')", patient, patientAccount, "+849" + patient.toString().replace("-", "").substring(0, 9));
        jdbc.update("insert into staff_profiles (is_doctor, id, account_id, display_name, publicly_visible, accepting_new_bookings, in_active_directory) values (true, ?, ?, 'Room Doctor', true, true, true)", doctor, doctorAccount);
        jdbc.update("insert into clinic_services (id, name, duration_minutes, buffer_minutes, price) values (?, ?, 30, 0, 100)", service, "Service " + service);
        for (UUID room : new UUID[]{oldRoom, newRoom}) {
            jdbc.update("insert into rooms (id, room_code, display_label, active) values (?, ?, 'Service Room', true)", room, "S-" + room.toString().substring(0, 8));
            jdbc.update("insert into room_supported_services (room_id, service_id) values (?, ?)", room, service);
        }
        jdbc.update("insert into visits (id, patient_profile_id, source, arrived_at, checked_in_at, created_by_account_id) values (?, ?, 'WALK_IN', ?, ?, ?)", visit, patient, Timestamp.from(now), Timestamp.from(now), receptionist);
        UUID round = UUID.randomUUID();
        jdbc.update("insert into service_order_rounds (id, visit_id, round_number, ordered_by_doctor_id, ordered_at, created_at) values (?, ?, 1, ?, ?, ?)", round, visit, doctor, Timestamp.from(now), Timestamp.from(now));
        jdbc.update("insert into service_orders (id, order_code, ordering_round_id, visit_id, patient_id, service_id, service_name_snapshot, service_type, requesting_doctor_id, clinical_reason, price_snapshot, order_status, payment_status, room_id, created_at, updated_at) values (?, ?, ?, ?, ?, ?, 'Other service', 'OTHER', ?, 'Pain', 100, 'ORDERED', 'PAID', ?, ?, ?)", order, "O-" + order.toString().substring(0, 8), round, visit, patient, service, doctor, oldRoom, Timestamp.from(now), Timestamp.from(now));
        jdbc.update("insert into queue_entries (visit_id, stage, status, entered_at, room_id, service_order_id, called_at) values (?, 'SERVICE', ?, ?, ?, ?, ?)", visit, status, Timestamp.from(now), oldRoom, order, status.equals("CALLED") ? Timestamp.from(now) : null);
        return new Fixture(receptionist, order, oldRoom, newRoom, visit, doctor);
    }
    private UUID account(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into accounts (id, normalized_email, account_type, status, created_at, updated_at) values (?, ?, ?, 'ACTIVE', now(), now())", id, id + "@example.test", role);
        return id;
    }
    private record Fixture(UUID receptionist, UUID order, UUID oldRoom, UUID newRoom, UUID visit, UUID doctor) {
        AuthenticatedAccount actor() { return new AuthenticatedAccount(receptionist, AccountRole.RECEPTIONIST, UUID.randomUUID()); }
    }
}
