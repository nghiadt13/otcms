package fu.fa26.sep490.g58.otcms_be.visit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.billing.service.BillingQueryService;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.service.QueueStateMachine;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitReturnService;
import fu.fa26.sep490.g58.otcms_be.visit.service.ServiceAdmissionHandler;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@LocalPostgresTest
@Import(VisitReturnWithinSevenDaysPostgresIntegrationTest.OtherTypeHandler.class)
class VisitReturnWithinSevenDaysPostgresIntegrationTest {
    @TestConfiguration
    static class OtherTypeHandler {
        @Bean ServiceAdmissionHandler otherTypeAdmissionHandler() {
            return new ServiceAdmissionHandler() {
                @Override public boolean supports(String type) { return "OTHER".equals(type); }
                @Override public List<UUID> eligibleRoomIds(UUID orderId) { return List.of(); }
                @Override public void assignRoom(UUID orderId, UUID roomId) { }
            };
        }
    }
    private static final String SCHEMA = "p05_return_" + UUID.randomUUID().toString().replace("-", "");
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    @DynamicPropertySource static void schema(DynamicPropertyRegistry properties) {
        properties.add("spring.flyway.schemas", () -> SCHEMA);
        properties.add("spring.flyway.default-schema", () -> SCHEMA);
        properties.add("spring.datasource.hikari.connection-init-sql", () -> "set search_path to " + SCHEMA + ", public");
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired VisitReturnService returns;
    @Autowired QueueStateMachine queue;
    @Autowired MockMvc mvc;
    @MockitoBean BillingQueryService billing;
    @AfterAll static void dropSchema(@Autowired JdbcTemplate jdbc) { jdbc.execute("drop schema if exists " + SCHEMA + " cascade"); }

    @Test void returnWithinSevenDaysReusesVisitAndInvoiceAndCanBeCalled() {
        Fixture f = fixture(2);
        order(f, "RESULTS_AVAILABLE");
        UUID invoice = UUID.randomUUID();
        jdbc.update("insert into invoices (id, invoice_number, patient_profile_id, visit_id, source_type, source_fingerprint, created_by_account_id) values (?, ?, ?, ?, 'VISIT', ?, ?)", invoice, invoice.toString(), f.patient, f.visit, "a".repeat(64), f.receptionist);
        var ticket = returns.returnToVisit(f.visit, f.actor(), "return-test");
        assertEquals(f.visit, ticket.visitId());
        assertEquals("WAITING", ticket.status());
        assertEquals(1, ticket.queueNumber());
        assertEquals(1, jdbc.queryForObject("select count(*) from invoices where visit_id = ?", Integer.class, f.visit));
        assertEquals(1, jdbc.queryForObject("select count(*) from queue_entries where visit_id = ? and stage = 'EXAM' and status = 'AWAITING_CONCLUSION'", Integer.class, f.visit));
        assertEquals("CALLED", queue.callNext(f.room, "RESULT_REVIEW", f.actor()).status());
    }
    @Test void returnRestoresCancelledPaidServiceQueueEntry() {
        Fixture f = fixture(1);
        UUID order = order(f, "ORDERED");
        when(billing.isServiceOrderPaid(order)).thenReturn(true);
        jdbc.update("insert into queue_entries (visit_id, stage, status, entered_at, room_id, service_order_id, finished_at) values (?, 'SERVICE', 'CANCELLED', now(), ?, ?, now())", f.visit, f.room, order);
        var ticket = returns.returnToVisit(f.visit, f.actor(), "return-service-test");
        assertEquals(f.room, ticket.roomId());
        assertEquals(1, jdbc.queryForObject("select count(*) from queue_entries where service_order_id = ? and stage = 'SERVICE' and status = 'WAITING'", Integer.class, order));
        assertEquals("AWAITING_CONCLUSION", jdbc.queryForObject("select status from queue_entries where visit_id = ? and stage = 'EXAM'", String.class, f.visit));
    }
    @Test void unpaidOutstandingServiceCannotEnterQueue() {
        Fixture f = fixture(1);
        order(f, "ORDERED");
        assertEquals("MSG-42", assertThrows(VisitOperationException.class, () -> returns.returnToVisit(f.visit, f.actor(), "return-test")).code());
    }
    @Test void returnReusesPreviousDaysResultReviewEntry() {
        Fixture f = fixture(2);
        order(f, "RESULTS_AVAILABLE");
        jdbc.update("insert into queue_entries (visit_id, stage, status, entered_at, room_id, doctor_profile_id, clinic_date, queue_number, queued_at) values (?, 'RESULT_REVIEW', 'WAITING', now() - interval '1 day', ?, ?, ?, 2, now() - interval '1 day')", f.visit, f.room, f.doctor, LocalDate.now(ZONE).minusDays(1));
        returns.returnToVisit(f.visit, f.actor(), "return-review-test");
        assertEquals(1, jdbc.queryForObject("select count(*) from queue_entries where visit_id = ? and stage = 'RESULT_REVIEW' and status = 'WAITING' and clinic_date = ?", Integer.class, f.visit, LocalDate.now(ZONE)));
    }
    @Test void returnOnDayEightMarksVisitIncompleteAndRejectsWithMsg42() {
        Fixture f = fixture(7);
        assertEquals("MSG-42", assertThrows(VisitOperationException.class, () -> returns.returnToVisit(f.visit, f.actor(), "return-test")).code());
        assertEquals("INCOMPLETE", jdbc.queryForObject("select status from visits where id = ?", String.class, f.visit));
    }
    @Test void nonReceptionistCannotReturnVisit() {
        Fixture f = fixture(1);
        var doctor = new AuthenticatedAccount(f.doctorAccount, AccountRole.DOCTOR, UUID.randomUUID());
        assertEquals("MSG-40", assertThrows(VisitOperationException.class, () -> returns.returnToVisit(f.visit, doctor, "return-test")).code());
    }
    @Test void returnCandidateEndpointReturns200ForEligibleVisitAnd204Otherwise() throws Exception {
        Fixture eligible = fixture(1), expired = fixture(7);
        var auth = new UsernamePasswordAuthenticationToken(eligible.actor(), null, List.of(new SimpleGrantedAuthority("ROLE_RECEPTIONIST")));
        mvc.perform(get("/api/v1/reception/patients/{id}/return-candidate", eligible.patient).with(authentication(auth)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.visitId").value(eligible.visit.toString()));
        mvc.perform(get("/api/v1/reception/patients/{id}/return-candidate", expired.patient).with(authentication(auth)))
                .andExpect(status().isNoContent());
    }
    private Fixture fixture(int daysAgo) {
        UUID receptionist = account("RECEPTIONIST"), doctorAccount = account("DOCTOR"), patientAccount = account("PATIENT");
        UUID patient = UUID.randomUUID(), doctor = UUID.randomUUID(), room = UUID.randomUUID(), visit = UUID.randomUUID();
        Instant first = LocalDate.now(ZONE).minusDays(daysAgo).atTime(10, 0).atZone(ZONE).toInstant();
        jdbc.update("insert into patient_profiles (id, account_id, full_name, phone_canonical, phone_owner_type, date_of_birth, gender) values (?, ?, 'Return Patient', ?, 'PATIENT', '1990-01-01', 'FEMALE')", patient, patientAccount, "+849" + patient.toString().replace("-", "").substring(0, 9));
        jdbc.update("insert into staff_profiles (is_doctor, id, account_id, display_name, publicly_visible, accepting_new_bookings, in_active_directory) values (true, ?, ?, 'Return Doctor', true, true, true)", doctor, doctorAccount);
        jdbc.update("insert into rooms (id, room_code, display_label, active) values (?, ?, 'Return Room', true)", room, "R-" + room.toString().substring(0, 8));
        jdbc.update("insert into visits (id, patient_profile_id, source, arrived_at, checked_in_at, created_by_account_id) values (?, ?, 'WALK_IN', ?, ?, ?)", visit, patient, Timestamp.from(first), Timestamp.from(first), receptionist);
        jdbc.update("insert into queue_entries (visit_id, stage, status, entered_at, doctor_profile_id, room_id, clinic_date, queue_number, queued_at, started_at, awaiting_at) values (?, 'EXAM', 'AWAITING_CONCLUSION', ?, ?, ?, ?, 7, ?, ?, ?)", visit, Timestamp.from(first), doctor, room, first.atZone(ZONE).toLocalDate(), Timestamp.from(first), Timestamp.from(first), Timestamp.from(first));
        return new Fixture(receptionist, doctorAccount, doctor, patient, room, visit);
    }
    private UUID order(Fixture f, String status) {
        UUID service = UUID.randomUUID(), order = UUID.randomUUID(), round = UUID.randomUUID();
        jdbc.update("insert into clinic_services (id, name, duration_minutes, buffer_minutes, price) values (?, ?, 30, 0, 100)", service, "Return service " + service);
        jdbc.update("insert into service_order_rounds (id, visit_id, round_number, ordered_by_doctor_id, ordered_at, created_at) values (?, ?, 1, ?, now(), now())", round, f.visit, f.doctor);
        jdbc.update("insert into service_orders (id, order_code, ordering_round_id, visit_id, patient_id, service_id, service_name_snapshot, service_type, requesting_doctor_id, clinical_reason, price_snapshot, order_status, payment_status, room_id, created_at, updated_at) values (?, ?, ?, ?, ?, ?, 'Return service', 'OTHER', ?, 'Pain', 100, ?, 'PAID', ?, now(), now())", order, "R-" + order.toString().substring(0, 8), round, f.visit, f.patient, service, f.doctor, status, f.room);
        return order;
    }
    private UUID account(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into accounts (id, normalized_email, account_type, status, created_at, updated_at) values (?, ?, ?, 'ACTIVE', now(), now())", id, id + "@example.test", role);
        return id;
    }
    private record Fixture(UUID receptionist, UUID doctorAccount, UUID doctor, UUID patient, UUID room, UUID visit) {
        AuthenticatedAccount actor() { return new AuthenticatedAccount(receptionist, AccountRole.RECEPTIONIST, UUID.randomUUID()); }
    }
}

