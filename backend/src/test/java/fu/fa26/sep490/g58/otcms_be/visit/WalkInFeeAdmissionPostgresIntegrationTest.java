package fu.fa26.sep490.g58.otcms_be.visit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Walk-in fee flow on the real database and triggers: walk-in (PENDING_PAYMENT), VISIT invoice, counter payment,
 * then admission to the exam queue with a number (plans/261111-0208-visit-flow-r3, A4, A5, B5).
 */
@SpringBootTest
@AutoConfigureMockMvc
@LocalPostgresTest
class WalkInFeeAdmissionPostgresIntegrationTest {
    private static final String CORRELATION = "5c1e9d0a-7b2f-4e3a-8d6c-1f0e9a8b7c6d";
    private static final long FEE_AMOUNT = 250_000L;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private UUID receptionist;
    private UUID patientAccount;
    private UUID patientProfile;
    private UUID doctorAccount;
    private UUID doctorProfile;
    private UUID feeService;
    private UUID otherFeeService;
    private UUID freeService;

    @BeforeEach
    void setUp() {
        receptionist = account(AccountType.RECEPTIONIST, "receptionist");
        patientAccount = account(AccountType.PATIENT, "patient");
        doctorAccount = account(AccountType.DOCTOR, "doctor");
        patientProfile = patient(patientAccount);
        doctorProfile = UUID.randomUUID();
        jdbc.update("insert into staff_profiles (is_doctor, id, account_id, display_name, photo_url, work_contact, specialties, "
                + "qualifications, biography, consultation_information, publicly_visible, accepting_new_bookings, "
                + "in_active_directory) values (true, ?, ?, 'WF Doctor', '', 'private', '', '', '', '', true, true, true)",
                doctorProfile, doctorAccount);
        feeService = service("WF Examination", new BigDecimal("250000.00"));
        otherFeeService = service("WF Other Examination", new BigDecimal("100000.00"));
        freeService = service("WF Free Examination", BigDecimal.ZERO);
        for (UUID serviceId : List.of(feeService, otherFeeService, freeService)) {
            jdbc.update("insert into doctor_services (doctor_profile_id, service_id, active) values (?, ?, true)",
                    doctorProfile, serviceId);
        }
        // On shift around now: the walk-in check uses the real clock.
        Instant now = Instant.now();
        jdbc.update("insert into work_shifts (id, staff_profile_id, starts_at, ends_at) values (?, ?, ?, ?)",
                UUID.randomUUID(), doctorProfile, Timestamp.from(now.minusSeconds(3600)),
                Timestamp.from(now.plusSeconds(8 * 3600L)));
    }

    @Test
    void feeWalkInWaitsForItsInvoiceAndIsNumberedWhenPaidInFull() throws Exception {
        mvc.perform(createWalkIn("wf-full", patientProfile, doctorProfile, feeService))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.examStatus").value("PENDING_PAYMENT"));
        UUID visitId = visitOf("wf-full");
        assertEquals("PENDING_PAYMENT", queueStatus(visitId));
        assertNull(queueNumber(visitId));

        mvc.perform(invoiceRequest(visitId, invoiceBody(item(feeService, 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceType").value("VISIT"))
                .andExpect(jsonPath("$.status").value("OPEN"));
        UUID invoice = invoiceOf(visitId);
        // A retry returns the same invoice, even after the fee is paid.
        mvc.perform(invoiceRequest(visitId, invoiceBody(item(feeService, 1)))).andExpect(status().isOk());
        assertEquals(1, invoiceCount(visitId));
        assertEquals("PENDING_PAYMENT", queueStatus(visitId));

        mvc.perform(counterPayment(invoice, FEE_AMOUNT, "wf-full-pay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        assertEquals("PAID", invoiceStatus(invoice));
        assertEquals("WAITING", queueStatus(visitId));
        assertEquals(Integer.valueOf(1), queueNumber(visitId));
        assertEquals("OPEN", visitStatus(visitId));
        // The legacy reception queue keeps its row until the doctor starts the examination.
        assertEquals("WAITING", jdbc.queryForObject("select status from queue_entries where stage = 'RECEPTION' and visit_id = ?", String.class, visitId));
    }

    @Test
    void partialPaymentLeavesTheWalkInPendingUntilTheFeeIsCovered() throws Exception {
        UUID visitId = createPendingWalkIn("wf-partial", feeService);
        UUID invoice = issueInvoice(visitId, feeService);

        mvc.perform(counterPayment(invoice, 100_000L, "wf-partial-1")).andExpect(status().isOk());
        assertEquals("OPEN", invoiceStatus(invoice));
        assertEquals("PENDING_PAYMENT", queueStatus(visitId));
        assertNull(queueNumber(visitId));

        mvc.perform(counterPayment(invoice, 150_000L, "wf-partial-2")).andExpect(status().isOk());
        assertEquals("PAID", invoiceStatus(invoice));
        assertEquals("WAITING", queueStatus(visitId));
        assertEquals(Integer.valueOf(1), queueNumber(visitId));
    }

    @Test
    void visitInvoiceForAPendingWalkInIsExactlyTheFeeServiceOnce() throws Exception {
        UUID visitId = createPendingWalkIn("wf-lines", feeService);
        for (String body : List.of(
                invoiceBody(item(feeService, 2)),
                invoiceBody(item(otherFeeService, 1)),
                invoiceBody(item(feeService, 1), item(otherFeeService, 1)),
                invoiceBody(item(freeService, 1)))) {
            mvc.perform(invoiceRequest(visitId, body))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("SOURCE_NOT_BILLABLE"));
        }
        assertEquals(0, invoiceCount(visitId));
        assertEquals("PENDING_PAYMENT", queueStatus(visitId));
    }

    @Test
    void freeWalkInIsNumberedAtOnceAndCanBeCancelledWithoutPayment() throws Exception {
        mvc.perform(createWalkIn("wf-free", patientProfile, doctorProfile, freeService))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.examStatus").value("WAITING"))
                .andExpect(jsonPath("$.queueNumber").value(1));
        UUID visitId = visitOf("wf-free");

        mvc.perform(cancel(visitId)).andExpect(status().isNoContent());
        assertEquals("CANCELLED", queueStatus(visitId));
        assertEquals("CLOSED", visitStatus(visitId));
    }

    @Test
    void paidWalkInCannotBeCancelledOnceAdmitted() throws Exception {
        UUID visitId = createPendingWalkIn("wf-paid-cancel", feeService);
        UUID invoice = issueInvoice(visitId, feeService);
        mvc.perform(counterPayment(invoice, FEE_AMOUNT, "wf-paid-cancel-pay")).andExpect(status().isOk());

        mvc.perform(cancel(visitId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("VISIT_CANCELLATION_NOT_ALLOWED"));
        assertEquals("WAITING", queueStatus(visitId));
        assertEquals("OPEN", visitStatus(visitId));
        assertEquals("PAID", invoiceStatus(invoice));
    }

    @Test
    void unpaidWalkInCanBeCancelledAndIsNeverInvoicedAfterwards() throws Exception {
        UUID visitId = createPendingWalkIn("wf-unpaid-cancel", feeService);

        mvc.perform(cancel(visitId)).andExpect(status().isNoContent());
        assertEquals("CANCELLED", queueStatus(visitId));
        assertEquals("CLOSED", visitStatus(visitId));

        mvc.perform(invoiceRequest(visitId, invoiceBody(item(feeService, 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SOURCE_NOT_BILLABLE"));
        assertEquals(0, invoiceCount(visitId));
    }

    @Test
    void walkInIsRefusedWhenTheChosenDoctorIsOffShift() throws Exception {
        jdbc.update("delete from work_shifts where staff_profile_id = ?", doctorProfile);

        mvc.perform(createWalkIn("wf-off-shift", patientProfile, doctorProfile, feeService))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DOCTOR_NOT_ON_SHIFT"));
        assertEquals(0, jdbc.queryForObject("select count(*) from visits where created_by_account_id = ?",
                Integer.class, receptionist));
    }

    private UUID createPendingWalkIn(String key, UUID serviceId) throws Exception {
        mvc.perform(createWalkIn(key, patientProfile, doctorProfile, serviceId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.examStatus").value("PENDING_PAYMENT"));
        UUID visitId = visitOf(key);
        assertEquals("PENDING_PAYMENT", queueStatus(visitId));
        return visitId;
    }

    private UUID issueInvoice(UUID visitId, UUID serviceId) throws Exception {
        mvc.perform(invoiceRequest(visitId, invoiceBody(item(serviceId, 1)))).andExpect(status().isOk());
        return invoiceOf(visitId);
    }

    private MockHttpServletRequestBuilder createWalkIn(String key, UUID patient, UUID doctor, UUID serviceId) {
        String json = "{\"patientProfileId\":\"" + patient + "\",\"doctorProfileId\":\"" + doctor
                + "\",\"serviceId\":\"" + serviceId + "\"}";
        return post("/api/v1/visits/walk-ins").with(actor(receptionist, AccountType.RECEPTIONIST)).with(csrf())
                .header("Idempotency-Key", key).header("X-Correlation-ID", CORRELATION)
                .contentType("application/json").content(json);
    }

    /** The frontend sends Idempotency-Key here; the endpoint does not declare it, so it must be accepted and ignored. */
    private MockHttpServletRequestBuilder invoiceRequest(UUID visitId, String body) {
        return post("/api/v1/visits/{visitId}/invoices", visitId)
                .with(actor(receptionist, AccountType.RECEPTIONIST)).with(csrf())
                .header("Idempotency-Key", "wf-invoice-" + visitId)
                .header("X-Correlation-ID", CORRELATION).contentType("application/json").content(body);
    }

    private MockHttpServletRequestBuilder counterPayment(UUID invoiceId, long amount, String key) {
        return post("/api/v1/invoices/{invoiceId}/counter-payments", invoiceId)
                .with(actor(receptionist, AccountType.RECEPTIONIST)).with(csrf())
                .header("Idempotency-Key", key).header("X-Correlation-ID", CORRELATION)
                .contentType("application/json").content("{\"amount\":" + amount + ",\"method\":\"CASH\"}");
    }

    private MockHttpServletRequestBuilder cancel(UUID visitId) {
        return post("/api/v1/visits/{visitId}/cancellation", visitId)
                .with(actor(receptionist, AccountType.RECEPTIONIST)).with(csrf())
                .header("X-Correlation-ID", CORRELATION);
    }

    private static String item(UUID serviceId, int quantity) {
        return "{\"serviceId\":\"" + serviceId + "\",\"quantity\":" + quantity + "}";
    }

    private static String invoiceBody(String... items) {
        return "{\"items\":[" + String.join(",", items) + "]}";
    }

    private UUID visitOf(String key) {
        return jdbc.queryForObject("select id from visits where created_by_account_id = ? and walk_in_idempotency_key = ?", UUID.class, receptionist, key);
    }

    private UUID invoiceOf(UUID visitId) {
        return jdbc.queryForObject("select id from invoices where visit_id = ?", UUID.class, visitId);
    }

    private int invoiceCount(UUID visitId) {
        return jdbc.queryForObject("select count(*) from invoices where visit_id = ?", Integer.class, visitId);
    }

    private String invoiceStatus(UUID invoiceId) {
        return jdbc.queryForObject("select status from invoices where id = ?", String.class, invoiceId);
    }

    private String queueStatus(UUID visitId) {
        return jdbc.queryForObject("select status from queue_entries where stage = 'EXAM' and visit_id = ?", String.class, visitId);
    }

    private Integer queueNumber(UUID visitId) {
        return jdbc.queryForObject("select queue_number from queue_entries where stage = 'EXAM' and visit_id = ?", Integer.class, visitId);
    }

    private String visitStatus(UUID visitId) {
        return jdbc.queryForObject("select status from visits where id = ?", String.class, visitId);
    }

    @AfterEach
    void cleanUp() {
        // Audit and walk-in history rows are append-only; their triggers are switched off for this cleanup only.
        String ownVisits = "select id from visits where created_by_account_id = ?";
        jdbc.execute("alter table audit_logs disable trigger tr_audit_logs_immutable");
        jdbc.execute("alter table audit_logs disable trigger tr_audit_logs_no_truncate");
        try {
            jdbc.update("delete from payment_attempts where invoice_id in (select id from invoices where visit_id in ("
                    + ownVisits + "))", receptionist);
            jdbc.update("delete from invoice_items where invoice_id in (select id from invoices where visit_id in ("
                    + ownVisits + "))", receptionist);
            jdbc.update("delete from invoices where visit_id in (" + ownVisits + ")", receptionist);
            jdbc.update("delete from audit_logs where source in ('walk_in_reception_queue_history', 'visit_assignment_audits', 'visit_audits') and record_label in (select id::text from (" + ownVisits + ") fixture_visits)", receptionist);
            jdbc.update("delete from queue_entries where stage = 'RECEPTION' and visit_id in (" + ownVisits + ")", receptionist);

            jdbc.update("delete from queue_entries where stage = 'EXAM' and (visit_id in (" + ownVisits
                    + ") or doctor_profile_id = ?)", receptionist, doctorProfile);

            jdbc.update("delete from audit_logs where source = 'visit_audits' and record_label in (select id::text from (" + ownVisits + ") fixture_visits)", receptionist);
            jdbc.update("delete from visits where created_by_account_id = ?", receptionist);
            jdbc.update("delete from work_shifts where staff_profile_id = ?", doctorProfile);
            jdbc.update("delete from doctor_services where doctor_profile_id = ?", doctorProfile);
            jdbc.update("delete from clinic_services where id in (?, ?, ?)", feeService, otherFeeService, freeService);
            jdbc.update("delete from staff_profiles where id = ?", doctorProfile);
            jdbc.update("delete from patient_profiles where id = ?", patientProfile);
            jdbc.update("delete from accounts where id in (?, ?, ?)", receptionist, patientAccount, doctorAccount);
        } finally {
                jdbc.execute("alter table audit_logs enable trigger tr_audit_logs_immutable");
            jdbc.execute("alter table audit_logs enable trigger tr_audit_logs_no_truncate");
        }
    }

    private UUID account(AccountType type, String suffix) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into accounts (id, normalized_email, account_type, status, created_at, updated_at) "
                + "values (?, ?, ?, 'ACTIVE', now(), now())", id, "wf-" + suffix + "-" + id + "@example.test",
                type.name());
        return id;
    }

    private UUID patient(UUID accountId) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into patient_profiles (id, account_id, full_name, phone_canonical, phone_owner_type, "
                + "date_of_birth, gender) values (?, ?, 'WF Patient', ?, 'PATIENT', '1990-01-01', 'OTHER')",
                id, accountId, "+848" + UUID.randomUUID().toString().replace("-", "").substring(0, 9));
        return id;
    }

    private UUID service(String name, BigDecimal price) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into clinic_services (id, name, price, duration_minutes, buffer_minutes, online_bookable, "
                + "active) values (?, ?, ?, 30, 5, true, true)", id, name + " " + id, price);
        return id;
    }

    private static RequestPostProcessor actor(UUID id, AccountType type) {
        var principal = new AuthenticatedAccount(id, AccountRole.valueOf(type.name()), UUID.randomUUID());
        return authentication(new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + type.name()))));
    }
}
