package fu.fa26.sep490.g58.otcms_be.appointment;

import fu.fa26.sep490.g58.otcms_be.billing.service.PaymentGateway;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D7 exam-fee flow end to end against real Postgres: booking never waits for payment (CONFIRMED + OPEN invoice at
 * once), the fee is paid any time before check-in (counter or PayOS webhook), unpaid blocks check-in with MSG-94, and
 * cancellation / no-show settle the fee item (no refund for the patient, REFUND_PENDING only for a clinic fault).
 * Only the PayOS gateway is faked. The QR check-in refusal is covered by AppointmentArrivalT053PostgresIntegrationTest.
 */
@SpringBootTest(properties = "app.payment.payos.enabled=false")
@AutoConfigureMockMvc
@LocalPostgresTest
@Import({BookingFeeFixtureSupport.ClockConfiguration.class, AppointmentFeePaymentFlowPostgresTest.GatewayConfiguration.class})
class AppointmentFeePaymentFlowPostgresTest extends BookingFeeFixtureSupport {
    private static final long PRICE = 150_000L;

    @Test
    void bookingConfirmsAtOnceAndOpensTheFeeInvoiceInTheSameTransaction() throws Exception {
        UUID appointment = bookPaid(patientA, SLOT);

        assertEquals("CONFIRMED", statusOf(appointment));
        UUID invoice = invoiceOf(appointment);
        assertEquals("OPEN", jdbc.queryForObject("select status from invoices where id = ?", String.class, invoice));
        assertEquals(1, count("select count(*) from invoice_items where invoice_id = ?", invoice));
        assertEquals(PRICE, jdbc.queryForObject("select unit_price_snapshot::bigint from invoice_items where invoice_id = ?",
                Long.class, invoice));
    }

    @Test
    void freeServiceBookingOpensNoInvoice() throws Exception {
        String body = createOnline(patientA, freeService, SLOT).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID appointment = UUID.fromString((String) json(body, "$.id"));

        assertEquals("CONFIRMED", statusOf(appointment));
        assertEquals(0, count("select count(*) from invoices where appointment_id = ?", appointment));
    }

    @Test
    void unpaidFeeBlocksCheckInUntilPaidLaterThenCheckInSucceeds() throws Exception {
        UUID appointment = bookPaid(patientA, SLOT);
        clock.set(SLOT.minusSeconds(600));

        checkIn(appointment, "fee-unpaid").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("MSG-94"));
        assertEquals("CONFIRMED", statusOf(appointment));

        // Paid later by the patient online: PayOS confirms through the webhook.
        long order = startOnlinePayment(invoiceOf(appointment));
        postWebhook(order, PRICE).andExpect(status().isOk());
        assertEquals("PAID", invoiceStatus(invoiceOf(appointment)));
        assertEquals("CONFIRMED", statusOf(appointment));

        checkIn(appointment, "fee-paid").andExpect(status().isOk())
                .andExpect(jsonPath("$.queueNumber").value(1));
        assertEquals("CHECKED_IN", statusOf(appointment));
    }

    @Test
    void feePaidAtTheCounterAllowsCheckIn() throws Exception {
        UUID appointment = bookPaid(patientA, SLOT);
        payAtCounter(invoiceOf(appointment), "fee-counter").andExpect(status().isOk());
        clock.set(SLOT.minusSeconds(600));

        checkIn(appointment, "fee-counter-in").andExpect(status().isOk());
        assertEquals("CHECKED_IN", statusOf(appointment));
    }

    @Test
    void patientCancellationOfAPaidAppointmentKeepsTheFeeWithNoRefund() throws Exception {
        UUID appointment = bookPaid(patientA, SLOT);
        UUID invoice = invoiceOf(appointment);
        payAtCounter(invoice, "keep-fee").andExpect(status().isOk());

        cancel(appointment, patientA, AccountRole.PATIENT, "{}").andExpect(status().isOk());

        assertEquals("CANCELLED", statusOf(appointment));
        assertNull(refundStatus(invoice));
        assertEquals(0, count("select count(*) from invoice_items where invoice_id = ? and voided_at is not null", invoice));
        assertEquals("PAID", invoiceStatus(invoice));
    }

    @Test
    void patientCancellationOfAnUnpaidAppointmentVoidsTheFeeItem() throws Exception {
        UUID appointment = bookPaid(patientA, SLOT);
        UUID invoice = invoiceOf(appointment);

        cancel(appointment, patientA, AccountRole.PATIENT, "{}").andExpect(status().isOk());

        assertEquals("CANCELLED", statusOf(appointment));
        assertEquals(1, count("select count(*) from invoice_items where invoice_id = ? and voided_at is not null", invoice));
        assertNull(refundStatus(invoice));
    }

    @Test
    void clinicFaultCancellationOfAPaidAppointmentAwaitsARefundAndNotifiesThePatient() throws Exception {
        UUID appointment = bookPaid(patientA, SLOT);
        UUID invoice = invoiceOf(appointment);
        payAtCounter(invoice, "clinic-fault").andExpect(status().isOk());

        cancel(appointment, receptionist, AccountRole.RECEPTIONIST, "{\"reason\":\"Doctor absent\",\"clinicFault\":true}").andExpect(status().isOk());

        assertEquals("CANCELLED", statusOf(appointment));
        assertEquals("REFUND_PENDING", refundStatus(invoice));
        assertEquals(1, count("select count(*) from notifications where recipient_account_id = ? and related_id = ?",
                patientA, invoice));
    }

    @Test
    void staffCancellationWithoutClinicFaultDoesNotCreateARefund() throws Exception {
        UUID appointment = bookPaid(patientA, SLOT);
        UUID invoice = invoiceOf(appointment);
        payAtCounter(invoice, "no-fault").andExpect(status().isOk());

        cancel(appointment, receptionist, AccountRole.RECEPTIONIST, "{\"reason\":\"Walk-in request\"}").andExpect(status().isOk());

        assertNull(refundStatus(invoice));
    }

    @Test
    void aPatientCannotClaimClinicFaultToGetARefund() throws Exception {
        UUID appointment = bookPaid(patientA, SLOT);
        UUID invoice = invoiceOf(appointment);
        payAtCounter(invoice, "claim-fault").andExpect(status().isOk());

        cancel(appointment, patientA, AccountRole.PATIENT, "{\"clinicFault\":true}").andExpect(status().isOk());

        assertNull(refundStatus(invoice));
    }

    @Test
    void moneyArrivingAfterTheAppointmentWasCancelledAwaitsARefund() throws Exception {
        UUID appointment = bookPaid(patientA, SLOT);
        UUID invoice = invoiceOf(appointment);
        long order = startOnlinePayment(invoice);

        cancel(appointment, patientA, AccountRole.PATIENT, "{}").andExpect(status().isOk());
        postWebhook(order, PRICE).andExpect(status().isOk());

        assertEquals("CONFIRMED", jdbc.queryForObject("select status from payment_attempts where provider_order_code = ?",
                String.class, order));
        assertEquals("REFUND_PENDING", refundStatus(invoice));
        assertEquals("CANCELLED", statusOf(appointment));
    }

    @Test
    void noShowOfAnUnpaidAppointmentVoidsTheFeeItem() throws Exception {
        UUID appointment = bookPaid(patientA, SLOT);
        UUID invoice = invoiceOf(appointment);
        clock.set(SLOT.plusSeconds(7200));

        mvc.perform(patch("/api/v1/appointments/{id}/status", appointment)
                        .with(actor(receptionist, AccountRole.RECEPTIONIST)).with(csrf())
                        .contentType("application/json").content("{\"targetStatus\":\"NO_SHOW\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NO_SHOW"));

        assertEquals(1, count("select count(*) from invoice_items where invoice_id = ? and voided_at is not null", invoice));
    }

    // ---- helpers ---------------------------------------------------------------------------------------------

    private UUID invoiceOf(UUID appointment) {
        return jdbc.queryForObject("select id from invoices where appointment_id = ?", UUID.class, appointment);
    }

    private String invoiceStatus(UUID invoice) {
        return jdbc.queryForObject("select status from invoices where id = ?", String.class, invoice);
    }

    private String refundStatus(UUID invoice) {
        return jdbc.queryForObject("select refund_status from invoice_items where invoice_id = ?", String.class, invoice);
    }

    private ResultActions checkIn(UUID appointment, String key) throws Exception {
        UUID profile = jdbc.queryForObject("select patient_profile_id from appointments where id = ?", UUID.class, appointment);
        return mvc.perform(post("/api/v1/appointments/{id}/check-in", appointment)
                .with(actor(receptionist, AccountRole.RECEPTIONIST)).with(csrf()).header("Idempotency-Key", key)
                .contentType("application/json").content("{\"verifiedPatientProfileId\":\"" + profile + "\"}"));
    }

    private ResultActions cancel(UUID appointment, UUID account, AccountRole role, String body) throws Exception {
        return mvc.perform(post("/api/v1/appointments/{id}/cancel", appointment)
                .with(actor(account, role)).with(csrf()).contentType("application/json").content(body));
    }

    private ResultActions payAtCounter(UUID invoice, String key) throws Exception {
        List<UUID> items = jdbc.queryForList("select id from invoice_items where invoice_id = ? order by line_number",
                UUID.class, invoice);
        String ids = String.join(",", items.stream().map(id -> "\"" + id + "\"").toList());
        return mvc.perform(post("/api/v1/invoices/{id}/counter-payments", invoice)
                .with(actor(receptionist, AccountRole.RECEPTIONIST)).with(csrf()).header("Idempotency-Key", key)
                .contentType("application/json").content("{\"itemIds\":[" + ids + "],\"method\":\"CASH\"}"));
    }

    /** The patient opens an online (PayOS) payment of the fee; returns the order code the gateway would report. */
    private long startOnlinePayment(UUID invoice) throws Exception {
        mvc.perform(post("/api/v1/invoices/{id}/payment-attempts", invoice)
                        .with(actor(patientA, AccountRole.PATIENT)).with(csrf()).header("Idempotency-Key", "online-" + invoice))
                .andExpect(status().isOk());
        return jdbc.queryForObject("select provider_order_code from payment_attempts where invoice_id = ?", Long.class, invoice);
    }

    private ResultActions postWebhook(long orderCode, long amount) throws Exception {
        String body = "{\"orderCode\":" + orderCode + ",\"amount\":" + amount + ",\"paymentLinkId\":\"link-" + orderCode
                + "\",\"reference\":\"ref-" + orderCode + "\",\"success\":true}";
        return mvc.perform(post("/api/v1/payment-webhooks/payos").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    // ---- PayOS stand-in --------------------------------------------------------------------------------------

    @TestConfiguration(proxyBeanMethods = false)
    static class GatewayConfiguration {
        @Bean FakePayOs fakePayOs() { return new FakePayOs(); }
    }

    static final class FakePayOs implements PaymentGateway {
        final Map<Long, GatewayPaymentStatus> statuses = new ConcurrentHashMap<>();

        @Override public String provider() { return "PAYOS"; }
        @Override public long linkTtlSeconds() { return 900; }
        @Override public GatewayLink createLink(GatewayPaymentCommand command) {
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
            return statuses.getOrDefault(orderCode,
                    new GatewayPaymentStatus("PENDING", orderCode, PRICE, 0, "link-" + orderCode, null, "VND"));
        }
    }
}
