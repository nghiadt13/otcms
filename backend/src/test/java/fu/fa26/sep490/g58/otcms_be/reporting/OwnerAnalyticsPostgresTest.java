package fu.fa26.sep490.g58.otcms_be.reporting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owner dashboard against real Postgres (rolled back per test). The core promise: a payment confirmed for a customer
 * raises exactly its day, its method, its doctor and its service, and every breakdown adds up to the revenue.
 * The period is in 2031, so rows left by other test classes never fall inside it.
 */
@SpringBootTest @LocalPostgresTest @Transactional @AutoConfigureMockMvc
class OwnerAnalyticsPostgresTest {
    static final String FROM = "2031-03-01", TO = "2031-03-03";
    static final Instant DAY1 = Instant.parse("2031-03-01T03:00:00Z"); // 10:00 Vietnam
    static final Instant DAY2 = Instant.parse("2031-03-02T03:00:00Z");
    static final Instant BEFORE = Instant.parse("2031-02-27T03:00:00Z"); // inside the previous period
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    final ObjectMapper json = new ObjectMapper();
    final UUID receptionist = UUID.randomUUID(), patientAccount = UUID.randomUUID(), profile = UUID.randomUUID();
    final UUID doctorAnAccount = UUID.randomUUID(), doctorBinhAccount = UUID.randomUUID();
    final UUID doctorAn = UUID.randomUUID(), doctorBinh = UUID.randomUUID();
    final UUID exam = UUID.randomUUID(), xray = UUID.randomUUID(), room = UUID.randomUUID();
    final UUID walkInVisit = UUID.randomUUID(), walkInInvoice = UUID.randomUUID();
    final Map<UUID, Integer> lines = new HashMap<>();
    int sequence, roundNumber, queueNumber;

    @BeforeEach void fixtures() {
        account(receptionist, "RECEPTIONIST"); account(patientAccount, "PATIENT");
        account(doctorAnAccount, "DOCTOR"); account(doctorBinhAccount, "DOCTOR");
        doctor(doctorAn, doctorAnAccount, "Bác sĩ An"); doctor(doctorBinh, doctorBinhAccount, "Bác sĩ Bình");
        // A booked appointment must name a doctor who offers the service.
        jdbc.update("insert into patient_profiles(id,account_id,full_name,phone_canonical,phone_owner_type,date_of_birth,gender) values (?,?,'Bệnh nhân Owner',?,'PATIENT',date '1990-01-01','OTHER')",
                profile, patientAccount, "+849" + String.format("%09d", Math.floorMod(profile.getLeastSignificantBits(), 1_000_000_000L)));
        jdbc.update("insert into clinic_services(id,name,duration_minutes,buffer_minutes,online_bookable,active,price,service_type) values (?,'Khám Owner',30,0,true,true,300000,'EXAMINATION')", exam);
        jdbc.update("insert into clinic_services(id,name,duration_minutes,buffer_minutes,online_bookable,active,price,service_type,modality) values (?,'Chụp X-quang Owner',30,0,false,true,100000,'IMAGING','XRAY')", xray);
        jdbc.update("insert into doctor_services(doctor_profile_id,service_id,active) values (?,?,true)", doctorAn, exam);
        jdbc.update("insert into doctor_services(doctor_profile_id,service_id,active) values (?,?,true)", doctorBinh, exam);
        jdbc.update("insert into rooms(id,room_code,display_label) values (?,?,'Phòng Owner')", room, "O-" + room.toString().substring(0, 8));
        // Walk-in visit examined by Bác sĩ An, closed on day 1.
        visit(walkInVisit, "CLOSED", DAY1);
        queue(walkInVisit, doctorAn);
        invoice(walkInInvoice, "VISIT", walkInVisit, null, null);
    }

    // ---- the promise -----------------------------------------------------------------------------------------------
    @Test void everyBreakdownAddsUpToTheRevenueAndEachFigureFollowsItsSource() throws Exception {
        UUID bookingInvoice = bookedAppointment(doctorBinh, DAY2);
        UUID roundInvoice = orderedRound(walkInVisit, doctorBinh, DAY2);
        pay(walkInInvoice, "COUNTER", "CONFIRMED", exam, 300_000, DAY1, null);            // cash, Bác sĩ An
        pay(bookingInvoice, "ONLINE", "CONFIRMED", exam, 300_000, DAY2, null);           // PayOS, Bác sĩ Bình (appointment doctor)
        pay(roundInvoice, "COUNTER_QR", "CONFIRMED", xray, 100_000, DAY2, null);         // PayOS, Bác sĩ Bình (ordering doctor)

        JsonNode body = analytics(FROM, TO);
        assertEquals(700_000, body.at("/revenue/total/value").asLong());
        assertEquals(300_000, body.at("/revenue/cash/value").asLong());
        assertEquals(400_000, body.at("/revenue/payos/value").asLong());
        assertEquals(3, body.at("/revenue/daily").size());
        assertEquals(300_000, body.at("/revenue/daily/0/cash").asLong());
        assertEquals(400_000, body.at("/revenue/daily/1/payos").asLong());
        assertEquals(0, body.at("/revenue/daily/2/cash").asLong() + body.at("/revenue/daily/2/payos").asLong());
        assertEquals("2031-03-01", body.at("/revenue/daily/0/date").asText());

        assertEquals(400_000, doctorRevenue(body, doctorBinh));
        assertEquals(300_000, doctorRevenue(body, doctorAn));
        assertEquals(1, doctor(body, doctorAn).path("visits").asLong());
        assertEquals(1, doctor(body, doctorAn).path("activeDays").asLong());
        assertEquals(600_000, service(body, exam).path("revenue").asLong());
        assertEquals(2, service(body, exam).path("count").asLong());
        assertEquals(100_000, service(body, xray).path("revenue").asLong());
        assertAddsUp(body);
    }

    @Test void aNewConfirmedPaymentRaisesExactlyItsDayMethodDoctorAndService() throws Exception {
        pay(walkInInvoice, "COUNTER", "CONFIRMED", exam, 300_000, DAY1, null);
        JsonNode before = analytics(FROM, TO);

        // A second customer pays 300,000 in cash on day 2 for an examination with Bác sĩ An.
        UUID secondVisit = UUID.randomUUID(), secondInvoice = UUID.randomUUID();
        visit(secondVisit, "CLOSED", DAY2); queue(secondVisit, doctorAn);
        invoice(secondInvoice, "VISIT", secondVisit, null, null);
        pay(secondInvoice, "COUNTER", "CONFIRMED", exam, 300_000, DAY2, null);

        JsonNode after = analytics(FROM, TO);
        assertEquals(before.at("/revenue/total/value").asLong() + 300_000, after.at("/revenue/total/value").asLong());
        assertEquals(before.at("/revenue/cash/value").asLong() + 300_000, after.at("/revenue/cash/value").asLong());
        assertEquals(before.at("/revenue/payos/value").asLong(), after.at("/revenue/payos/value").asLong());
        assertEquals(before.at("/revenue/daily/0/cash").asLong(), after.at("/revenue/daily/0/cash").asLong());
        assertEquals(before.at("/revenue/daily/1/cash").asLong() + 300_000, after.at("/revenue/daily/1/cash").asLong());
        assertEquals(doctorRevenue(before, doctorAn) + 300_000, doctorRevenue(after, doctorAn));
        assertEquals(2, doctor(after, doctorAn).path("visits").asLong());
        assertEquals(2, doctor(after, doctorAn).path("activeDays").asLong());
        assertEquals(service(before, exam).path("revenue").asLong() + 300_000, service(after, exam).path("revenue").asLong());
        assertAddsUp(after);
    }

    // ---- what must not count ---------------------------------------------------------------------------------------
    @Test void onlyConfirmedMoneyInThePeriodCountsAndRefundedItemsAreTakenOut() throws Exception {
        pay(walkInInvoice, "COUNTER", "CONFIRMED", exam, 300_000, DAY1, null);
        UUID other = UUID.randomUUID(), otherVisit = UUID.randomUUID();
        visit(otherVisit, "CLOSED", DAY1); queue(otherVisit, doctorAn); invoice(other, "VISIT", otherVisit, null, null);
        pay(other, "ONLINE", "PENDING", exam, 999_000, null, null);                       // never confirmed
        pay(other, "COUNTER", "CONFIRMED", exam, 70_000, DAY1.minusSeconds(86400L * 30), null); // outside both periods
        pay(other, "COUNTER", "CONFIRMED", exam, 80_000, DAY2, "REFUNDED");               // paid, then refunded in full
        pay(other, "COUNTER", "CONFIRMED", exam, 50_000, DAY2, "REFUND_PENDING");         // still held, not refunded yet

        JsonNode body = analytics(FROM, TO);
        assertEquals(350_000, body.at("/revenue/total/value").asLong());
        assertEquals(350_000, body.at("/revenue/cash/value").asLong());
        assertAddsUp(body);
    }

    @Test void moneyThatCannotBeTracedToADoctorIsListedAsUnknownAndStillAddsUp() throws Exception {
        UUID strayVisit = UUID.randomUUID(), strayInvoice = UUID.randomUUID();
        visit(strayVisit, "CLOSED", DAY1);                                                 // no examination queue entry
        invoice(strayInvoice, "VISIT", strayVisit, null, null);
        pay(strayInvoice, "COUNTER", "CONFIRMED", exam, 300_000, DAY1, null);
        pay(walkInInvoice, "COUNTER", "CONFIRMED", exam, 300_000, DAY1, null);

        JsonNode body = analytics(FROM, TO);
        JsonNode last = body.at("/doctors").get(body.at("/doctors").size() - 1);
        assertEquals(true, last.path("doctorId").isNull());
        assertEquals("Không xác định", last.path("name").asText());
        assertEquals(300_000, last.path("revenue").asLong());
        assertAddsUp(body);
    }

    @Test void thePreviousPeriodIsComparedDayByDayAndPerMeasure() throws Exception {
        UUID oldVisit = UUID.randomUUID(), oldInvoice = UUID.randomUUID();
        visit(oldVisit, "CLOSED", BEFORE); queue(oldVisit, doctorAn); invoice(oldInvoice, "VISIT", oldVisit, null, null);
        pay(oldInvoice, "ONLINE", "CONFIRMED", exam, 300_000, BEFORE, null);               // 2031-02-27 = day 2 of the previous period
        pay(walkInInvoice, "COUNTER", "CONFIRMED", exam, 300_000, DAY1, null);

        JsonNode body = analytics(FROM, TO);
        assertEquals("2031-02-26", body.at("/previousPeriod/from").asText());
        assertEquals("2031-02-28", body.at("/previousPeriod/to").asText());
        assertEquals(300_000, body.at("/revenue/total/previousValue").asLong());
        assertEquals(300_000, body.at("/revenue/payos/previousValue").asLong());
        assertEquals(0, body.at("/revenue/daily/0/previousTotal").asLong());
        assertEquals(300_000, body.at("/revenue/daily/1/previousTotal").asLong());
        assertEquals(300_000, doctor(body, doctorAn).path("previousRevenue").asLong());
        assertEquals(300_000, service(body, exam).path("previousRevenue").asLong());
    }

    // ---- bookings and arrivals -------------------------------------------------------------------------------------
    @Test void funnelCountsBookingsArrivalsAndCompletionsWithoutPaymentHolds() throws Exception {
        appointment(doctorAn, "CONFIRMED", DAY1.plusSeconds(3600), null);
        appointment(doctorAn, "CANCELLED", DAY1.plusSeconds(7200), null);
        appointment(doctorAn, "NO_SHOW", DAY1.plusSeconds(10800), null);
        appointment(doctorAn, "COMPLETED", DAY2, walkInVisit);
        appointment(doctorAn, "AWAITING_PAYMENT", DAY2.plusSeconds(3600), null);           // technical hold, never counted
        appointment(doctorAn, "PAYMENT_EXPIRED", DAY2.plusSeconds(7200), null);
        mvc.perform(get("/api/v1/owner/analytics?from=" + FROM + "&to=" + TO).with(owner())).andExpect(status().isOk())
                .andExpect(jsonPath("$.funnel.booked").value(4)).andExpect(jsonPath("$.funnel.arrived").value(1))
                .andExpect(jsonPath("$.funnel.completed").value(1)).andExpect(jsonPath("$.funnel.cancelled").value(1))
                .andExpect(jsonPath("$.funnel.noShow").value(1)).andExpect(jsonPath("$.appointments.value").value(4))
                .andExpect(jsonPath("$.arrivals.value").value(1));                          // the one walk-in visit
    }

    // ---- access and period -----------------------------------------------------------------------------------------
    @Test void onlyTheOwnerMayReadAndThePeriodMustBeValid() throws Exception {
        String url = "/api/v1/owner/analytics?from=" + FROM + "&to=" + TO;
        mvc.perform(get(url).with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(get(url).with(user("doctor").roles("DOCTOR"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/owner/analytics?from=" + FROM).with(owner())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MSG-72"));
        mvc.perform(get("/api/v1/owner/analytics?from=2031-03-09&to=2031-03-01").with(owner())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MSG-72"));
        mvc.perform(get("/api/v1/owner/analytics?from=2031-01-01&to=2031-06-30").with(owner())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MSG-72"));
    }

    // ---- assertions ------------------------------------------------------------------------------------------------
    private void assertAddsUp(JsonNode body) {
        long total = body.at("/revenue/total/value").asLong();
        long daily = 0, doctors = 0, services = 0;
        for (JsonNode day : body.at("/revenue/daily")) daily += day.path("cash").asLong() + day.path("payos").asLong();
        for (JsonNode row : body.at("/doctors")) doctors += row.path("revenue").asLong();
        for (JsonNode row : body.at("/services")) services += row.path("revenue").asLong();
        assertEquals(total, body.at("/revenue/cash/value").asLong() + body.at("/revenue/payos/value").asLong(), "cash + PayOS");
        assertEquals(total, daily, "daily bars");
        assertEquals(total, doctors, "doctors + unknown");
        assertEquals(total, services, "services");
    }

    private JsonNode doctor(JsonNode body, UUID id) {
        for (JsonNode row : body.at("/doctors")) if (id.toString().equals(row.path("doctorId").asText(null))) return row;
        throw new AssertionError("doctor missing: " + id);
    }
    private long doctorRevenue(JsonNode body, UUID id) { return doctor(body, id).path("revenue").asLong(); }
    private JsonNode service(JsonNode body, UUID id) {
        for (JsonNode row : body.at("/services")) if (id.toString().equals(row.path("serviceId").asText())) return row;
        throw new AssertionError("service missing: " + id);
    }
    private JsonNode analytics(String from, String to) throws Exception {
        String content = mvc.perform(get("/api/v1/owner/analytics?from=" + from + "&to=" + to).with(owner())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(content);
    }

    // ---- fixtures --------------------------------------------------------------------------------------------------
    private RequestPostProcessor owner() { return user("owner").roles("OWNER"); }
    private void account(UUID id, String type) {
        jdbc.update("insert into accounts(id,normalized_email,account_type,status,password_hash,created_at,updated_at) values (?,?,?,'ACTIVE','test',now(),now())", id, "own-" + id + "@example.test", type);
    }
    private void doctor(UUID id, UUID account, String name) {
        jdbc.update("insert into staff_profiles(is_doctor,id,account_id,display_name,publicly_visible,accepting_new_bookings,in_active_directory) values (true,?,?,?,true,true,true)", id, account, name);
    }
    private void visit(UUID id, String status, Instant checkedIn) {
        jdbc.update("insert into visits(id,patient_profile_id,source,arrived_at,checked_in_at,created_by_account_id,status) values (?,?,'WALK_IN',?,?,?,?)",
                id, profile, Timestamp.from(checkedIn), Timestamp.from(checkedIn), receptionist, status);
    }
    private void queue(UUID visit, UUID doctor) {
        jdbc.update("insert into queue_entries(visit_id,stage,status,entered_at,sort_order,doctor_profile_id,room_id,clinic_date,queue_number) "
                + "values (?,'EXAM','WAITING',now(),?,?,?,date '2031-03-01',?)", visit, ++queueNumber, doctor, room, queueNumber);
    }
    private void invoice(UUID id, String source, UUID visit, UUID appointment, UUID round) {
        jdbc.update("insert into invoices(id,invoice_number,patient_profile_id,visit_id,appointment_id,clinical_ordering_round_id,source_type,source_fingerprint,status,created_by_account_id,issued_at) "
                + "values (?,?,?,?,?,?,?,?,'OPEN',?,?)", id, "INV-OWN-" + id.toString().substring(0, 8), profile, visit, appointment, round, source,
                "b".repeat(64), receptionist, Timestamp.from(DAY1));
    }
    private UUID appointment(UUID doctor, String status, Instant start, UUID visit) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into appointments(id,patient_profile_id,service_id,doctor_profile_id,status,booking_source,purpose,start_time,end_time,duration_minutes,visit_id,created_by_account_id,occupied_until,payment_deadline) "
                + "values (?,?,?,?,?,'PATIENT_ONLINE','Khám',?,?,30,?,?,?,?)", id, profile, exam, doctor, status, Timestamp.from(start),
                Timestamp.from(start.plusSeconds(1800)), visit, receptionist, Timestamp.from(start.plusSeconds(1800)),
                status.equals("AWAITING_PAYMENT") ? Timestamp.from(start) : null);
        return id;
    }
    /** A confirmed booking by a patient: the appointment of {@code doctor} and its APPOINTMENT invoice. */
    private UUID bookedAppointment(UUID doctor, Instant start) {
        UUID invoice = UUID.randomUUID();
        invoice(invoice, "APPOINTMENT", null, appointment(doctor, "CONFIRMED", start, null), null);
        return invoice;
    }
    /** A service order round of {@code doctor} on the visit, and its CLINICAL_ROUND invoice. */
    private UUID orderedRound(UUID visit, UUID doctor, Instant at) {
        UUID round = UUID.randomUUID(), invoice = UUID.randomUUID();
        jdbc.update("insert into service_order_rounds(id,visit_id,round_number,ordered_by_doctor_id,ordered_at,created_at) values (?,?,?,?,?,?)",
                round, visit, ++roundNumber, doctor, Timestamp.from(at), Timestamp.from(at));
        invoice(invoice, "CLINICAL_ROUND", null, null, round);
        return invoice;
    }
    /** One payment attempt with the single invoice item it pays, as the billing flow records it (payment is per item). */
    private void pay(UUID invoice, String channel, String status, UUID service, long amount, Instant confirmedAt, String refundStatus) {
        UUID attempt = UUID.randomUUID();
        boolean counter = channel.equals("COUNTER");
        jdbc.update("insert into payment_attempts(id,invoice_id,channel,counter_method,provider,provider_order_code,amount,status,created_by_account_id,created_at,confirmed_at) "
                + "values (?,?,?,?,?,?,?,?,?,?,?)", attempt, invoice, channel, counter ? "CASH" : null, counter ? null : "PAYOS",
                counter ? null : (long) (910_000_000L + (++sequence) + Math.floorMod(attempt.getLeastSignificantBits(), 1_000_000L) * 1000), amount, status,
                counter ? receptionist : patientAccount, Timestamp.from(confirmedAt == null ? DAY1 : confirmedAt),
                confirmedAt == null ? null : Timestamp.from(confirmedAt));
        jdbc.update("insert into invoice_items(invoice_id,line_number,service_id,description_snapshot,quantity,unit_price_snapshot,payment_attempt_id,refund_status) values (?,?,?,'Khoản đã trả',1,?,?,?)",
                invoice, lines.merge(invoice, 1, Integer::sum), service, amount, attempt, refundStatus);
    }
}
