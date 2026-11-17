package fu.fa26.sep490.g58.otcms_be.billing;

import fu.fa26.sep490.g58.otcms_be.billing.model.PaymentChannel;
import fu.fa26.sep490.g58.otcms_be.billing.model.dto.request.CounterPaymentRequest;
import fu.fa26.sep490.g58.otcms_be.billing.model.dto.request.PaymentAttemptRequest;
import fu.fa26.sep490.g58.otcms_be.billing.model.dto.response.InvoiceResponse;
import fu.fa26.sep490.g58.otcms_be.billing.model.dto.response.PaymentAttemptResponse;
import fu.fa26.sep490.g58.otcms_be.billing.service.BillingRefundService;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import java.util.List;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** UC-53 / UC-56 / UC-57 / UC-58 read APIs on real Postgres: unpaid items, patient billing, payment detail, receipt JSON and PDF. */
class BillingDetailPostgresTest extends HoldBillingTestSupport {
    @Autowired BillingRefundService refunds;

    private static RequestPostProcessor as(AuthenticatedAccount principal) {
        return authentication(new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + principal.accountRole().name()))));
    }

    private AuthenticatedAccount admin() { return new AuthenticatedAccount(doctorAccountId, AccountRole.ADMIN, UUID.randomUUID()); }

    private void addItem(UUID invoiceId, UUID id, int line, long price) {
        jdbc.update("insert into invoice_items (id, invoice_id, line_number, service_id, description_snapshot, quantity, "
                + "unit_price_snapshot) values (?, ?, ?, ?, 'Extra item', 1, ?)", id, invoiceId, line, serviceId, price);
    }

    private PaymentAttemptResponse cash(UUID invoiceId, UUID... items) {
        return payments.recordCounterPayment(invoiceId, new CounterPaymentRequest(List.of(items), "CASH"),
                receptionist, "cash-" + UUID.randomUUID());
    }

    @Test
    void unpaidItemsListsPayableAndCheckoutItemsButNotPaidOnes() throws Exception {
        InvoiceResponse invoice = invoiceFor(appointment("CONFIRMED", null));
        UUID i1 = UUID.randomUUID(), i2 = UUID.randomUUID();
        addItem(invoice.id(), i1, 2, 50_000);
        addItem(invoice.id(), i2, 3, 70_000);
        cash(invoice.id(), invoice.items().getFirst().id());
        payments.createOnlineAttempt(invoice.id(), new PaymentAttemptRequest(List.of(i1), PaymentChannel.ONLINE), patient, "u-" + UUID.randomUUID());

        mockMvc.perform(get("/api/v1/invoice-items/unpaid").with(as(receptionist)).param("patientId", patientProfileId.toString()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].itemId").value(i1.toString()))
                .andExpect(jsonPath("$.items[0].status").value("PAYMENT_PENDING"))
                .andExpect(jsonPath("$.items[0].patientName").value("Hold Billing Patient"))
                .andExpect(jsonPath("$.items[0].appointmentId").isNotEmpty())
                .andExpect(jsonPath("$.items[0].lineTotal").value(50000.0))
                .andExpect(jsonPath("$.items[1].itemId").value(i2.toString()))
                .andExpect(jsonPath("$.items[1].status").value("PENDING"));
        // Filters: another service or another patient matches nothing; a reversed period is MSG-54.
        mockMvc.perform(get("/api/v1/invoice-items/unpaid").with(as(admin())).param("patientId", patientProfileId.toString())
                        .param("serviceId", UUID.randomUUID().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0));
        mockMvc.perform(get("/api/v1/invoice-items/unpaid").with(as(receptionist)).param("patientId", UUID.randomUUID().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0));
        mockMvc.perform(get("/api/v1/invoice-items/unpaid").with(as(receptionist))
                        .param("from", "2026-12-02").param("to", "2026-12-01"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("MSG-54"));
    }

    @Test
    void voidItemsAreNotUnpaid() throws Exception {
        InvoiceResponse invoice = invoiceFor(appointment("CONFIRMED", null));
        refunds.requestRefund(invoice.items().getFirst().id(), "clinic cancelled");

        mockMvc.perform(get("/api/v1/invoice-items/unpaid").with(as(receptionist)).param("patientId", patientProfileId.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void patientBillingPutsInvoicesWithSomethingToPayFirstAndKnowsOnlyExistingPatients() throws Exception {
        InvoiceResponse older = invoiceFor(appointment("CONFIRMED", null));
        InvoiceResponse newer = invoiceFor(appointment("CONFIRMED", null));
        cash(newer.id(), newer.items().getFirst().id());

        mockMvc.perform(get("/api/v1/reception/patients/{id}/billing", patientProfileId).with(as(receptionist)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.patientName").value("Hold Billing Patient"))
                .andExpect(jsonPath("$.invoices.totalItems").value(2))
                .andExpect(jsonPath("$.invoices.items[0].id").value(older.id().toString()))
                .andExpect(jsonPath("$.invoices.items[0].items[0].status").value("PENDING"))
                .andExpect(jsonPath("$.invoices.items[1].id").value(newer.id().toString()))
                .andExpect(jsonPath("$.invoices.items[1].items[0].status").value("PAID"));
        mockMvc.perform(get("/api/v1/reception/patients/{id}/billing", UUID.randomUUID()).with(as(receptionist)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("MSG-41"));
        mockMvc.perform(get("/api/v1/reception/patients/{id}/billing", patientProfileId).with(as(patient)))
                .andExpect(status().isForbidden());
    }

    @Test
    void counterPaymentDetailReceiptAndPdf() throws Exception {
        InvoiceResponse invoice = invoiceFor(appointment("CONFIRMED", null));
        UUID paymentId = cash(invoice.id(), invoice.items().getFirst().id()).id();

        // The owner patient and staff see it; another patient does not (MSG-50).
        mockMvc.perform(get("/api/v1/payments/{id}", paymentId).with(as(patient)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.method").value("CASH"))
                .andExpect(jsonPath("$.channel").value("COUNTER")).andExpect(jsonPath("$.amount").value(125000.0))
                .andExpect(jsonPath("$.patientName").value("Hold Billing Patient"))
                .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].status").value("PAID"))
                .andExpect(jsonPath("$.receiptNumber").value(org.hamcrest.Matchers.startsWith("RC-")));
        var stranger = new AuthenticatedAccount(UUID.randomUUID(), AccountRole.PATIENT, UUID.randomUUID());
        mockMvc.perform(get("/api/v1/payments/{id}", paymentId).with(as(stranger)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("MSG-50"));
        mockMvc.perform(get("/api/v1/payments/{id}", UUID.randomUUID()).with(as(receptionist)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("MSG-50"));

        // Receipt: receptionist only, same data as JSON, and the PDF carries the Vietnamese text and the amount.
        mockMvc.perform(get("/api/v1/payments/{id}/receipt", paymentId).with(as(receptionist)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.invoiceNumber").value(invoice.invoiceNumber()));
        mockMvc.perform(get("/api/v1/payments/{id}/receipt", paymentId).with(as(patient))).andExpect(status().isForbidden());
        byte[] pdf = mockMvc.perform(get("/api/v1/payments/{id}/receipt.pdf", paymentId).with(as(receptionist)))
                .andExpect(status().isOk()).andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals("%PDF".getBytes(), java.util.Arrays.copyOf(pdf, 4));
        try (var document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("BIÊN LAI THANH TOÁN"), text);
            assertTrue(text.contains("Hold Billing Patient"), text);
            assertTrue(text.contains("125.000"), text);
        }
    }

    @Test
    void payosPaymentShowsTheProviderReferenceAndAdminSeesTheRefundReason() throws Exception {
        InvoiceResponse invoice = invoiceFor(appointment("CONFIRMED", null));
        long order = orderCode(payments.createOnlineAttempt(invoice.id(), patient, "p-" + UUID.randomUUID()).id());
        payments.handleVerifiedGatewayEvent(paidEvent(order));
        UUID paymentId = jdbc.queryForObject("select id from payment_attempts where provider_order_code = ?", UUID.class, order);
        refunds.requestRefund(invoice.items().getFirst().id(), "clinic cancelled");

        mockMvc.perform(get("/api/v1/payments/{id}", paymentId).with(as(admin())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.method").value("PAYOS"))
                .andExpect(jsonPath("$.providerTransactionReference").value("ref-" + order))
                .andExpect(jsonPath("$.receivedByName").doesNotExist())
                .andExpect(jsonPath("$.items[0].status").value("REFUND_PENDING"))
                .andExpect(jsonPath("$.items[0].refundReason").value("clinic cancelled"));
        mockMvc.perform(get("/api/v1/payments/{id}", paymentId).with(as(receptionist)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].refundReason").doesNotExist());
    }

    @Test
    void anUnconfirmedAttemptIsNotAPayment() throws Exception {
        InvoiceResponse invoice = invoiceFor(appointment("CONFIRMED", null));
        UUID pending = payments.createOnlineAttempt(invoice.id(), patient, "n-" + UUID.randomUUID()).id();

        mockMvc.perform(get("/api/v1/payments/{id}", pending).with(as(receptionist)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("MSG-50"));
        mockMvc.perform(get("/api/v1/payments/{id}/receipt.pdf", pending).with(as(receptionist)))
                .andExpect(status().isNotFound());
    }
}
