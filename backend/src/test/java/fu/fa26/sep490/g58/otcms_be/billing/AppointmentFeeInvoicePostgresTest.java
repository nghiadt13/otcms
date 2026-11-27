package fu.fa26.sep490.g58.otcms_be.billing;

import fu.fa26.sep490.g58.otcms_be.billing.exception.BillingException;
import fu.fa26.sep490.g58.otcms_be.billing.service.PaymentGateway;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exam-fee invoice of an appointment (D7): who may open it, the PayOS webhook and reconcile paths, late money. */
class AppointmentFeeInvoicePostgresTest extends HoldBillingTestSupport {
    private record Online(UUID appointmentId, UUID invoiceId, UUID attemptId, long orderCode) { }

    private Online online() {
        UUID appointment = appointment("CONFIRMED", null);
        UUID invoice = invoiceFor(appointment).id();
        UUID attempt = qr(invoice).id();
        return new Online(appointment, invoice, attempt, orderCode(attempt));
    }

    @Test
    void invoiceIsCreatedOnlyForConfirmedAppointments() {
        assertEquals("OPEN", invoiceFor(appointment("CONFIRMED", null)).status());
        for (String status : List.of("CANCELLED", "PAYMENT_EXPIRED")) {
            UUID closed = appointment(status, null);
            assertEquals("SOURCE_NOT_BILLABLE", assertThrows(BillingException.class, () -> invoiceFor(closed)).code());
        }
    }

    @Test
    void ownerAndReceptionistOpenTheSameInvoiceAndAStrangerIsDenied() {
        UUID appointment = appointment("CONFIRMED", null);
        var asOwner = invoiceFor(appointment);
        var stranger = new AuthenticatedAccount(UUID.randomUUID(), AccountRole.PATIENT, UUID.randomUUID());
        assertEquals("ACCESS_DENIED", assertThrows(BillingException.class,
                () -> invoices.createForAppointment(appointment, stranger)).code());
        assertEquals(asOwner.id(), invoices.createForAppointment(appointment, receptionist).id());
    }

    @Test
    void listMineHidesVoidInvoices() {
        UUID shown = invoiceFor(appointment("CONFIRMED", null)).id();
        UUID voided = invoiceFor(appointment("CONFIRMED", null)).id();
        jdbc.update("update invoices set status = 'VOID' where id = ?", voided);

        var ids = invoices.listMine(patient, 0, 10).items().stream().map(i -> i.id()).toList();
        assertEquals(List.of(shown), ids);
    }

    @Test
    void listMineRejectsBadPagingAsMsg39AndOtherRolesAsAccessDenied() {
        assertEquals("VALIDATION_FAILED", assertThrows(BillingException.class, () -> invoices.listMine(patient, -1, 10)).code());
        assertEquals("VALIDATION_FAILED", assertThrows(BillingException.class, () -> invoices.listMine(patient, 0, 0)).code());
        assertEquals("VALIDATION_FAILED", assertThrows(BillingException.class, () -> invoices.listMine(patient, 0, 51)).code());
        assertEquals("ACCESS_DENIED", assertThrows(BillingException.class,
                () -> invoices.listMine(receptionist, 0, 10)).code());
        assertEquals("ACCESS_DENIED", assertThrows(BillingException.class, () -> invoices.listMine(null, 0, 10)).code());
        assertDoesNotThrow(() -> invoices.listMine(patient, 0, 50));
    }

    @Test
    void webhookPaidSettlesTheInvoiceKeepsTheAppointmentAndADuplicateChangesNothing() {
        Online online = online();
        payments.handleVerifiedGatewayEvent(paidEvent(online.orderCode()));

        assertEquals("CONFIRMED", appointmentStatus(online.appointmentId()));
        assertEquals("PAID", invoiceStatus(online.invoiceId()));
        assertEquals("CONFIRMED", attemptStatus(online.attemptId()));

        // Exact redelivery is ignored; the same order with another reference is a DUPLICATE.
        payments.handleVerifiedGatewayEvent(paidEvent(online.orderCode()));
        payments.handleVerifiedGatewayEvent(new PaymentGateway.VerifiedGatewayEvent("PAYOS", online.orderCode(), PRICE_LONG,
                "VND", "link-" + online.orderCode(), "ref-other", "00", true));
        assertEquals(List.of("APPLIED:", "DUPLICATE:"), eventOutcomes(online.orderCode()));
    }

    @Test
    void reconcileOfAPaidQrSettlesTheInvoice() {
        Online online = online();
        gateway.setStatus(online.orderCode(), "PAID");

        assertEquals("CONFIRMED", payments.reconcile(online.attemptId(), receptionist).status());
        payments.reconcile(online.attemptId(), receptionist);
        assertEquals("PAID", invoiceStatus(online.invoiceId()));
    }

    @Test
    void duplicateWebhookOverHttpStays2xx() throws Exception {
        Online online = online();
        assertEquals(200, postWebhook(online.orderCode(), PRICE_LONG, "ref-dup"));
        assertEquals(200, postWebhook(online.orderCode(), PRICE_LONG, "ref-dup"));
        assertEquals(200, postWebhook(online.orderCode(), PRICE_LONG, "ref-dup-2"));
        assertEquals("PAID", invoiceStatus(online.invoiceId()));
    }

    @Test
    void webhookForAnUnknownOrderOrWrongAmountIsRejectedWith2xx() throws Exception {
        // audit_logs rows cannot be deleted, so use an order code no other run has used.
        long unknownOrderCode = ThreadLocalRandom.current().nextLong(1L << 40, 1L << 52);
        assertEquals(200, postWebhook(unknownOrderCode, PRICE_LONG, "ref-unknown"));
        assertEquals(List.of("REJECTED:UNKNOWN_ORDER_CODE"), eventOutcomes(unknownOrderCode));

        Online online = online();
        assertEquals(200, postWebhook(online.orderCode(), PRICE_LONG - 1, "ref-short"));
        assertEquals("PENDING", attemptStatus(online.attemptId()));
        assertEquals("OPEN", invoiceStatus(online.invoiceId()));
    }

    @Test
    void unreadableWebhookIsAuditedAndNeverApplied() throws Exception {
        Online online = online();
        String rejectedSql = "select count(*) from audit_logs where source = 'payment_gateway_events' "
                + "and action = 'REJECTED' and metadata->>'signatureVerified' = 'false'";
        int before = jdbc.queryForObject(rejectedSql, Integer.class);

        var response = postRawWebhook("{}");
        assertEquals(400, response.getStatus());
        assertTrue(response.getContentAsString().contains("INVALID_WEBHOOK"), response.getContentAsString());

        assertEquals(before + 1, jdbc.queryForObject(rejectedSql, Integer.class));
        assertEquals("PENDING", attemptStatus(online.attemptId()));
        assertEquals("OPEN", invoiceStatus(online.invoiceId()));
    }

    @Test
    void latePaidWebhookAfterTheInvoiceWasVoidedIsFlaggedForStaffWith2xx() throws Exception {
        Online online = online();
        jdbc.update("update invoices set status = 'VOID' where id = ?", online.invoiceId());

        assertEquals(200, postWebhook(online.orderCode(), PRICE_LONG, "ref-late"));

        assertEquals("VOID", invoiceStatus(online.invoiceId()));
        assertEquals("RECONCILIATION_REQUIRED", attemptStatus(online.attemptId()));
        assertEquals(List.of("RECONCILIATION_REQUIRED:INVOICE_VOID"), eventOutcomes(online.orderCode()));
    }
}
