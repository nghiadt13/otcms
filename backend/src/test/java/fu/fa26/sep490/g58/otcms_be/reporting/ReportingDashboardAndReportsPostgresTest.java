package fu.fa26.sep490.g58.otcms_be.reporting;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-77 dashboard sources and UC-78 report topics against real Postgres (rolled back per test). The period is in 2031,
 * so rows left by other test classes never fall inside it; snapshot metrics are asserted as before/after deltas.
 */
@SpringBootTest @LocalPostgresTest @Transactional @AutoConfigureMockMvc
class ReportingDashboardAndReportsPostgresTest {
    static final String FROM = "2031-03-01", TO = "2031-03-03";
    static final Instant DAY1 = Instant.parse("2031-03-01T03:00:00Z"); // 10:00 Vietnam
    static final Instant DAY2 = Instant.parse("2031-03-02T03:00:00Z");
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    final UUID receptionistAccount = UUID.randomUUID(), patientAccount = UUID.randomUUID(), doctorAccount = UUID.randomUUID();
    final UUID profile = UUID.randomUUID(), doctor = UUID.randomUUID(), service = UUID.randomUUID(), visit = UUID.randomUUID();
    final UUID room = UUID.randomUUID(), invoice = UUID.randomUUID();
    int sequence, roundNumber;

    @BeforeEach void fixtures() {
        account(receptionistAccount, "RECEPTIONIST"); account(patientAccount, "PATIENT"); account(doctorAccount, "DOCTOR");
        jdbc.update("update accounts set full_name='Lễ tân Hoa' where id=?", receptionistAccount);
        jdbc.update("insert into staff_profiles(id,account_id,display_name,is_doctor) values (?,?,'Lễ tân Hoa',false)", UUID.randomUUID(), receptionistAccount);
        jdbc.update("insert into staff_profiles(is_doctor,id,account_id,display_name,publicly_visible,accepting_new_bookings,in_active_directory) values (true,?,?,'Bác sĩ Minh',true,true,true)", doctor, doctorAccount);
        jdbc.update("insert into patient_profiles(id,account_id,full_name,phone_canonical,phone_owner_type,date_of_birth,gender) values (?,?,'Báo cáo Bệnh nhân',?,'PATIENT',date '1990-01-01','OTHER')",
                profile, patientAccount, "+849" + String.format("%09d", Math.floorMod(profile.getLeastSignificantBits(), 1_000_000_000L)));
        jdbc.update("insert into clinic_services(id,name,duration_minutes,buffer_minutes,online_bookable,active,price) values (?,'Chụp X-quang báo cáo',30,0,false,true,100000)", service);
        jdbc.update("insert into rooms(id,room_code,display_label) values (?,?,'Phòng báo cáo')", room, "P10-" + room.toString().substring(0, 8));
        visit(visit);
        jdbc.update("insert into invoices(id,invoice_number,patient_profile_id,visit_id,source_type,source_fingerprint,status,created_by_account_id,issued_at) values (?,?,?,?,'VISIT',?,'OPEN',?,?)",
                invoice, "INV-P10-" + invoice.toString().substring(0, 8), profile, visit, "a".repeat(64), receptionistAccount, Timestamp.from(DAY1));
    }

    @Test void dashboardDefaultsToTodayWhenNoPeriodIsGiven() throws Exception {
        String today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).toString();
        mvc.perform(get("/api/v1/admin/dashboard").with(admin())).andExpect(status().isOk())
                .andExpect(jsonPath("$.period.from").value(today)).andExpect(jsonPath("$.period.to").value(today));
        mvc.perform(get("/api/v1/admin/dashboard?from=" + FROM).with(admin())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MSG-72"));
    }

    @Test void dashboardPaymentsSplitByMethodAndExcludeUnconfirmedAttempts() throws Exception {
        counterPayment(300_000, DAY1); onlinePayment(200_000, DAY2);
        attempt("PENDING", "ONLINE", 999_000, null);                       // never counted
        counterPayment(70_000, DAY1.minusSeconds(86400 * 30));              // outside the period
        mvc.perform(get("/api/v1/admin/dashboard?from=" + FROM + "&to=" + TO).with(admin())).andExpect(status().isOk())
                .andExpect(jsonPath("$.payments.status").value("READY"))
                .andExpect(jsonPath("$.payments.collected.value").value(500_000))
                .andExpect(jsonPath("$.payments.byMethod[?(@.code=='CASH')].value").value(300_000))
                .andExpect(jsonPath("$.payments.byMethod[?(@.code=='PAYOS')].value").value(200_000));
    }

    @Test void dashboardImagingOrdersAndReviewsBlocksAreFilled() throws Exception {
        long refundBefore = jdbc.queryForObject("select count(*) from invoice_items where refund_status='REFUND_PENDING'", Long.class);
        long pendingBefore = jdbc.queryForObject("select count(*) from patient_reviews where status='PENDING'", Long.class);
        imaging("REVIEWED", DAY1); imaging("RESULT_AVAILABLE", DAY2); imaging("PENDING", DAY1);   // pending is not performed
        order("ORDERED", "PENDING_PAYMENT", "OTHER", DAY1); order("ORDERED", "PAID", "OTHER", DAY1);
        order("CANCELLED", "PENDING_PAYMENT", "OTHER", DAY2);
        review(5, "PUBLISHED", DAY1); review(4, "PUBLISHED", DAY2); review(1, "HIDDEN", DAY1); review(3, "PENDING", DAY1);
        item(1, 80_000, "REFUND_PENDING");
        mvc.perform(get("/api/v1/admin/dashboard?from=" + FROM + "&to=" + TO).with(admin())).andExpect(status().isOk())
                .andExpect(jsonPath("$.imagingAndLab.status").value("READY"))
                .andExpect(jsonPath("$.imagingAndLab.imagingPerformed.value").value(2))
                .andExpect(jsonPath("$.imagingAndLab.labStatus").value("NO_SOURCE"))
                .andExpect(jsonPath("$.pendingAndCancelled.pendingPayment.value").value(1))
                .andExpect(jsonPath("$.pendingAndCancelled.cancelled.value").value(1))
                .andExpect(jsonPath("$.pendingAndCancelled.refundPending.value").value(refundBefore + 1))
                .andExpect(jsonPath("$.reviews.averageRating.value").value(4.5))
                .andExpect(jsonPath("$.reviews.pendingApproval.value").value(pendingBefore + 1));
    }

    @Test void dashboardListsRoomsWithPeopleWaitingNow() throws Exception {
        String today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).toString();
        jdbc.update("insert into queue_entries(visit_id,stage,status,entered_at,sort_order,doctor_profile_id,room_id,clinic_date,queue_number) "
                + "values (?,'EXAM','WAITING',now(),1,?,?,date '" + today + "',1)", visit, doctor, room);
        mvc.perform(get("/api/v1/admin/dashboard?from=" + FROM + "&to=" + TO).with(admin())).andExpect(status().isOk())
                .andExpect(jsonPath("$.visits.waitingByRoomStatus").value("READY"))
                .andExpect(jsonPath("$.visits.waitingByRoom[?(@.roomName=='Phòng báo cáo')].value").value(1));
    }

    @Test void paymentsReportBreaksDownByDateReceptionistAndMethodWithMatchingTotals() throws Exception {
        counterPayment(300_000, DAY1); counterPayment(100_000, DAY2); onlinePayment(200_000, DAY2);
        for (String breakdown : new String[] {"DATE", "RECEPTIONIST", "METHOD"}) {
            mvc.perform(report("PAYMENTS", breakdown)).andExpect(status().isOk()).andExpect(jsonPath("$.hasData").value(true))
                    .andExpect(jsonPath("$.sections[0].totalRow.count").value(3))
                    .andExpect(jsonPath("$.sections[0].totalRow.amount").value(600_000))
                    .andExpect(jsonPath("$.sections[0].columns[2].kind").value("money"));
        }
        mvc.perform(report("PAYMENTS", "DATE")).andExpect(jsonPath("$.sections[0].rows[0].date").value("2031-03-01"))
                .andExpect(jsonPath("$.sections[0].rows[0].amount").value(300_000))
                .andExpect(jsonPath("$.sections[0].rows[1].amount").value(300_000));
        mvc.perform(report("PAYMENTS", "RECEPTIONIST")).andExpect(jsonPath("$.sections[0].rows[?(@.receptionist=='Lễ tân Hoa')].amount").value(400_000))
                .andExpect(jsonPath("$.sections[0].rows[?(@.receptionist=='Thanh toán trực tuyến (không qua lễ tân)')].amount").value(200_000));
        mvc.perform(report("PAYMENTS", "METHOD")).andExpect(jsonPath("$.sections[0].rows[?(@.method=='Tiền mặt')].count").value(2))
                .andExpect(jsonPath("$.sections[0].rows[?(@.method=='PayOS (trực tuyến)')].count").value(1));
    }

    @Test void ordersImagingAndReviewsReportsFollowTheirSources() throws Exception {
        order("ORDERED", "PENDING_PAYMENT", "OTHER", DAY1); order("CANCELLED", "PENDING_PAYMENT", "OTHER", DAY1);
        imaging("REVIEWED", DAY1); imaging("REVIEWED", DAY2);
        review(5, "PUBLISHED", DAY1); review(4, "PUBLISHED", DAY1);
        mvc.perform(report("PENDING_CANCELLED_ORDERS", null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.sections[0].rows[?(@.group=='Chờ thanh toán')].count").value(1))
                .andExpect(jsonPath("$.sections[0].rows[?(@.group=='Đã hủy')].count").value(1))
                .andExpect(jsonPath("$.sections[0].rows[?(@.group=='Chờ hoàn tiền')].count").value(0));
        mvc.perform(report("IMAGING", "DATE")).andExpect(status().isOk()).andExpect(jsonPath("$.sections[0].totalRow.count").value(2))
                .andExpect(jsonPath("$.sections[0].rows[0].date").value("2031-03-01"));
        mvc.perform(report("REVIEWS", "DATE")).andExpect(status().isOk()).andExpect(jsonPath("$.sections[0].rows[0].count").value(2))
                .andExpect(jsonPath("$.sections[0].rows[0].average").value(4.5)).andExpect(jsonPath("$.sections[0].columns[2].kind").value("score"));
    }

    @Test void csvExportHasUtf8BomVietnameseHeadersAndTheSameTotalAsTheScreen() throws Exception {
        counterPayment(300_000, DAY1); onlinePayment(200_000, DAY2);
        var response = mvc.perform(get("/api/v1/admin/reports/export?subject=PAYMENTS&breakdown=METHOD&from=" + FROM + "&to=" + TO + "&format=csv").with(admin()))
                .andExpect(status().isOk()).andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("report-payments-2031-03-01-2031-03-03.csv")))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andReturn().getResponse().getContentAsByteArray();
        assertEquals((byte) 0xEF, response[0]); assertEquals((byte) 0xBB, response[1]); assertEquals((byte) 0xBF, response[2]);
        String csv = new String(response, 3, response.length - 3, StandardCharsets.UTF_8);
        String[] lines = csv.split("\r\n");
        assertEquals("Phương thức,Số giao dịch,Số tiền (VND)", lines[0]);
        assertTrue(csv.contains("Tiền mặt,1,300000"));
        assertTrue(csv.contains("PayOS (trực tuyến),1,200000"));
        assertEquals("Tổng,2,500000", lines[lines.length - 1]);
        // The screen's total is the same number.
        mvc.perform(report("PAYMENTS", "METHOD")).andExpect(jsonPath("$.sections[0].totalRow.amount").value(500_000));
    }

    @Test void csvExportRejectsBadSubjectFormatAndPeriodWithMsg72AndBlocksOtherRoles() throws Exception {
        String base = "/api/v1/admin/reports/export?from=" + FROM + "&to=" + TO;
        mvc.perform(get(base + "&subject=LAB").with(admin())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("MSG-72"));
        mvc.perform(get(base + "&subject=PAYMENTS&format=pdf").with(admin())).andExpect(status().isBadRequest());
        mvc.perform(get(base + "&subject=SERVICES&breakdown=DATE").with(admin())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/reports/export?subject=VISITS&from=2031-03-09&to=2031-03-01").with(admin())).andExpect(status().isBadRequest());
        mvc.perform(get(base + "&subject=VISITS").with(user("rec").roles("RECEPTIONIST"))).andExpect(status().isForbidden());
        mvc.perform(get(base + "&subject=VISITS").with(user("owner").roles("OWNER"))).andExpect(status().isOk());
    }

    @Test void csvEscapesCommasQuotesAndFormulaStartsInUserText() throws Exception {
        jdbc.update("update staff_profiles set display_name='=Hoa, \"A\"' where account_id=?", receptionistAccount);
        counterPayment(10_000, DAY1);
        var bytes = mvc.perform(get("/api/v1/admin/reports/export?subject=PAYMENTS&breakdown=RECEPTIONIST&from=" + FROM + "&to=" + TO).with(admin()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        String csv = new String(bytes, StandardCharsets.UTF_8);
        assertTrue(csv.contains("\"'=Hoa, \"\"A\"\"\",1,10000"), csv);
    }

    // ---- fixtures -------------------------------------------------------------------------------------------------
    private org.springframework.test.web.servlet.request.RequestPostProcessor admin() { return user("admin").roles("ADMIN"); }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder report(String subject, String breakdown) {
        return post("/api/v1/admin/reports").with(admin()).with(csrf()).contentType("application/json").content(
                "{\"subject\":\"" + subject + "\",\"from\":\"" + FROM + "\",\"to\":\"" + TO + "\"" + (breakdown == null ? "" : ",\"breakdown\":\"" + breakdown + "\"") + "}");
    }
    private void account(UUID id, String role) {
        jdbc.update("insert into accounts(id,normalized_email,account_type,status,password_hash,created_at,updated_at) values (?,?,?,'ACTIVE','test',now(),now())", id, "p10-" + id + "@example.test", role);
    }
    private void visit(UUID id) {
        jdbc.update("insert into visits(id,patient_profile_id,source,arrived_at,checked_in_at,created_by_account_id,status) values (?,?,'WALK_IN',?,?,?,'OPEN')",
                id, profile, Timestamp.from(DAY1), Timestamp.from(DAY1), receptionistAccount);
    }
    private UUID attempt(String status, String channel, long amount, Instant confirmedAt) {
        UUID id = UUID.randomUUID();
        boolean counter = channel.equals("COUNTER");
        jdbc.update("insert into payment_attempts(id,invoice_id,channel,counter_method,provider,provider_order_code,amount,status,created_by_account_id,created_at,confirmed_at) "
                + "values (?,?,?,?,?,?,?,?,?,?,?)", id, invoice, channel, counter ? "CASH" : null, counter ? null : "PAYOS",
                counter ? null : (long) (900_000_000L + (++sequence) + Math.floorMod(id.getLeastSignificantBits(), 1_000_000L) * 1000), amount, status,
                counter ? receptionistAccount : patientAccount, Timestamp.from(confirmedAt == null ? DAY1 : confirmedAt),
                confirmedAt == null ? null : Timestamp.from(confirmedAt));
        return id;
    }
    private void counterPayment(long amount, Instant at) { attempt("CONFIRMED", "COUNTER", amount, at); }
    private void onlinePayment(long amount, Instant at) { attempt("CONFIRMED", "ONLINE", amount, at); }
    private void item(int line, long price, String refundStatus) {
        jdbc.update("insert into invoice_items(invoice_id,line_number,service_id,description_snapshot,quantity,unit_price_snapshot,refund_status) values (?,?,?,'Khoản chờ hoàn',1,?,?)",
                invoice, line, service, price, refundStatus);
    }
    private void order(String orderStatus, String paymentStatus, String type, Instant orderedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into service_orders(id,order_code,ordering_round_id,visit_id,patient_id,service_id,service_name_snapshot,service_type,requesting_doctor_id,clinical_reason,"
                + "price_snapshot,order_status,payment_status,created_at,updated_at) values (?,?,?,?,?,?,'Chỉ định',?,?,'lý do',100000,?,?,?,?)",
                id, "P10-" + id.toString().substring(0, 18), newRound(orderedAt), visit, profile, service, type, doctor, orderStatus, paymentStatus,
                Timestamp.from(orderedAt), Timestamp.from(orderedAt));
    }
    /** One round per fixture order: the round carries ordered_at, which the order reports filter on. */
    private UUID newRound(Instant orderedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into service_order_rounds(id,visit_id,round_number,ordered_by_doctor_id,ordered_at,created_at) values (?,?,?,?,?,?)",
                id, visit, ++roundNumber, doctor, Timestamp.from(orderedAt), Timestamp.from(orderedAt));
        return id;
    }
    private void imaging(String executionStatus, Instant at) {
        UUID id = UUID.randomUUID();
        boolean done = !executionStatus.equals("PENDING");
        jdbc.update("insert into service_orders(id,order_code,ordering_round_id,visit_id,patient_id,service_id,service_name_snapshot,service_type,requesting_doctor_id,clinical_reason,"
                + "price_snapshot,order_status,payment_status,created_at,updated_at,room_id) values (?,?,?,?,?,?,'Chụp X-quang','IMAGING',?,'lý do',100000,'ORDERED','PAID',?,?,?)",
                id, "P10-" + id.toString().substring(0, 18), newRound(at), visit, profile, service, doctor,
                Timestamp.from(at), Timestamp.from(at), done ? room : null);
        jdbc.update("insert into imaging_executions(service_order_id,imaging_type,ordering_doctor_account_id,execution_status,attempt_id,study_ref,first_use_at,created_at,updated_at) "
                + "values (?,'XRAY',?,?,?,?,?,?,?)",
                id, doctorAccount, executionStatus, done ? UUID.randomUUID() : null, done ? "study-" + id : null,
                done ? Timestamp.from(at) : null, Timestamp.from(at), Timestamp.from(at));
    }
    private void review(int rating, String status, Instant at) {
        UUID reviewVisit = UUID.randomUUID();
        visit(reviewVisit);
        jdbc.update("update visits set status='CLOSED' where id=?", reviewVisit);
        jdbc.update("insert into patient_reviews(id,visit_id,patient_profile_id,author_account_id,rating,content,status,submitted_at,updated_at) values (?,?,?,?,?,'Nội dung đánh giá đủ dài',?,?,?)",
                UUID.randomUUID(), reviewVisit, profile, patientAccount, rating, status, Timestamp.from(at), Timestamp.from(at));
    }
}
