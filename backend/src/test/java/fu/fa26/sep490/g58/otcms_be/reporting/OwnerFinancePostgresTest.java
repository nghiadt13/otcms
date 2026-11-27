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
 * Owner finance page against real Postgres (rolled back per test): the Owner sees how much was collected and
 * refunded, who confirmed each refund, and what is still owed. The period is in 2031 so other test classes'
 * rows never fall inside it; the pending snapshot is global, so it is asserted as a before/after delta.
 */
@SpringBootTest @LocalPostgresTest @Transactional @AutoConfigureMockMvc
class OwnerFinancePostgresTest {
    static final String FROM = "2031-03-01", TO = "2031-03-03";
    static final Instant DAY1 = Instant.parse("2031-03-01T03:00:00Z");
    static final Instant DAY2 = Instant.parse("2031-03-02T03:00:00Z");
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    final ObjectMapper json = new ObjectMapper();
    final UUID receptionist = UUID.randomUUID(), adminAccount = UUID.randomUUID(), patientAccount = UUID.randomUUID(), profile = UUID.randomUUID();
    final UUID service = UUID.randomUUID(), visit = UUID.randomUUID(), invoice = UUID.randomUUID();
    int sequence, line;

    @BeforeEach void fixtures() {
        account(receptionist, "RECEPTIONIST"); account(adminAccount, "ADMIN"); account(patientAccount, "PATIENT");
        jdbc.update("insert into staff_profiles(id,account_id,display_name,is_doctor) values (?,?,'Lễ tân Hoa',false)", UUID.randomUUID(), receptionist);
        jdbc.update("insert into staff_profiles(id,account_id,display_name,is_doctor) values (?,?,'Quản trị Nam',false)", UUID.randomUUID(), adminAccount);
        jdbc.update("insert into patient_profiles(id,account_id,full_name,phone_canonical,phone_owner_type,date_of_birth,gender) values (?,?,'Bệnh nhân Hoàn',?,'PATIENT',date '1990-01-01','OTHER')",
                profile, patientAccount, "+849" + String.format("%09d", Math.floorMod(profile.getLeastSignificantBits(), 1_000_000_000L)));
        jdbc.update("insert into clinic_services(id,name,duration_minutes,buffer_minutes,online_bookable,active,price) values (?,'Dịch vụ hoàn',30,0,false,true,100000)", service);
        jdbc.update("insert into visits(id,patient_profile_id,source,arrived_at,checked_in_at,created_by_account_id,status) values (?,?,'WALK_IN',?,?,?,'OPEN')",
                visit, profile, Timestamp.from(DAY1), Timestamp.from(DAY1), receptionist);
        jdbc.update("insert into invoices(id,invoice_number,patient_profile_id,visit_id,source_type,source_fingerprint,status,created_by_account_id,issued_at) values (?,?,?,?,'VISIT',?,'OPEN',?,?)",
                invoice, "INV-FIN-" + invoice.toString().substring(0, 8), profile, visit, "c".repeat(64), receptionist, Timestamp.from(DAY1));
    }

    @Test void showsHowMuchWasCollectedRefundedAndWhoConfirmedEachRefund() throws Exception {
        long pendingBefore = analytics().at("/summary/pending").asLong(), pendingCountBefore = analytics().at("/summary/pendingCount").asLong();
        paid(300_000, DAY1, null, null, null, null);                                              // collected, not refunded
        paid(80_000, DAY1, "REFUNDED", "Khách huỷ chỉ định", receptionist, DAY1.plusSeconds(3600));
        paid(70_000, DAY1, "REFUNDED", "Chỉ định nhầm", receptionist, DAY2);
        paid(50_000, DAY2, "REFUNDED", "Lịch hẹn đã huỷ", adminAccount, DAY2.plusSeconds(60));
        paid(40_000, DAY1, "REFUND_PENDING", "Thanh toán sau khi khoản bị huỷ", null, null);       // still owed
        paid(90_000, DAY1, "REFUNDED", "Hoàn từ kỳ khác", receptionist, DAY1.minusSeconds(86400L * 30)); // refunded outside the period

        JsonNode body = analytics();
        assertEquals("2031-03-01", body.at("/period/from").asText());
        assertEquals(300_000 + 80_000 + 70_000 + 50_000 + 40_000 + 90_000, body.at("/summary/collected").asLong());
        assertEquals(200_000, body.at("/summary/refunded").asLong());
        assertEquals(3, body.at("/summary/refundedCount").asLong());
        assertEquals(pendingBefore + 40_000, body.at("/summary/pending").asLong());
        assertEquals(pendingCountBefore + 1, body.at("/summary/pendingCount").asLong());

        // Who refunded: the receptionist refunded 150,000 in two items, the admin 50,000.
        assertEquals(2, body.at("/byPerson").size());
        assertEquals("Lễ tân Hoa", body.at("/byPerson/0/name").asText());
        assertEquals("RECEPTIONIST", body.at("/byPerson/0/role").asText());
        assertEquals(2, body.at("/byPerson/0/count").asLong());
        assertEquals(150_000, body.at("/byPerson/0/amount").asLong());
        assertEquals("Quản trị Nam", body.at("/byPerson/1/name").asText());
        assertEquals("ADMIN", body.at("/byPerson/1/role").asText());

        // Rows: pending first, then refunded newest first; the refund outside the period is not listed.
        JsonNode mine = rowsOf(body);
        assertEquals("REFUND_PENDING", mine.get(0).path("status").asText());
        assertEquals(40_000, mine.get(0).path("amount").asLong());
        assertEquals(true, mine.get(0).path("refundedAt").isNull());
        assertEquals(4, mine.size());
        assertEquals("Quản trị Nam", mine.get(1).path("refundedByName").asText());   // 2031-03-02 10:01, the latest
        assertEquals("Lịch hẹn đã huỷ", mine.get(1).path("reason").asText());
        assertEquals("Bệnh nhân Hoàn", mine.get(1).path("patientName").asText());
        assertEquals(50_000, mine.get(1).path("amount").asLong());
    }

    @Test void onlyTheOwnerMayReadAndThePeriodMustBeValid() throws Exception {
        String url = "/api/v1/owner/finance?from=" + FROM + "&to=" + TO;
        mvc.perform(get(url).with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(get(url).with(user("rec").roles("RECEPTIONIST"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/owner/finance?from=" + FROM).with(owner())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MSG-72"));
        mvc.perform(get("/api/v1/owner/finance?from=2031-01-01&to=2031-06-30").with(owner())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MSG-72"));
    }

    /** The rows of this test's own invoice only, in the order the API returned them. */
    private JsonNode rowsOf(JsonNode body) {
        var rows = json.createArrayNode();
        String number = "INV-FIN-" + invoice.toString().substring(0, 8);
        for (JsonNode row : body.at("/refunds")) if (number.equals(row.path("invoiceNumber").asText())) rows.add(row);
        return rows;
    }
    private JsonNode analytics() throws Exception {
        String content = mvc.perform(get("/api/v1/owner/finance?from=" + FROM + "&to=" + TO).with(owner())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(content);
    }
    private RequestPostProcessor owner() { return user("owner").roles("OWNER"); }
    private void account(UUID id, String type) {
        jdbc.update("insert into accounts(id,normalized_email,account_type,status,password_hash,created_at,updated_at) values (?,?,?,'ACTIVE','test',now(),now())", id, "fin-" + id + "@example.test", type);
    }
    /** A confirmed counter payment of {@code amount} paying one item of the invoice, optionally refunded by {@code by}. */
    private void paid(long amount, Instant paidAt, String refundStatus, String reason, UUID by, Instant refundedAt) {
        UUID attempt = UUID.randomUUID();
        jdbc.update("insert into payment_attempts(id,invoice_id,channel,counter_method,amount,status,created_by_account_id,created_at,confirmed_at) values (?,?,'COUNTER','CASH',?,'CONFIRMED',?,?,?)",
                attempt, invoice, amount, receptionist, Timestamp.from(paidAt), Timestamp.from(paidAt));
        jdbc.update("insert into invoice_items(invoice_id,line_number,service_id,description_snapshot,quantity,unit_price_snapshot,payment_attempt_id,refund_status,refund_reason,refunded_at,refunded_by_account_id) "
                + "values (?,?,?,'Khoản đã trả',1,?,?,?,?,?,?)", invoice, ++line, service, amount, attempt, refundStatus, reason,
                refundedAt == null ? null : Timestamp.from(refundedAt), by);
    }
}
