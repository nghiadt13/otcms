package fu.fa26.sep490.g58.otcms_be.billing;

import fu.fa26.sep490.g58.otcms_be.billing.exception.BillingException;
import fu.fa26.sep490.g58.otcms_be.billing.service.AppointmentHoldSettlementService.Outcome;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Money path of the online booking hold: webhook confirms, late payment never crashes and never flips VOID. */
class AppointmentHoldPaymentFlowPostgresTest extends HoldBillingTestSupport {

    // ---- invoice creation --------------------------------------------------------------------------------------

    @Test
    void invoiceIsCreatedForHeldAndConfirmedAppointmentsButNotForReleasedOnes() {
        assertEquals("OPEN", invoiceFor(hold(Duration.ofMinutes(15))).status());
        assertEquals("OPEN", invoiceFor(appointment("CONFIRMED", null)).status());
        for (String status : List.of("CANCELLED", "PAYMENT_EXPIRED")) {
            UUID released = appointment(status, null);
            BillingException refused = assertThrows(BillingException.class, () -> invoiceFor(released));
            assertEquals("SOURCE_NOT_BILLABLE", refused.code());
        }
    }

    @Test
    void onlyThePatientMayOpenTheInvoiceOfAnAwaitingPaymentHold() {
        UUID held = hold(Duration.ofMinutes(15));
        var asOwner = invoiceFor(held);
        // Staff cannot see the hold, so neither a new invoice nor the existing one may be opened by them.
        assertEquals("SOURCE_NOT_BILLABLE", assertThrows(BillingException.class,
                () -> invoices.createForAppointment(held, receptionist)).code());
        UUID heldWithoutInvoice = hold(Duration.ofMinutes(15));
        assertEquals("SOURCE_NOT_BILLABLE", assertThrows(BillingException.class,
                () -> invoices.createForAppointment(heldWithoutInvoice, receptionist)).code());
        // A patient who does not own the profile is denied; the owner still gets the same invoice back.
        var stranger = new fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount(UUID.randomUUID(),
                fu.fa26.sep490.g58.otcms_be.common.security.AccountRole.PATIENT, UUID.randomUUID());
        assertEquals("ACCESS_DENIED", assertThrows(BillingException.class,
                () -> invoices.createForAppointment(held, stranger)).code());
        assertEquals(asOwner.id(), invoiceFor(held).id());
        // Receptionist/legacy CONFIRMED bookings keep working for receptionists.
        assertEquals("OPEN", invoices.createForAppointment(appointment("CONFIRMED", null), receptionist).status());
        assertEquals(0, jdbc.queryForObject("select count(*) from invoices where appointment_id = ?",
                Integer.class, heldWithoutInvoice));
    }

    @Test
    void staffUnpaidListHidesHoldsAndOrphansButKeepsConfirmedAppointmentInvoices() {
        var admin = new fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount(UUID.randomUUID(),
                fu.fa26.sep490.g58.otcms_be.common.security.AccountRole.ADMIN, UUID.randomUUID());
        long before = billingReads.unpaid(receptionist, 0, 1).totalItems();

        UUID heldAppointment = hold(Duration.ofMinutes(15));
        UUID heldInvoice = invoiceFor(heldAppointment).id();
        UUID orphanAppointment = hold(Duration.ofMinutes(15));
        UUID orphanInvoice = invoiceFor(orphanAppointment).id();
        setAppointmentStatus(orphanAppointment, "PAYMENT_EXPIRED"); // released, invoice still OPEN
        UUID visibleInvoice = invoiceFor(appointment("CONFIRMED", null)).id();

        for (var staff : List.of(receptionist, admin)) {
            var page = billingReads.unpaid(staff, 0, 1);
            assertEquals(before + 1, page.totalItems(), "count must exclude holds and orphans");
            assertEquals((int) Math.max(1, before + 1), page.totalPages());
        }
        List<UUID> shown = new java.util.ArrayList<>();
        for (int p = 0; ; p++) {
            var items = billingReads.unpaid(receptionist, p, 50).items();
            if (items.isEmpty()) break;
            items.forEach(b -> shown.add(b.id()));
        }
        assertTrue(shown.contains(visibleInvoice));
        assertTrue(!shown.contains(heldInvoice) && !shown.contains(orphanInvoice));

        // Once the booking is CONFIRMED the invoice is an ordinary unpaid invoice for staff.
        setAppointmentStatus(heldAppointment, "CONFIRMED");
        assertEquals(before + 2, billingReads.unpaid(receptionist, 0, 1).totalItems());
    }

    // ---- payment confirms the hold -----------------------------------------------------------------------------

    @Test
    void webhookPaidConfirmsAppointmentInTheSameTransactionAndDuplicateDoesNotConfirmAgain() {
        Held held = heldWithQr();
        payments.handleVerifiedGatewayEvent(paidEvent(held.orderCode()));

        assertEquals("CONFIRMED", appointmentStatus(held.appointmentId()));
        assertEquals("PAID", invoiceStatus(held.invoiceId()));
        assertEquals("CONFIRMED", attemptStatus(held.attemptId()));
        assertEquals(1, appointmentPort.confirmedTransitions.get());

        // Exact redelivery (same fingerprint) is ignored; a re-sent event with another reference is DUPLICATE.
        payments.handleVerifiedGatewayEvent(paidEvent(held.orderCode()));
        payments.handleVerifiedGatewayEvent(new fu.fa26.sep490.g58.otcms_be.billing.service.PaymentGateway.VerifiedGatewayEvent(
                "PAYOS", held.orderCode(), PRICE_LONG, "VND", "link-" + held.orderCode(), "ref-other", "00", true));
        assertEquals(1, appointmentPort.confirmedTransitions.get());
        assertEquals(1, appointmentPort.confirmCalls.get());
        assertEquals(List.of("APPLIED:", "DUPLICATE:"), eventOutcomes(held.orderCode()));
    }

    @Test
    void reconcileOfAPaidQrConfirmsTheHoldThroughTheSamePath() {
        Held held = heldWithQr();
        gateway.setStatus(held.orderCode(), "PAID");
        assertEquals("CONFIRMED", payments.reconcile(held.attemptId(), receptionist).status());
        payments.reconcile(held.attemptId(), receptionist);
        assertEquals("CONFIRMED", appointmentStatus(held.appointmentId()));
        assertEquals("PAID", invoiceStatus(held.invoiceId()));
        assertEquals(1, appointmentPort.confirmedTransitions.get());
    }

    // ---- QR lifetime -------------------------------------------------------------------------------------------

    @Test
    void qrExpiryIsTheEarlierOfLinkTtlAndHoldDeadline() {
        UUID shortHold = hold(Duration.ofMinutes(5));
        qr(invoiceFor(shortHold).id());
        assertEquals(clock.instant().plus(Duration.ofMinutes(5)), gateway.commands.getLast().expiresAt());

        UUID longHold = hold(Duration.ofMinutes(60));
        qr(invoiceFor(longHold).id());
        assertEquals(clock.instant().plusSeconds(900), gateway.commands.getLast().expiresAt());
    }

    @Test
    void legacyConfirmedAppointmentQrKeepsTheFullTtl() {
        qr(invoiceFor(appointment("CONFIRMED", null)).id());
        assertEquals(clock.instant().plusSeconds(900), gateway.commands.getLast().expiresAt());
    }

    @Test
    void noQrIsIssuedOnceTheHoldDeadlineHasPassedOrTheHoldWasReleased() {
        UUID expiredHold = hold(Duration.ofMinutes(15));
        UUID expiredInvoice = invoiceFor(expiredHold).id();
        clock.advance(Duration.ofMinutes(16));
        assertEquals("INVOICE_NOT_PAYABLE", assertThrows(BillingException.class, () -> qr(expiredInvoice)).code());

        UUID released = hold(Duration.ofMinutes(15));
        UUID releasedInvoice = invoiceFor(released).id();
        setAppointmentStatus(released, "PAYMENT_EXPIRED");
        assertEquals("INVOICE_NOT_PAYABLE", assertThrows(BillingException.class, () -> qr(releasedInvoice)).code());
        assertEquals(0, gateway.commands.size());
    }

    @Test
    void createOnlineAttemptAfterVoidIsNotPayable() {
        UUID appointmentId = hold(Duration.ofMinutes(15));
        UUID invoiceId = invoiceFor(appointmentId).id();
        assertEquals(Outcome.VOIDED, settlement.settle(appointmentId, true));
        assertEquals("VOID", invoiceStatus(invoiceId));
        assertEquals("INVOICE_NOT_PAYABLE", assertThrows(BillingException.class, () -> qr(invoiceId)).code());
    }

    // ---- late payment: reconciliation, never a crash, never VOID -> PAID ------------------------------------------

    @Test
    void latePaidWebhookAfterVoidIsReconciliationWith2xxAndTheInvoiceStaysVoid() throws Exception {
        Held held = heldWithQr();
        assertEquals(Outcome.VOIDED, settlement.settle(held.appointmentId(), true));
        setAppointmentStatus(held.appointmentId(), "PAYMENT_EXPIRED");

        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(
                fu.fa26.sep490.g58.otcms_be.billing.service.impl.InvoiceSettlementSupport.class);
        var captured = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        captured.start();
        logger.addAppender(captured);
        try {
            assertEquals(200, postWebhook(held.orderCode(), PRICE_LONG, "ref-late"));
        } finally {
            logger.detachAppender(captured);
        }
        // Ops trail: ids, amount and reason; no checkout URL / QR / personal data.
        var warnings = captured.list.stream().filter(e -> e.getLevel() == ch.qos.logback.classic.Level.WARN)
                .map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage).toList();
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().contains("orderCode=" + held.orderCode()));
        assertTrue(warnings.getFirst().contains("invoiceId=" + held.invoiceId()));
        assertTrue(warnings.getFirst().contains("attemptId=" + held.attemptId()));
        assertTrue(warnings.getFirst().contains("amount=125000"));
        assertTrue(!warnings.getFirst().contains("https://") && !warnings.getFirst().contains("qr-"));

        assertEquals("VOID", invoiceStatus(held.invoiceId()));
        assertEquals("RECONCILIATION_REQUIRED", attemptStatus(held.attemptId()));
        assertNull(jdbc.queryForObject("select confirmed_at from payment_attempts where id = ?",
                java.sql.Timestamp.class, held.attemptId()));
        assertEquals("PAYMENT_EXPIRED", appointmentStatus(held.appointmentId()));
        assertEquals(List.of("RECONCILIATION_REQUIRED:INVOICE_VOID"), eventOutcomes(held.orderCode()));
        assertEquals(0, appointmentPort.confirmCalls.get());
    }

    @Test
    void latePaidReconcileAfterVoidIsReconciliationAndTheInvoiceStaysVoid() {
        Held held = heldWithQr();
        assertEquals(Outcome.VOIDED, settlement.settle(held.appointmentId(), true));
        gateway.setStatus(held.orderCode(), "PAID");

        assertEquals("RECONCILIATION_REQUIRED", payments.reconcile(held.attemptId(), receptionist).status());
        assertEquals("RECONCILIATION_REQUIRED", payments.reconcile(held.attemptId(), receptionist).status());
        assertEquals("VOID", invoiceStatus(held.invoiceId()));
        assertEquals(0, appointmentPort.confirmCalls.get());
    }

    @Test
    void paidWebhookForAReleasedAppointmentWithAnOpenInvoiceIsReconciledNotApplied() throws Exception {
        Held held = heldWithQr();
        setAppointmentStatus(held.appointmentId(), "PAYMENT_EXPIRED"); // released, invoice not voided yet

        assertEquals(200, postWebhook(held.orderCode(), PRICE_LONG, "ref-released"));

        assertEquals("OPEN", invoiceStatus(held.invoiceId()));
        assertEquals("RECONCILIATION_REQUIRED", attemptStatus(held.attemptId()));
        assertEquals("PAYMENT_EXPIRED", appointmentStatus(held.appointmentId()));
        assertEquals(List.of("RECONCILIATION_REQUIRED:APPOINTMENT_HOLD_RELEASED"), eventOutcomes(held.orderCode()));
        assertEquals(0, appointmentPort.confirmedTransitions.get());
    }

    @Test
    void paidWebhookForACancelledAppointmentIsReconciledWith2xx() throws Exception {
        Held held = heldWithQr();
        setAppointmentStatus(held.appointmentId(), "CANCELLED");

        assertEquals(200, postWebhook(held.orderCode(), PRICE_LONG, "ref-cancelled"));
        assertEquals("RECONCILIATION_REQUIRED", attemptStatus(held.attemptId()));
        assertEquals("OPEN", invoiceStatus(held.invoiceId()));
        assertEquals("CANCELLED", appointmentStatus(held.appointmentId()));
    }

    @Test
    void paidWebhookForAnAlreadyConfirmedAppointmentIsAppliedWithoutAnotherConfirmation() throws Exception {
        UUID confirmed = appointment("CONFIRMED", null);
        UUID invoiceId = invoiceFor(confirmed).id();
        UUID attemptId = qr(invoiceId).id();
        long code = orderCode(attemptId);

        assertEquals(200, postWebhook(code, PRICE_LONG, "ref-legacy"));

        assertEquals("PAID", invoiceStatus(invoiceId));
        assertEquals("CONFIRMED", appointmentStatus(confirmed));
        assertEquals(0, appointmentPort.confirmedTransitions.get());
    }

    @Test
    void duplicateWebhookOverHttpStays2xxAndConfirmsOnce() throws Exception {
        Held held = heldWithQr();
        assertEquals(200, postWebhook(held.orderCode(), PRICE_LONG, "ref-dup"));
        assertEquals(200, postWebhook(held.orderCode(), PRICE_LONG, "ref-dup"));
        assertEquals(200, postWebhook(held.orderCode(), PRICE_LONG, "ref-dup-2"));
        assertEquals(1, appointmentPort.confirmedTransitions.get());
        assertEquals("PAID", invoiceStatus(held.invoiceId()));
    }

    @Test
    void webhookForAnUnknownOrderOrWrongAmountIsRejectedWith2xx() throws Exception {
        assertEquals(200, postWebhook(987_654_321L, PRICE_LONG, "ref-unknown"));
        assertEquals(List.of("REJECTED:UNKNOWN_ORDER_CODE"), eventOutcomes(987_654_321L));

        Held held = heldWithQr();
        assertEquals(200, postWebhook(held.orderCode(), PRICE_LONG - 1, "ref-short"));
        assertEquals("PENDING", attemptStatus(held.attemptId()));
        assertEquals("AWAITING_PAYMENT", appointmentStatus(held.appointmentId()));
        jdbc.update("delete from payment_gateway_events where provider_order_code = ?", 987_654_321L);
    }

    // ---- my invoices -----------------------------------------------------------------------------------------

    @Test
    void listMineHidesVoidAndHeldAppointmentsAndPagesOverTheVisibleOnes() {
        // newest first: visible1, held, visible2, voided, visible3, released-but-open
        UUID visible3 = appointment("CONFIRMED", null);
        UUID released = hold(Duration.ofMinutes(15));
        UUID voided = hold(Duration.ofMinutes(15));
        UUID visible2 = appointment("CONFIRMED", null);
        UUID held = hold(Duration.ofMinutes(15));
        UUID visible1 = appointment("CONFIRMED", null);
        List<UUID> oldestToNewest = List.of(visible3, released, voided, visible2, held, visible1);
        int minutesAgo = oldestToNewest.size();
        for (UUID appointmentId : oldestToNewest) {
            UUID invoiceId = invoiceFor(appointmentId).id();
            jdbc.update("update invoices set issued_at = now() - (? * interval '1 minute') where id = ?",
                    minutesAgo--, invoiceId);
        }
        settlement.settle(voided, true);
        setAppointmentStatus(released, "PAYMENT_EXPIRED");

        var first = invoices.listMine(patient, 0, 2);
        var second = invoices.listMine(patient, 1, 2);
        var third = invoices.listMine(patient, 2, 2);
        assertEquals(List.of(visible1, visible2), first.items().stream().map(i -> i.appointmentId()).toList());
        assertTrue(first.hasMore());
        assertEquals(List.of(visible3), second.items().stream().map(i -> i.appointmentId()).toList());
        assertEquals(false, second.hasMore());
        assertEquals(0, third.items().size());
        assertEquals(false, third.hasMore());
        assertEquals(List.of(visible1), invoices.listMine(patient, 0, 1).items().stream().map(i -> i.appointmentId()).toList());
        assertTrue(invoices.listMine(patient, 0, 1).hasMore());
    }
}
