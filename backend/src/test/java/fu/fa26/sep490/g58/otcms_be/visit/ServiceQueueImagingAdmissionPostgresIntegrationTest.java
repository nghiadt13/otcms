package fu.fa26.sep490.g58.otcms_be.visit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import fu.fa26.sep490.g58.otcms_be.billing.service.BillingQueryService;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticAcquisitionService;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import fu.fa26.sep490.g58.otcms_be.visit.service.QueueStateMachine;
import fu.fa26.sep490.g58.otcms_be.visit.service.ServiceQueueService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Phase 08 main path with the real imaging handler: a paid imaging order gets a room and a PENDING imaging request,
 * the patient waits in the room queue, the technician calls the patient and only then starts the acquisition, and
 * an unpaid order cannot be started (D7).
 */
@SpringBootTest
@LocalPostgresTest
class ServiceQueueImagingAdmissionPostgresIntegrationTest {
    private static final String SCHEMA = "p08_imaging_" + UUID.randomUUID().toString().replace("-", "");

    @DynamicPropertySource static void schema(DynamicPropertyRegistry properties) {
        properties.add("spring.flyway.schemas", () -> SCHEMA);
        properties.add("spring.flyway.default-schema", () -> SCHEMA);
        properties.add("spring.datasource.hikari.connection-init-sql", () -> "set search_path to " + SCHEMA + ", public");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired ServiceQueueService serviceQueue;
    @Autowired QueueStateMachine machine;
    @Autowired DiagnosticAcquisitionService acquisition;
    @MockitoBean BillingQueryService billing;

    @AfterAll static void dropSchema(@Autowired JdbcTemplate jdbc) { jdbc.execute("drop schema if exists " + SCHEMA + " cascade"); }

    @Test void paidImagingOrderQueuesInTheStaffedRoomAndStartsOnlyAfterTheCall() {
        Fx f = fixture();
        when(billing.isServiceOrderPaid(f.order)).thenReturn(true);
        serviceQueue.admitPaidOrder(f.order);

        assertEquals(f.room, jdbc.queryForObject("select room_id from clinical_service_orders where id = ?", UUID.class, f.order));
        assertEquals("PENDING", jdbc.queryForObject("select execution_status from imaging_order_executions where service_order_id = ?", String.class, f.order));
        assertEquals("PAID", jdbc.queryForObject("select payment_status from clinical_service_orders where id = ?", String.class, f.order));
        assertEquals(1, jdbc.queryForObject("select count(*) from queue_entries where service_order_id = ? and stage = 'SERVICE' and status = 'WAITING' and room_id = ?", Integer.class, f.order, f.room));

        // Not called yet: MSG-42.
        var notCalled = assertThrows(DiagnosticOperationException.class, () -> acquisition.start(f.technician(), f.order));
        assertEquals(DiagnosticOperationOutcome.PATIENT_NOT_CALLED, notCalled.outcome());

        var actor = new AuthenticatedAccount(f.techAccount, AccountRole.IMAGING_TECHNICIAN, UUID.randomUUID());
        assertEquals("CALLED", machine.callNext(f.room, "SERVICE", actor).status());
        assertNotNull(acquisition.start(f.technician(), f.order));
        assertEquals("IN_EXAMINATION", jdbc.queryForObject("select status from queue_entries where service_order_id = ?", String.class, f.order));
        assertEquals("IN_PROGRESS", jdbc.queryForObject("select execution_status from imaging_order_executions where service_order_id = ?", String.class, f.order));
    }

    @Test void technicianCannotStartAnOrderWhoseFeeIsNotPaid() {
        Fx f = fixture();
        when(billing.isServiceOrderPaid(f.order)).thenReturn(true);
        serviceQueue.admitPaidOrder(f.order);
        var actor = new AuthenticatedAccount(f.techAccount, AccountRole.IMAGING_TECHNICIAN, UUID.randomUUID());
        machine.callNext(f.room, "SERVICE", actor);
        // The fee was refunded or voided meanwhile: D7, MSG-95.
        when(billing.isServiceOrderPaid(f.order)).thenReturn(false);
        var unpaid = assertThrows(DiagnosticOperationException.class, () -> acquisition.start(f.technician(), f.order));
        assertEquals(DiagnosticOperationOutcome.ORDER_NOT_PAID, unpaid.outcome());
        assertEquals("PENDING", jdbc.queryForObject("select execution_status from imaging_order_executions where service_order_id = ?", String.class, f.order));
    }

    @Test void cannotPerformLeavesTheQueueAndKeepsTheOrderForTheDoctor() {
        Fx f = fixture();
        when(billing.isServiceOrderPaid(f.order)).thenReturn(true);
        serviceQueue.admitPaidOrder(f.order);
        acquisition.reportCannotPerform(f.technician(), f.order, "Bệnh nhân có kim loại trong người");
        assertEquals("CANCELLED", jdbc.queryForObject("select status from queue_entries where service_order_id = ?", String.class, f.order));
        assertEquals("PENDING", jdbc.queryForObject("select execution_status from imaging_order_executions where service_order_id = ?", String.class, f.order));
        assertEquals("ORDERED", jdbc.queryForObject("select order_status from clinical_service_orders where id = ?", String.class, f.order));
    }

    // ---- fixture -----------------------------------------------------------------------------------------------

    private record Fx(UUID order, UUID room, UUID techAccount) {
        DiagnosticActor technician() { return DiagnosticActor.user(techAccount, AccountRole.IMAGING_TECHNICIAN); }
    }

    private Fx fixture() {
        UUID receptionist = account("RECEPTIONIST"), doctorAccount = account("DOCTOR"), patientAccount = account("PATIENT"),
                techAccount = account("IMAGING_TECHNICIAN");
        UUID patient = UUID.randomUUID(), doctor = UUID.randomUUID(), techProfile = UUID.randomUUID(), room = UUID.randomUUID(),
                service = UUID.randomUUID(), visit = UUID.randomUUID(), order = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("insert into patient_profiles (id, account_id, full_name, phone_canonical, phone_owner_type, date_of_birth, gender) values (?, ?, 'Imaging Patient', ?, 'PATIENT', '1990-01-01', 'FEMALE')",
                patient, patientAccount, "+849" + patient.toString().replace("-", "").substring(0, 9));
        jdbc.update("insert into staff_profiles (is_doctor, id, account_id, display_name, publicly_visible, accepting_new_bookings, in_active_directory) values (true, ?, ?, 'Imaging Doctor', true, true, true)", doctor, doctorAccount);
        jdbc.update("insert into staff_profiles (id, account_id, display_name, work_contact) values (?, ?, 'Imaging technician', '')", techProfile, techAccount);
        jdbc.update("insert into clinic_services (id, name, service_type, modality, duration_minutes, buffer_minutes, price) values (?, ?, 'IMAGING', 'MRI', 30, 0, 100)", service, "MRI " + service);
        jdbc.update("insert into rooms (id, room_code, display_label, room_type) values (?, ?, 'MRI room', 'IMAGING')", room, "MRI-" + room.toString().substring(0, 8));
        jdbc.update("update rooms set capabilities = array['IMAGING_ROOM'] where id = ?", room);
        jdbc.update("insert into room_supported_services (room_id, service_id) values (?, ?)", room, service);
        jdbc.update("insert into work_shifts (staff_profile_id, room_id, starts_at, ends_at) values (?, ?, ?, ?)", techProfile, room,
                Timestamp.from(now.minusSeconds(60)), Timestamp.from(now.plusSeconds(3600)));
        jdbc.update("insert into visits (id, patient_profile_id, source, arrived_at, checked_in_at, created_by_account_id) values (?, ?, 'WALK_IN', ?, ?, ?)",
                visit, patient, Timestamp.from(now), Timestamp.from(now), receptionist);
        jdbc.update("insert into visit_assignments (id, visit_id, doctor_profile_id, starts_at, ends_at, status) values (?, ?, ?, ?, ?, 'IN_PROGRESS')",
                UUID.randomUUID(), visit, doctor, Timestamp.from(now), Timestamp.from(now.plusSeconds(1800)));
        Timestamp at = Timestamp.from(now);
        UUID round = UUID.randomUUID();
        jdbc.update("insert into clinical_ordering_rounds (id, visit_id, round_number, ordered_by_doctor_id, ordered_at, created_at) values (?, ?, 1, ?, ?, ?)",
                round, visit, doctor, at, at);
        jdbc.update("insert into clinical_service_orders (id, order_code, ordering_round_id, visit_id, patient_id, service_id, service_name_snapshot, service_type, requesting_doctor_id, clinical_reason, body_area, price_snapshot, order_status, payment_status, created_at, updated_at) values (?, ?, ?, ?, ?, ?, 'MRI knee', 'IMAGING', ?, 'Knee pain', 'Knee', 100, 'ORDERED', 'PENDING_PAYMENT', ?, ?)",
                order, "O-" + order.toString().substring(0, 8), round, visit, patient, service, doctor, at, at);
        return new Fx(order, room, techAccount);
    }

    private UUID account(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into accounts (id, normalized_email, account_type, status, created_at, updated_at) values (?, ?, ?, 'ACTIVE', now(), now())", id, id + "@example.test", role);
        return id;
    }
}
