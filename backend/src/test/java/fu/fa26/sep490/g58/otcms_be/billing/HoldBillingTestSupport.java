package fu.fa26.sep490.g58.otcms_be.billing;

import fu.fa26.sep490.g58.otcms_be.billing.integration.AppointmentPaymentPort;
import fu.fa26.sep490.g58.otcms_be.billing.model.dto.response.InvoiceResponse;
import fu.fa26.sep490.g58.otcms_be.billing.model.dto.response.PaymentAttemptResponse;
import fu.fa26.sep490.g58.otcms_be.billing.service.AppointmentHoldSettlementService;
import fu.fa26.sep490.g58.otcms_be.billing.service.InvoiceService;
import fu.fa26.sep490.g58.otcms_be.billing.service.PaymentGateway;
import fu.fa26.sep490.g58.otcms_be.billing.service.PaymentService;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Real Postgres fixture for the online-booking payment hold. Fakes sit only at the two system boundaries: the payment
 * gateway (PayOS) and the appointment port (reads/updates the real appointments table, so row locks and the
 * confirm-in-the-payment-transaction behaviour are genuine). Time comes from an injectable mutable Clock.
 */
// The suite registers its own PAYOS gateway fake: force the real one off so a developer shell with
// APP_PAYMENT_PAYOS_ENABLED=true cannot create a duplicate "PAYOS" provider key.
@SpringBootTest(properties = "app.payment.payos.enabled=false")
@LocalPostgresTest
@AutoConfigureMockMvc
@Import(HoldBillingTestSupport.TestBeans.class)
abstract class HoldBillingTestSupport {
    static final BigDecimal PRICE = new BigDecimal("125000.00");
    static final long PRICE_LONG = 125_000L;

    @Autowired JdbcTemplate jdbc;
    @Autowired InvoiceService invoices;
    @Autowired PaymentService payments;
    @Autowired AppointmentHoldSettlementService settlement;
    @Autowired fu.fa26.sep490.g58.otcms_be.billing.service.BillingReadService billingReads;
    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired FakeGateway gateway;
    @Autowired JdbcAppointmentPort appointmentPort;

    final UUID patientAccountId = UUID.randomUUID();
    final UUID receptionistAccountId = UUID.randomUUID();
    final UUID doctorAccountId = UUID.randomUUID();
    final UUID patientProfileId = UUID.randomUUID();
    final UUID doctorProfileId = UUID.randomUUID();
    final UUID serviceId = UUID.randomUUID();
    final AuthenticatedAccount patient = new AuthenticatedAccount(patientAccountId, AccountRole.PATIENT, UUID.randomUUID());
    final AuthenticatedAccount receptionist = new AuthenticatedAccount(
            receptionistAccountId, AccountRole.RECEPTIONIST, UUID.randomUUID());
    final List<UUID> appointmentIds = new ArrayList<>();
    private int slot;

    @BeforeEach
    void createFixture() {
        clock.set(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS)); // Postgres keeps microseconds
        gateway.reset();
        appointmentPort.reset();
        insertAccount(patientAccountId, "PATIENT");
        insertAccount(receptionistAccountId, "RECEPTIONIST");
        insertAccount(doctorAccountId, "DOCTOR");
        jdbc.update("insert into patient_profiles (id, account_id, full_name, phone_canonical, "
                        + "phone_owner_type, date_of_birth, gender) values (?, ?, ?, ?, 'PATIENT', date '1990-01-01', 'OTHER')",
                patientProfileId, patientAccountId, "Hold Billing Patient",
                "+849" + String.format("%09d", Math.floorMod(patientProfileId.getLeastSignificantBits(), 1_000_000_000L)));
        jdbc.update("insert into staff_profiles (is_doctor, id, account_id, display_name) values (true, ?, ?, ?)",
                doctorProfileId, doctorAccountId, "Hold Billing Doctor");
        jdbc.update("insert into clinic_services (id, name, service_type, price, duration_minutes, "
                        + "buffer_minutes, online_bookable, active) values (?, ?, 'EXAMINATION', ?, 30, 0, true, true)",
                serviceId, "Hold Billing Examination", PRICE);
        jdbc.update("insert into doctor_services (doctor_profile_id, service_id, active) values (?, ?, true)",
                doctorProfileId, serviceId);
    }

    @AfterEach
    void removeFixture() {
        for (UUID invoiceId : jdbc.queryForList("select id from invoices where patient_profile_id = ?",
                UUID.class, patientProfileId)) {
            jdbc.update("delete from payment_gateway_events where provider_order_code in "
                    + "(select provider_order_code from payment_attempts where invoice_id = ?)", invoiceId);
            jdbc.update("delete from payment_attempts where invoice_id = ?", invoiceId);
            jdbc.update("delete from invoice_items where invoice_id = ?", invoiceId);
            jdbc.update("delete from invoices where id = ?", invoiceId);
        }
        for (UUID id : appointmentIds) jdbc.update("delete from appointments where id = ?", id);
        jdbc.update("delete from doctor_services where doctor_profile_id = ? and service_id = ?", doctorProfileId, serviceId);
        jdbc.update("delete from clinic_services where id = ?", serviceId);
        jdbc.update("delete from staff_profiles where id = ?", doctorProfileId);
        jdbc.update("delete from patient_profiles where id = ?", patientProfileId);
        jdbc.update("delete from accounts where id in (?, ?, ?)", patientAccountId, receptionistAccountId, doctorAccountId);
    }

    // ---- fixture helpers -------------------------------------------------------------------------------------

    /** An online booking hold (AWAITING_PAYMENT) whose deadline is {@code deadlineIn} from the test clock. */
    UUID hold(Duration deadlineIn) {
        return appointment("AWAITING_PAYMENT", clock.instant().plus(deadlineIn));
    }

    UUID appointment(String status, Instant paymentDeadline) {
        UUID id = UUID.randomUUID();
        Instant start = Instant.now().plus(Duration.ofDays(7)).plus(Duration.ofHours(slot++));
        jdbc.update("insert into appointments (id, patient_profile_id, service_id, doctor_profile_id, status, "
                        + "booking_source, purpose, start_time, end_time, duration_minutes, buffer_minutes, "
                        + "created_by_account_id, payment_deadline) values (?, ?, ?, ?, ?, 'PATIENT_ONLINE', ?, ?, ?, 30, 0, ?, ?)",
                id, patientProfileId, serviceId, doctorProfileId, status, "Hold billing test",
                Timestamp.from(start), Timestamp.from(start.plusSeconds(1800)), patientAccountId,
                paymentDeadline == null ? null : Timestamp.from(paymentDeadline));
        appointmentIds.add(id);
        return id;
    }

    void setAppointmentStatus(UUID id, String status) {
        jdbc.update("update appointments set status = ?, payment_deadline = case when ? = 'AWAITING_PAYMENT' "
                + "then payment_deadline else null end where id = ?", status, status, id);
    }

    InvoiceResponse invoiceFor(UUID appointmentId) {
        return invoices.createForAppointment(appointmentId, patient);
    }

    PaymentAttemptResponse qr(UUID invoiceId) {
        return payments.createOnlineAttempt(invoiceId, patient, "hold-" + UUID.randomUUID());
    }

    long orderCode(UUID attemptId) {
        return jdbc.queryForObject("select provider_order_code from payment_attempts where id = ?", Long.class, attemptId);
    }

    /** Hold + invoice + live QR; returns {appointmentId, invoiceId, attemptId} and the order code. */
    Held heldWithQr() {
        UUID appointmentId = hold(Duration.ofMinutes(15));
        UUID invoiceId = invoiceFor(appointmentId).id();
        UUID attemptId = qr(invoiceId).id();
        return new Held(appointmentId, invoiceId, attemptId, orderCode(attemptId));
    }

    record Held(UUID appointmentId, UUID invoiceId, UUID attemptId, long orderCode) { }

    String appointmentStatus(UUID id) {
        return jdbc.queryForObject("select status from appointments where id = ?", String.class, id);
    }

    String invoiceStatus(UUID id) {
        return jdbc.queryForObject("select status from invoices where id = ?", String.class, id);
    }

    String attemptStatus(UUID id) {
        return jdbc.queryForObject("select status from payment_attempts where id = ?", String.class, id);
    }

    List<String> eventOutcomes(long orderCode) {
        return jdbc.queryForList("select outcome || ':' || coalesce(outcome_detail, '') from payment_gateway_events "
                + "where provider_order_code = ? order by received_at", String.class, orderCode);
    }

    PaymentGateway.VerifiedGatewayEvent paidEvent(long orderCode) {
        return new PaymentGateway.VerifiedGatewayEvent("PAYOS", orderCode, PRICE_LONG, "VND",
                "link-" + orderCode, "ref-" + orderCode, "00", true);
    }

    /** Posts a PayOS webhook through the real controller and security chain; returns the HTTP status. */
    int postWebhook(long orderCode, long amount, String reference) throws Exception {
        String body = "{\"orderCode\":" + orderCode + ",\"amount\":" + amount + ",\"paymentLinkId\":\"link-" + orderCode
                + "\",\"reference\":\"" + reference + "\",\"success\":true}";
        return mockMvc.perform(post("/api/v1/payment-webhooks/payos").contentType(MediaType.APPLICATION_JSON)
                .content(body)).andReturn().getResponse().getStatus();
    }

    private void insertAccount(UUID id, String type) {
        jdbc.update("insert into accounts (id, normalized_email, account_type, status, password_hash, created_at, updated_at) "
                        + "values (?, ?, ?, 'ACTIVE', 'integration-test', now(), now())",
                id, "hold-billing-" + id + "@example.test", type);
    }

    // ---- boundary fakes ----------------------------------------------------------------------------------------

    @TestConfiguration
    static class TestBeans {
        @Bean @Primary MutableClock mutableClock() { return new MutableClock(); }
        @Bean FakeGateway fakeGateway() { return new FakeGateway(); }
        @Bean @Primary JdbcAppointmentPort jdbcAppointmentPort(JdbcTemplate jdbc) { return new JdbcAppointmentPort(jdbc); }
    }

    static final class MutableClock extends Clock {
        private volatile Instant now = Instant.now();
        void set(Instant value) { now = value; }
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    /** PayOS stand-in: programmable status per order code, records link commands, can be switched off. */
    static final class FakeGateway implements PaymentGateway {
        final Map<Long, GatewayPaymentStatus> statuses = new ConcurrentHashMap<>();
        final List<GatewayPaymentCommand> commands = new CopyOnWriteArrayList<>();
        volatile boolean down;

        void reset() { statuses.clear(); commands.clear(); down = false; }
        void setStatus(long orderCode, String status) {
            long paid = "PAID".equals(status) ? PRICE_LONG : 0;
            statuses.put(orderCode, new GatewayPaymentStatus(status, orderCode, PRICE_LONG, paid,
                    "link-" + orderCode, "PAID".equals(status) ? "ref-" + orderCode : null, "VND"));
        }
        @Override public String provider() { return "PAYOS"; }
        @Override public long linkTtlSeconds() { return 900; }
        @Override public GatewayLink createLink(GatewayPaymentCommand command) {
            commands.add(command);
            return new GatewayLink("link-" + command.orderCode(), command.orderCode(),
                    "https://pay.test/" + command.orderCode(), "qr-" + command.orderCode(), command.expiresAt(),
                    new TransferInfo("970422", "0123456789", "OTCMS", "ORDER " + command.orderCode()));
        }
        @Override public VerifiedGatewayEvent verifyWebhook(Object payload) {
            Map<?, ?> map = (Map<?, ?>) payload;
            return new VerifiedGatewayEvent("PAYOS", ((Number) map.get("orderCode")).longValue(),
                    ((Number) map.get("amount")).longValue(), "VND", (String) map.get("paymentLinkId"),
                    (String) map.get("reference"), "00", Boolean.TRUE.equals(map.get("success")));
        }
        @Override public GatewayPaymentStatus query(long orderCode) {
            if (down) throw new IllegalStateException("gateway down");
            return statuses.getOrDefault(orderCode,
                    new GatewayPaymentStatus("PENDING", orderCode, PRICE_LONG, 0, "link-" + orderCode, null, "VND"));
        }
    }

    /** Appointment side of the port backed by the real appointments table (row lock, same transaction). */
    static final class JdbcAppointmentPort implements AppointmentPaymentPort {
        private final JdbcTemplate jdbc;
        final AtomicInteger confirmedTransitions = new AtomicInteger();
        final AtomicInteger confirmCalls = new AtomicInteger();

        JdbcAppointmentPort(JdbcTemplate jdbc) { this.jdbc = jdbc; }
        void reset() { confirmedTransitions.set(0); confirmCalls.set(0); }

        @Override public ConfirmResult confirmPaid(UUID appointmentId) {
            confirmCalls.incrementAndGet();
            List<String> rows = jdbc.queryForList("select status from appointments where id = ? for update",
                    String.class, appointmentId);
            if (rows.isEmpty()) return ConfirmResult.NOT_AWAITING;
            return switch (rows.getFirst()) {
                case "AWAITING_PAYMENT" -> {
                    jdbc.update("update appointments set status = 'CONFIRMED', payment_deadline = null where id = ?",
                            appointmentId);
                    confirmedTransitions.incrementAndGet();
                    yield ConfirmResult.CONFIRMED;
                }
                case "PAYMENT_EXPIRED", "CANCELLED" -> ConfirmResult.NOT_AWAITING;
                default -> ConfirmResult.ALREADY_CONFIRMED;
            };
        }

        @Override public Optional<Instant> liveHoldDeadline(UUID appointmentId) {
            return jdbc.query("select payment_deadline from appointments where id = ? and status = 'AWAITING_PAYMENT'",
                    (rs, i) -> rs.getTimestamp(1).toInstant(), appointmentId).stream().findFirst();
        }

        @Override public Set<UUID> heldOrReleasedIds(Collection<UUID> appointmentIds) {
            if (appointmentIds.isEmpty()) return Set.of();
            String placeholders = String.join(",", java.util.Collections.nCopies(appointmentIds.size(), "?"));
            return Set.copyOf(jdbc.queryForList("select id from appointments where status in "
                    + "('AWAITING_PAYMENT', 'PAYMENT_EXPIRED') and id in (" + placeholders + ")", UUID.class,
                    appointmentIds.toArray()));
        }
    }
}
