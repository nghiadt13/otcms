package fu.fa26.sep490.g58.otcms_be.visit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import fu.fa26.sep490.g58.otcms_be.billing.event.ServiceOrderPaidEvent;
import fu.fa26.sep490.g58.otcms_be.billing.service.BillingQueryService;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.service.ResultReviewQueueService;
import fu.fa26.sep490.g58.otcms_be.visit.service.ServiceAdmissionHandler;
import fu.fa26.sep490.g58.otcms_be.visit.service.ServiceQueueService;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Phase 08: paid service order to room queue admission (D4/D7), one queue at a time, next room, and the NF-04
 * result-review entry. The service type OTHER is handled by a test handler, which also shows how another service
 * type (LAB) plugs in without touching the imaging handler.
 */
@SpringBootTest
@LocalPostgresTest
@Import(ServiceQueueAdmissionPostgresIntegrationTest.OtherTypeHandler.class)
class ServiceQueueAdmissionPostgresIntegrationTest {
    private static final String SCHEMA = "p08_queue_" + UUID.randomUUID().toString().replace("-", "");
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));

    @DynamicPropertySource static void schema(DynamicPropertyRegistry properties) {
        properties.add("spring.flyway.schemas", () -> SCHEMA);
        properties.add("spring.flyway.default-schema", () -> SCHEMA);
        properties.add("spring.datasource.hikari.connection-init-sql", () -> "set search_path to " + SCHEMA + ", public");
    }

    /** Admits type OTHER from the rooms that support the order's service; stores the room on the order row. */
    @TestConfiguration
    static class OtherTypeHandler {
        @Bean ServiceAdmissionHandler otherTypeAdmissionHandler(JdbcTemplate jdbc) {
            return new ServiceAdmissionHandler() {
                @Override public boolean supports(String serviceType) { return "OTHER".equals(serviceType); }
                @Override public List<UUID> eligibleRoomIds(UUID orderId) {
                    return jdbc.queryForList("select r.room_id from room_supported_services r join clinical_service_orders o"
                            + " on o.service_id = r.service_id where o.id = ? order by r.room_id", UUID.class, orderId);
                }
                @Override public void assignRoom(UUID orderId, UUID roomId) {
                    jdbc.update("update clinical_service_orders set room_id = ? where id = ?", roomId, orderId);
                }
            };
        }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired ServiceQueueService serviceQueue;
    @Autowired ResultReviewQueueService resultReview;
    @Autowired fu.fa26.sep490.g58.otcms_be.visit.service.ServiceWorklistQueryService worklist;
    @Autowired ApplicationEventPublisher events;
    @Autowired TransactionTemplate tx;
    @MockitoBean BillingQueryService billing;

    @AfterAll static void dropSchema(@Autowired JdbcTemplate jdbc) { jdbc.execute("drop schema if exists " + SCHEMA + " cascade"); }

    @Test void paidOrderGoesToTheRoomWithFewestWaitingPatients() {
        Ctx c = ctx();
        UUID busy = room(), quiet = room(), service = service(c, busy, quiet);
        waiting(busy, 1); waiting(busy, 2); waiting(quiet, 1);
        waiting(quiet, 2); // quiet has 2, busy has 2: make busy the longer one
        waiting(busy, 3);
        UUID visit = visit(c), order = order(c, visit, service, 1);
        paid(order);
        serviceQueue.admitPaidOrder(order);
        assertEquals(quiet, jdbc.queryForObject("select room_id from clinical_service_orders where id = ?", UUID.class, order));
        assertEquals(1, count("select count(*) from queue_entries where service_order_id = ? and stage = 'SERVICE' and status = 'WAITING' and room_id = ?", order, quiet));
        assertEquals(3, jdbc.queryForObject("select queue_number from queue_entries where service_order_id = ?", Integer.class, order));
        assertEquals("PAID", jdbc.queryForObject("select payment_status from clinical_service_orders where id = ?", String.class, order));
    }

    @Test void unpaidOrderIsNotAdmitted() {
        Ctx c = ctx();
        UUID room = room(), service = service(c, room), visit = visit(c), order = order(c, visit, service, 1);
        when(billing.isServiceOrderPaid(order)).thenReturn(false);
        serviceQueue.admitPaidOrder(order);
        assertEquals(0, count("select count(*) from queue_entries where service_order_id = ?", order));
        assertNull(jdbc.queryForObject("select room_id from clinical_service_orders where id = ?", UUID.class, order));
    }

    @Test void paymentEventAdmitsTheOrderAfterCommit() {
        Ctx c = ctx();
        UUID room = room(), service = service(c, room), visit = visit(c), order = order(c, visit, service, 1);
        paid(order);
        tx.executeWithoutResult(status -> events.publishEvent(new ServiceOrderPaidEvent(order, UUID.randomUUID(), UUID.randomUUID())));
        assertEquals(1, count("select count(*) from queue_entries where service_order_id = ? and room_id = ?", order, room));
    }

    @Test void patientWaitsInOneRoomAtATimeAndMovesOnWhenTheStayEnds() {
        Ctx c = ctx();
        UUID roomA = room(), roomB = room(), s1 = service(c, roomA), s2 = service(c, roomB), visit = visit(c);
        UUID first = order(c, visit, s1, 1), second = order(c, visit, s2, 2);
        paid(first); paid(second);
        serviceQueue.admitPaidOrder(first);
        serviceQueue.admitPaidOrder(second);
        assertEquals(1, count("select count(*) from queue_entries where visit_id = ? and stage = 'SERVICE'", visit));
        assertEquals(roomB, jdbc.queryForObject("select room_id from clinical_service_orders where id = ?", UUID.class, second));
        tx.executeWithoutResult(status -> serviceQueue.complete(first));
        assertEquals("COMPLETED", jdbc.queryForObject("select status from queue_entries where service_order_id = ?", String.class, first));
        assertEquals(roomB, jdbc.queryForObject("select room_id from queue_entries where service_order_id = ?", UUID.class, second));
        assertEquals("WAITING", jdbc.queryForObject("select status from queue_entries where service_order_id = ?", String.class, second));
    }

    @Test void leavingTheRoomAlsoMovesToTheNextOrder() {
        Ctx c = ctx();
        UUID roomA = room(), roomB = room(), s1 = service(c, roomA), s2 = service(c, roomB), visit = visit(c);
        UUID first = order(c, visit, s1, 1), second = order(c, visit, s2, 2);
        paid(first); paid(second);
        serviceQueue.admitPaidOrder(first);
        serviceQueue.admitPaidOrder(second);
        tx.executeWithoutResult(status -> serviceQueue.leave(first));
        assertEquals("CANCELLED", jdbc.queryForObject("select status from queue_entries where service_order_id = ?", String.class, first));
        assertEquals(1, count("select count(*) from queue_entries where service_order_id = ? and status = 'WAITING'", second));
    }

    @Test void nextOrderInTheSameRoomKeepsThePlaceOfTheFinishedStay() {
        Ctx c = ctx();
        UUID room = room(), s1 = service(c, room), s2 = service(c, room), visit = visit(c);
        UUID first = order(c, visit, s1, 1), second = order(c, visit, s2, 2);
        paid(first); paid(second);
        serviceQueue.admitPaidOrder(first);
        serviceQueue.admitPaidOrder(second);
        long placeOfFirst = jdbc.queryForObject("select sort_order from queue_entries where service_order_id = ?", Long.class, first);
        tx.executeWithoutResult(status -> serviceQueue.complete(first));
        assertEquals(placeOfFirst, jdbc.queryForObject("select sort_order from queue_entries where service_order_id = ?", Long.class, second));
    }

    @Test void technicianCannotStartBeforeTheCallAndStartsAfterIt() {
        Ctx c = ctx();
        UUID room = room(), service = service(c, room), visit = visit(c), order = order(c, visit, service, 1);
        paid(order);
        serviceQueue.admitPaidOrder(order);
        assertEquals("MSG-42", assertThrows(VisitOperationException.class,
                () -> tx.executeWithoutResult(status -> serviceQueue.markInProgress(order))).code());
        jdbc.update("update queue_entries set status = 'CALLED', called_at = now() where service_order_id = ?", order);
        tx.executeWithoutResult(status -> serviceQueue.markInProgress(order));
        assertEquals("IN_EXAMINATION", jdbc.queryForObject("select status from queue_entries where service_order_id = ?", String.class, order));
    }

    @Test void orderWithoutQueueEntryStillStarts() {
        Ctx c = ctx();
        UUID room = room(), service = service(c, room), visit = visit(c), order = order(c, visit, service, 1);
        assertDoesNotThrow(() -> tx.executeWithoutResult(status -> serviceQueue.markInProgress(order)));
    }

    @Test void resultReviewEntryAndMsg74AppearOnlyWhenTheWholeLatestRoundHasResults() {
        Ctx c = ctx();
        UUID doctorRoom = room(), service = service(c, doctorRoom), visit = visit(c);
        exam(visit, c.doctor, doctorRoom);
        UUID round = UUID.randomUUID();
        UUID one = order(c, visit, service, 1, round), two = order(c, visit, service, 1, round);
        jdbc.update("update clinical_service_orders set order_status = 'RESULTS_AVAILABLE' where id = ?", one);
        tx.executeWithoutResult(status -> resultReview.onOrderResultAvailable(one));
        assertEquals(0, count("select count(*) from queue_entries where visit_id = ? and stage = 'RESULT_REVIEW'", visit));

        jdbc.update("update clinical_service_orders set order_status = 'RESULTS_AVAILABLE' where id = ?", two);
        tx.executeWithoutResult(status -> resultReview.onOrderResultAvailable(two));
        tx.executeWithoutResult(status -> resultReview.onOrderResultAvailable(two)); // idempotent
        assertEquals(1, count("select count(*) from queue_entries where visit_id = ? and stage = 'RESULT_REVIEW' and room_id = ? and status = 'WAITING'", visit, doctorRoom));
        assertEquals(1, count("select count(*) from notifications where recipient_account_id = ? and related_id = ? and type = 'RESULT_REVIEW_READY'", c.patientAccount, visit));
        String content = jdbc.queryForObject("select content from notifications where recipient_account_id = ? and related_id = ?", String.class, c.patientAccount, visit);
        assertTrue(content.startsWith("Đã có đủ kết quả cận lâm sàng của Queue Patient"), content);
    }

    @Test void cancelledOrdersDoNotBlockTheResultReview() {
        Ctx c = ctx();
        UUID doctorRoom = room(), service = service(c, doctorRoom), visit = visit(c);
        exam(visit, c.doctor, doctorRoom);
        UUID round = UUID.randomUUID();
        UUID one = order(c, visit, service, 1, round), two = order(c, visit, service, 1, round);
        jdbc.update("update clinical_service_orders set order_status = 'CANCELLED', cancel_reason = 'x' where id = ?", two);
        jdbc.update("update clinical_service_orders set order_status = 'RESULTS_AVAILABLE' where id = ?", one);
        tx.executeWithoutResult(status -> resultReview.onOrderResultAvailable(one));
        assertEquals(1, count("select count(*) from queue_entries where visit_id = ? and stage = 'RESULT_REVIEW'", visit));
    }

    @Test void closingTheVisitClearsOpenServiceAndResultReviewEntries() {
        Ctx c = ctx();
        UUID room = room(), service = service(c, room), visit = visit(c), order = order(c, visit, service, 1);
        paid(order);
        serviceQueue.admitPaidOrder(order);
        exam(visit, c.doctor, room);
        jdbc.update("update clinical_service_orders set order_status = 'RESULTS_AVAILABLE' where id = ?", order);
        tx.executeWithoutResult(status -> resultReview.onOrderResultAvailable(order));
        assertEquals(1, count("select count(*) from queue_entries where visit_id = ? and stage = 'RESULT_REVIEW' and status = 'WAITING'", visit));
        tx.executeWithoutResult(status -> events.publishEvent(new fu.fa26.sep490.g58.otcms_be.visit.event.VisitCompletedEvent(visit, c.doctor)));
        assertEquals("COMPLETED", jdbc.queryForObject("select status from queue_entries where visit_id = ? and stage = 'RESULT_REVIEW'", String.class, visit));
        assertEquals("CANCELLED", jdbc.queryForObject("select status from queue_entries where service_order_id = ?", String.class, order));
    }

    @Test void worklistShowsPatientHeaderAndOrdersOfTheRoomWithPaymentFlag() {
        Ctx c = ctx();
        UUID room = room(), service = service(c, room), visit = visit(c), order = order(c, visit, service, 1);
        jdbc.update("update clinical_service_orders set instructions = 'Remove jewelry', body_area = 'Knee' where id = ?", order);
        paid(order);
        serviceQueue.admitPaidOrder(order);
        var receptionist = new fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount(c.receptionist,
                fu.fa26.sep490.g58.otcms_be.common.security.AccountRole.RECEPTIONIST, UUID.randomUUID());
        var list = worklist.worklist(room, receptionist);
        assertEquals(1, list.entries().size());
        var entry = list.entries().getFirst();
        assertEquals("Queue Patient", entry.patient().fullName());
        assertEquals(1, entry.orders().size());
        assertEquals("Other service", entry.orders().getFirst().serviceName());
        assertEquals("Remove jewelry", entry.orders().getFirst().instructions());
        assertTrue(entry.orders().getFirst().paid());
        when(billing.isServiceOrderPaid(order)).thenReturn(false);
        assertFalse(worklist.worklist(room, receptionist).entries().getFirst().orders().getFirst().paid());
        assertEquals("MSG-40", assertThrows(VisitOperationException.class, () -> worklist.worklist(room,
                new fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount(c.patientAccount,
                        fu.fa26.sep490.g58.otcms_be.common.security.AccountRole.PATIENT, UUID.randomUUID()))).code());
        assertEquals("MSG-41", assertThrows(VisitOperationException.class, () -> worklist.worklist(UUID.randomUUID(), receptionist)).code());
    }

    // ---- fixtures ----------------------------------------------------------------------------------------------

    private record Ctx(UUID receptionist, UUID patientAccount, UUID patient, UUID doctor) { }

    private Ctx ctx() {
        UUID receptionist = account("RECEPTIONIST"), doctorAccount = account("DOCTOR"), patientAccount = account("PATIENT");
        UUID patient = UUID.randomUUID(), doctor = UUID.randomUUID();
        jdbc.update("insert into patient_profiles (id, account_id, full_name, phone_canonical, phone_owner_type, date_of_birth, gender) values (?, ?, 'Queue Patient', ?, 'PATIENT', '1990-01-01', 'FEMALE')",
                patient, patientAccount, "+849" + patient.toString().replace("-", "").substring(0, 9));
        jdbc.update("insert into staff_profiles (is_doctor, id, account_id, display_name, publicly_visible, accepting_new_bookings, in_active_directory) values (true, ?, ?, 'Queue Doctor', true, true, true)", doctor, doctorAccount);
        return new Ctx(receptionist, patientAccount, patient, doctor);
    }

    private UUID account(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into accounts (id, normalized_email, account_type, status, created_at, updated_at) values (?, ?, ?, 'ACTIVE', now(), now())", id, id + "@example.test", role);
        return id;
    }

    private UUID room() {
        UUID room = UUID.randomUUID();
        jdbc.update("insert into rooms (id, room_code, display_label, active) values (?, ?, 'Service Room', true)", room, "S-" + room.toString().substring(0, 8));
        return room;
    }

    private UUID service(Ctx c, UUID... rooms) {
        UUID service = UUID.randomUUID();
        jdbc.update("insert into clinic_services (id, name, duration_minutes, buffer_minutes, price) values (?, ?, 30, 0, 100)", service, "Service " + service);
        for (UUID room : rooms) jdbc.update("insert into room_supported_services (room_id, service_id) values (?, ?)", room, service);
        return service;
    }

    private UUID visit(Ctx c) {
        UUID visit = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("insert into visits (id, patient_profile_id, source, arrived_at, checked_in_at, created_by_account_id) values (?, ?, 'WALK_IN', ?, ?, ?)",
                visit, c.patient, Timestamp.from(now), Timestamp.from(now), c.receptionist);
        return visit;
    }

    private UUID order(Ctx c, UUID visit, UUID service, int roundNumber) {
        return order(c, visit, service, roundNumber, UUID.randomUUID());
    }

    /** The round row is inserted by the first order of the round; the next order of the same round finds it. */
    private UUID order(Ctx c, UUID visit, UUID service, int roundNumber, UUID round) {
        UUID order = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("insert into clinical_ordering_rounds (id, visit_id, round_number, ordered_by_doctor_id, ordered_at, created_at) values (?, ?, ?, ?, ?, ?) on conflict (id) do nothing",
                round, visit, roundNumber, c.doctor, now, now);
        jdbc.update("insert into clinical_service_orders (id, order_code, ordering_round_id, visit_id, patient_id, service_id, service_name_snapshot, service_type, requesting_doctor_id, clinical_reason, price_snapshot, order_status, payment_status, created_at, updated_at) values (?, ?, ?, ?, ?, ?, 'Other service', 'OTHER', ?, 'Pain', 100, 'ORDERED', 'PENDING_PAYMENT', ?, ?)",
                order, "O-" + order.toString().substring(0, 8), round, visit, c.patient, service, c.doctor, now, now);
        return order;
    }

    private void paid(UUID order) { when(billing.isServiceOrderPaid(order)).thenReturn(true); }

    /** Another patient already waiting in the room, so the room counts as busier. */
    private void waiting(UUID room, int number) {
        Ctx other = ctx();
        UUID visit = visit(other), service = service(other, room), order = order(other, visit, service, 1);
        jdbc.update("update clinical_service_orders set room_id = ? where id = ?", room, order);
        jdbc.update("insert into queue_entries (visit_id, stage, status, entered_at, room_id, service_order_id, clinic_date, queue_number) values (?, 'SERVICE', 'WAITING', now(), ?, ?, ?, ?)",
                visit, room, order, TODAY, number);
    }

    private void exam(UUID visit, UUID doctor, UUID room) {
        jdbc.update("insert into queue_entries (visit_id, stage, status, entered_at, doctor_profile_id, room_id, clinic_date, queue_number, queued_at) values (?, 'EXAM', 'AWAITING_CONCLUSION', now(), ?, ?, ?, 1, now())",
                visit, doctor, room, TODAY);
    }

    private int count(String sql, Object... args) { return jdbc.queryForObject(sql, Integer.class, args); }
}
