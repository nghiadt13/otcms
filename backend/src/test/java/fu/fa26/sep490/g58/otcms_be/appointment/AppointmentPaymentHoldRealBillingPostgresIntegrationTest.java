package fu.fa26.sep490.g58.otcms_be.appointment;

import fu.fa26.sep490.g58.otcms_be.billing.service.InvoiceService;
import fu.fa26.sep490.g58.otcms_be.billing.service.PaymentGateway;
import fu.fa26.sep490.g58.otcms_be.billing.service.PaymentService;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Releasing a hold against the REAL billing (invoice, payment attempt, settlement and the confirm hook), faking only
 * the PayOS gateway. Guards the money path: when the gateway says the QR was paid, cancelling / leaving / re-booking
 * must leave the appointment CONFIRMED, the invoice PAID and the payment attempt CONFIRMED committed in the database.
 */
@SpringBootTest(properties = "app.payment.payos.enabled=false")
@AutoConfigureMockMvc
@LocalPostgresTest
@Import({PaymentHoldFixtureSupport.ClockConfiguration.class,
        AppointmentPaymentHoldRealBillingPostgresIntegrationTest.GatewayConfiguration.class})
class AppointmentPaymentHoldRealBillingPostgresIntegrationTest extends PaymentHoldFixtureSupport {
    private static final long PRICE = 150_000L;

    @Autowired InvoiceService invoices;
    @Autowired PaymentService payments;
    @Autowired FakePayOs gateway;

    @BeforeEach
    void resetGateway() {
        gateway.statuses.clear();
    }

    /** A hold with an invoice and a live QR; returns {appointmentId, invoiceId, attemptId, orderCode}. */
    private Held holdWithQr(UUID account, Instant start) throws Exception {
        UUID appointment = createHold(account, start);
        var principal = new AuthenticatedAccount(account, AccountRole.PATIENT, UUID.randomUUID());
        UUID invoice = invoices.createForAppointment(appointment, principal).id();
        UUID attempt = payments.createOnlineAttempt(invoice, principal, "hold-" + UUID.randomUUID()).id();
        long order = jdbc.queryForObject("select provider_order_code from payment_attempts where id = ?", Long.class, attempt);
        return new Held(appointment, invoice, attempt, order);
    }

    private record Held(UUID appointmentId, UUID invoiceId, UUID attemptId, long orderCode) { }

    private String invoiceStatus(UUID id) {
        return jdbc.queryForObject("select status from invoices where id = ?", String.class, id);
    }

    private String attemptStatus(UUID id) {
        return jdbc.queryForObject("select status from payment_attempts where id = ?", String.class, id);
    }

    @Test
    void cancellingAHoldWhoseQrWasPaidKeepsThePaymentConfirmationCommitted() throws Exception {
        Held held = holdWithQr(patientA, SLOT);
        gateway.paid(held.orderCode());

        mvc.perform(post("/api/v1/appointments/" + held.appointmentId() + "/cancel")
                        .with(actor(patientA, AccountRole.PATIENT)).with(csrf())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("APPOINTMENT_NOT_CHANGEABLE"));

        // The 409 must not undo the money: everything billing confirmed is durable.
        assertEquals("CONFIRMED", statusOf(held.appointmentId()));
        assertEquals("PAID", invoiceStatus(held.invoiceId()));
        assertEquals("CONFIRMED", attemptStatus(held.attemptId()));
        assertEquals(1, notificationsFor(held.appointmentId()));
    }

    @Test
    void leavingThePaymentStepAfterPayingReportsAlreadyPaidAndKeepsTheBooking() throws Exception {
        Held held = holdWithQr(patientA, SLOT);
        gateway.paid(held.orderCode());

        mvc.perform(post("/api/v1/appointments/pending-hold/cancel").with(actor(patientA, AccountRole.PATIENT)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cancelledCount").value(0))
                .andExpect(jsonPath("$.alreadyPaid").value(true));

        assertEquals("CONFIRMED", statusOf(held.appointmentId()));
        assertEquals("PAID", invoiceStatus(held.invoiceId()));
    }

    @Test
    void bookingAgainWhileTheOldQrWasPaidKeepsTheOldBookingConfirmed() throws Exception {
        Held old = holdWithQr(patientA, SLOT);
        gateway.paid(old.orderCode());

        UUID fresh = createHold(patientA, SLOT_2);

        assertEquals("CONFIRMED", statusOf(old.appointmentId()));
        assertEquals("PAID", invoiceStatus(old.invoiceId()));
        assertEquals("CONFIRMED", attemptStatus(old.attemptId()));
        assertEquals("AWAITING_PAYMENT", statusOf(fresh));
    }

    @Test
    void cancellingAnUnpaidHoldVoidsTheInvoiceAndFreesTheSlot() throws Exception {
        Held held = holdWithQr(patientA, SLOT);

        mvc.perform(post("/api/v1/appointments/" + held.appointmentId() + "/cancel")
                        .with(actor(patientA, AccountRole.PATIENT)).with(csrf())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAYMENT_EXPIRED"));

        assertEquals("PAYMENT_EXPIRED", statusOf(held.appointmentId()));
        assertEquals("VOID", invoiceStatus(held.invoiceId()));
        createOnline(patientB, paidService, SLOT).andExpect(status().isCreated());
    }

    // ---- PayOS stand-in ----------------------------------------------------------------------------------------

    @TestConfiguration(proxyBeanMethods = false)
    static class GatewayConfiguration {
        @Bean FakePayOs fakePayOs() { return new FakePayOs(); }
    }

    static final class FakePayOs implements PaymentGateway {
        final Map<Long, GatewayPaymentStatus> statuses = new ConcurrentHashMap<>();

        void paid(long orderCode) {
            statuses.put(orderCode, new GatewayPaymentStatus("PAID", orderCode, PRICE, PRICE, "link-" + orderCode,
                    "ref-" + orderCode, "VND"));
        }

        @Override public String provider() { return "PAYOS"; }
        @Override public long linkTtlSeconds() { return 900; }
        @Override public GatewayLink createLink(GatewayPaymentCommand command) {
            return new GatewayLink("link-" + command.orderCode(), command.orderCode(),
                    "https://pay.test/" + command.orderCode(), "qr-" + command.orderCode(), command.expiresAt(),
                    new TransferInfo("970422", "0123456789", "OTCMS", "ORDER " + command.orderCode()));
        }
        @Override public VerifiedGatewayEvent verifyWebhook(Object payload) {
            throw new UnsupportedOperationException("webhooks are not used in this test");
        }
        @Override public GatewayPaymentStatus query(long orderCode) {
            return statuses.getOrDefault(orderCode,
                    new GatewayPaymentStatus("PENDING", orderCode, PRICE, 0, "link-" + orderCode, null, "VND"));
        }
    }
}
