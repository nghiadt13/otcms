package fu.fa26.sep490.g58.otcms_be.billing;

import fu.fa26.sep490.g58.otcms_be.billing.exception.BillingException;
import fu.fa26.sep490.g58.otcms_be.billing.model.PaymentChannel;
import fu.fa26.sep490.g58.otcms_be.billing.model.dto.request.CounterPaymentRequest;
import fu.fa26.sep490.g58.otcms_be.billing.model.dto.request.PaymentAttemptRequest;
import fu.fa26.sep490.g58.otcms_be.billing.model.dto.response.PaymentAttemptResponse;
import fu.fa26.sep490.g58.otcms_be.billing.service.PaymentGateway;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Payment per invoice item on real Postgres: counter cash, PayOS checkout (patient) and PayOS QR at the counter
 * (receptionist) over a 3-item invoice (125,000 + 50,000 + 70,000). PayOS is the fake gateway of the base class.
 */
class BillingItemPaymentPostgresTest extends HoldBillingTestSupport {
    private record Fixture(UUID invoiceId, UUID i0, UUID i1, UUID i2) { }

    private Fixture threeItems() {
        var invoice = invoiceFor(appointment("CONFIRMED", null));
        UUID i1 = UUID.randomUUID(), i2 = UUID.randomUUID();
        insertItem(invoice.id(), i1, 2, 50_000);
        insertItem(invoice.id(), i2, 3, 70_000);
        return new Fixture(invoice.id(), invoice.items().getFirst().id(), i1, i2);
    }

    private void insertItem(UUID invoiceId, UUID id, int line, long price) {
        jdbc.update("insert into invoice_items (id, invoice_id, line_number, service_id, description_snapshot, quantity, "
                + "unit_price_snapshot) values (?, ?, ?, ?, 'Extra item', 1, ?)", id, invoiceId, line, serviceId, price);
    }

    private PaymentAttemptResponse checkout(Fixture f, PaymentChannel channel, UUID... items) {
        var actor = channel == PaymentChannel.COUNTER_QR ? receptionist : patient;
        return payments.createOnlineAttempt(f.invoiceId(), new PaymentAttemptRequest(List.of(items), channel),
                actor, "item-" + UUID.randomUUID());
    }

    private void cash(Fixture f, UUID... items) {
        payments.recordCounterPayment(f.invoiceId(), new CounterPaymentRequest(List.of(items), "CASH"),
                receptionist, "cash-" + UUID.randomUUID());
    }

    private static String code(Runnable action) {
        return assertThrows(BillingException.class, action::run).code();
    }

    private List<String> itemStatuses(Fixture f) {
        return invoices.get(f.invoiceId(), receptionist).items().stream().map(item -> item.status()).toList();
    }

    private PaymentGateway.VerifiedGatewayEvent paid(long orderCode, long amount, String reference) {
        return new PaymentGateway.VerifiedGatewayEvent("PAYOS", orderCode, amount, "VND", "link-" + orderCode,
                reference, "00", true);
    }

    private UUID linkedAttempt(UUID itemId) {
        return jdbc.queryForObject("select payment_attempt_id from invoice_items where id = ?", UUID.class, itemId);
    }

    private static RequestPostProcessor as(AuthenticatedAccount principal) {
        return authentication(new UsernamePasswordAuthenticationToken(principal, null,
                java.util.List.of(new SimpleGrantedAuthority("ROLE_" + principal.accountRole().name()))));
    }

    @Test
    void httpContractOfCounterPaymentAndCheckoutByItems() throws Exception {
        Fixture f = threeItems();
        mockMvc.perform(post("/api/v1/invoices/{id}/counter-payments", f.invoiceId()).with(as(receptionist)).with(csrf())
                        .header("Idempotency-Key", "h-" + UUID.randomUUID()).contentType("application/json")
                        .content("{\"itemIds\":[\"" + f.i0() + "\"],\"method\":\"CASH\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.channel").value("COUNTER"))
                .andExpect(jsonPath("$.status").value("CONFIRMED")).andExpect(jsonPath("$.amount").value(125000.0));
        // The same item again, an unknown item and the old {amount} body are all MSG-49.
        for (String body : List.of("{\"itemIds\":[\"" + f.i0() + "\"],\"method\":\"CASH\"}",
                "{\"itemIds\":[\"" + UUID.randomUUID() + "\"],\"method\":\"CASH\"}", "{\"amount\":50000,\"method\":\"CASH\"}")) {
            mockMvc.perform(post("/api/v1/invoices/{id}/counter-payments", f.invoiceId()).with(as(receptionist)).with(csrf())
                            .header("Idempotency-Key", "h-" + UUID.randomUUID()).contentType("application/json").content(body))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("MSG-49"));
        }
        // Checkout: the patient with an items body, the receptionist only with COUNTER_QR.
        mockMvc.perform(post("/api/v1/invoices/{id}/payment-attempts", f.invoiceId()).with(as(patient)).with(csrf())
                        .header("Idempotency-Key", "h-" + UUID.randomUUID()).contentType("application/json")
                        .content("{\"itemIds\":[\"" + f.i1() + "\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.channel").value("ONLINE"))
                .andExpect(jsonPath("$.status").value("PENDING")).andExpect(jsonPath("$.amount").value(50000.0));
        mockMvc.perform(post("/api/v1/invoices/{id}/payment-attempts", f.invoiceId()).with(as(receptionist)).with(csrf())
                        .header("Idempotency-Key", "h-" + UUID.randomUUID()).contentType("application/json")
                        .content("{\"itemIds\":[\"" + f.i2() + "\"],\"channel\":\"COUNTER_QR\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.channel").value("COUNTER_QR"));
        mockMvc.perform(post("/api/v1/invoices/{id}/payment-attempts", f.invoiceId()).with(as(patient)).with(csrf())
                        .header("Idempotency-Key", "h-" + UUID.randomUUID()).contentType("application/json")
                        .content("{\"itemIds\":[\"" + f.i2() + "\"],\"channel\":\"COUNTER_QR\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
        assertEquals(List.of("PAID", "PAYMENT_PENDING", "PAYMENT_PENDING"), itemStatuses(f));
    }

    // ---- counter (cash) -------------------------------------------------------------------------------------------

    @Test
    void counterPaymentOfTwoOfThreeItemsLeavesTheThirdPayableAndTheInvoiceOpen() {
        Fixture f = threeItems();
        cash(f, f.i0(), f.i2());
        assertEquals(List.of("PAID", "PENDING", "PAID"), itemStatuses(f));
        assertEquals("OPEN", invoiceStatus(f.invoiceId()));
        cash(f, f.i1());
        assertEquals("PAID", invoiceStatus(f.invoiceId()));
        assertEquals(new BigDecimal("245000.00"), invoices.get(f.invoiceId(), receptionist).paidAmount());
    }

    @Test
    void badSelectionsAreRefusedWithMsg49AndWriteNothing() {
        Fixture f = threeItems();
        var otherInvoice = invoiceFor(appointment("CONFIRMED", null));
        for (List<UUID> ids : List.of(List.<UUID>of(), List.of(UUID.randomUUID()), List.of(f.i0(), f.i0()),
                List.of(otherInvoice.items().getFirst().id()), List.of(f.i0(), UUID.randomUUID()))) {
            assertEquals("MSG-49", code(() -> payments.recordCounterPayment(f.invoiceId(),
                    new CounterPaymentRequest(ids, "CASH"), receptionist, "bad-" + UUID.randomUUID())), ids.toString());
        }
        assertEquals("PAYMENT_METHOD_INVALID", code(() -> payments.recordCounterPayment(f.invoiceId(),
                new CounterPaymentRequest(List.of(f.i0()), "BANK_TRANSFER"), receptionist, "bad-method")));
        assertEquals(0, jdbc.queryForObject("select count(*) from payment_attempts where invoice_id = ?",
                Integer.class, f.invoiceId()));
        assertEquals(List.of("PENDING", "PENDING", "PENDING"), itemStatuses(f));
    }

    @Test
    void sameIdempotencyKeyReturnsTheFirstPaymentWithoutCollectingTwice() {
        Fixture f = threeItems();
        var request = new CounterPaymentRequest(List.of(f.i0()), "CASH");
        var first = payments.recordCounterPayment(f.invoiceId(), request, receptionist, "once");
        var again = payments.recordCounterPayment(f.invoiceId(), request, receptionist, "once");
        assertEquals(first.id(), again.id());
        assertEquals(1, jdbc.queryForObject("select count(*) from payment_attempts where invoice_id = ?",
                Integer.class, f.invoiceId()));
    }

    @Test
    void moneyTakenBeforePaymentWasPerItemBlocksTheInvoiceInsteadOfBeingCollectedTwice() {
        Fixture f = threeItems();
        jdbc.update("insert into payment_attempts (id, invoice_id, channel, counter_method, amount, currency_code, status, "
                + "created_by_account_id, idempotency_key, request_fingerprint, confirmed_at) values "
                + "(?, ?, 'COUNTER', 'CASH', 50000, 'VND', 'CONFIRMED', ?, ?, ?, now())",
                UUID.randomUUID(), f.invoiceId(), receptionistAccountId, "legacy-" + UUID.randomUUID(), "0".repeat(64));
        assertEquals("MSG-49", code(() -> cash(f, f.i0())));
    }

    @Test
    void voidItemCannotBePaidAndTheInvoiceSettlesWithoutIt() {
        Fixture f = threeItems();
        jdbc.update("update invoice_items set voided_at = now() where id = ?", f.i1());
        assertEquals("MSG-49", code(() -> cash(f, f.i1())));
        assertEquals("MSG-49", code(() -> cash(f, f.i0(), f.i1())));
        cash(f, f.i0(), f.i2());
        assertEquals(List.of("PAID", "VOID", "PAID"), itemStatuses(f));
        assertEquals("PAID", invoiceStatus(f.invoiceId()));
        assertEquals(0, new BigDecimal("195000").compareTo(invoices.get(f.invoiceId(), receptionist).totalAmount()));
    }

    @Test
    void receptionistsPayingDifferentItemsOfOneInvoiceAtTheSameTimeBothSucceed() throws Exception {
        Fixture f = threeItems();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tasks = List.of(f.i0(), f.i1()).stream().map(item -> executor.submit(() -> {
                start.await();
                cash(f, item);
                return "OK";
            })).toList();
            start.countDown();
            assertEquals("OK", tasks.get(0).get(60, TimeUnit.SECONDS));
            assertEquals("OK", tasks.get(1).get(60, TimeUnit.SECONDS));
        }
        assertEquals(List.of("PAID", "PAID", "PENDING"), itemStatuses(f));
    }

    // ---- PayOS checkout -------------------------------------------------------------------------------------------

    @Test
    void onlineCheckoutPaysExactlyTheSelectedItemsAndTheOthersStayPayableAtTheCounter() {
        Fixture f = threeItems();
        var attempt = checkout(f, PaymentChannel.ONLINE, f.i0(), f.i1());
        assertEquals(new BigDecimal("175000.00"), attempt.amount());
        assertEquals("ONLINE", attempt.channel());
        assertEquals(List.of("PAYMENT_PENDING", "PAYMENT_PENDING", "PENDING"), itemStatuses(f));
        assertEquals(attempt.id(), linkedAttempt(f.i0()));
        var link = gateway.commands.getLast();
        assertEquals(175_000L, link.items().stream().mapToLong(i -> i.quantity() * i.unitPrice()).sum());
        assertEquals(2, link.items().size());

        // The same items again return the open checkout; other items of the invoice wait (one open online checkout).
        assertEquals(attempt.id(), checkout(f, PaymentChannel.ONLINE, f.i0(), f.i1()).id());
        assertEquals("MSG-49", code(() -> checkout(f, PaymentChannel.ONLINE, f.i2())));
        // Held items cannot be taken at the counter; the free one can.
        assertEquals("MSG-49", code(() -> cash(f, f.i0())));
        cash(f, f.i2());
        assertEquals("OPEN", invoiceStatus(f.invoiceId()));

        long orderCode = orderCode(attempt.id());
        payments.handleVerifiedGatewayEvent(paid(orderCode, 175_000, "ref-" + orderCode));
        assertEquals("CONFIRMED", attemptStatus(attempt.id()));
        assertEquals(List.of("PAID", "PAID", "PAID"), itemStatuses(f));
        assertEquals("PAID", invoiceStatus(f.invoiceId()));
        assertEquals(List.of("APPLIED:"), eventOutcomes(orderCode));
        assertEquals("INVOICE_NOT_PAYABLE", code(() -> checkout(f, PaymentChannel.ONLINE, f.i0())));
    }

    @Test
    void checkoutWithoutItemsPaysEveryPayableItem() {
        Fixture f = threeItems();
        cash(f, f.i1());
        var attempt = payments.createOnlineAttempt(f.invoiceId(), patient, "all-" + UUID.randomUUID());
        assertEquals(new BigDecimal("195000.00"), attempt.amount());
        long orderCode = orderCode(attempt.id());
        payments.handleVerifiedGatewayEvent(paid(orderCode, 195_000, "ref-all"));
        assertEquals("PAID", invoiceStatus(f.invoiceId()));
    }

    @Test
    void anExpiredCheckoutKeepsItsItemsSoALateWebhookStillPaysThem() {
        Fixture f = threeItems();
        var attempt = checkout(f, PaymentChannel.ONLINE, f.i0());
        long orderCode = orderCode(attempt.id());
        gateway.setStatus(orderCode, "EXPIRED");
        assertEquals("EXPIRED", payments.reconcile(attempt.id(), receptionist).status());
        // Expired: payable again, but still pointing at the attempt so the mapping survives.
        assertEquals(List.of("PENDING", "PENDING", "PENDING"), itemStatuses(f));
        assertEquals(attempt.id(), linkedAttempt(f.i0()));

        payments.handleVerifiedGatewayEvent(paid(orderCode, 125_000, "ref-late"));
        assertEquals("CONFIRMED", attemptStatus(attempt.id()));
        assertEquals(List.of("PAID", "PENDING", "PENDING"), itemStatuses(f));
    }

    @Test
    void aNewerPaymentTakesTheItemsOfAnExpiredCheckoutAndTheLateWebhookIsFlaggedForStaff() {
        Fixture f = threeItems();
        var attempt = checkout(f, PaymentChannel.ONLINE, f.i0());
        long orderCode = orderCode(attempt.id());
        clock.advance(Duration.ofMinutes(20)); // past the 15 minute link

        assertEquals(List.of("PENDING", "PENDING", "PENDING"), itemStatuses(f));
        cash(f, f.i0());
        assertEquals("EXPIRED", attemptStatus(attempt.id()));

        payments.handleVerifiedGatewayEvent(paid(orderCode, 125_000, "ref-too-late"));
        assertEquals("RECONCILIATION_REQUIRED", attemptStatus(attempt.id()));
        assertEquals(List.of("RECONCILIATION_REQUIRED:ITEMS_CHANGED"), eventOutcomes(orderCode));
        assertEquals(List.of("PAID", "PENDING", "PENDING"), itemStatuses(f));
        // The counter payment, not the late attempt, owns the item.
        assertTrue(!attempt.id().equals(linkedAttempt(f.i0())));
    }

    @Test
    void aNewCheckoutAfterExpiryReplacesTheOldOneAndTakesTheItem() {
        Fixture f = threeItems();
        var first = checkout(f, PaymentChannel.ONLINE, f.i0());
        clock.advance(Duration.ofMinutes(20));
        var second = checkout(f, PaymentChannel.ONLINE, f.i0());
        assertTrue(!first.id().equals(second.id()));
        assertEquals("EXPIRED", attemptStatus(first.id()));
        assertEquals(second.id(), linkedAttempt(f.i0()));
        assertEquals(1, jdbc.queryForObject("select count(*) from payment_attempts where invoice_id = ? and status = 'PENDING'",
                Integer.class, f.invoiceId()));
    }

    @Test
    void paidWebhookForAnItemVoidedMeanwhileMarksItRefundPendingWithoutRefunding() {
        Fixture f = threeItems();
        var attempt = checkout(f, PaymentChannel.ONLINE, f.i0(), f.i1());
        long orderCode = orderCode(attempt.id());
        jdbc.update("update invoice_items set voided_at = now() where id = ?", f.i1());
        assertEquals(List.of("PAYMENT_PENDING", "VOID", "PENDING"), itemStatuses(f));

        payments.handleVerifiedGatewayEvent(paid(orderCode, 175_000, "ref-void"));

        assertEquals("CONFIRMED", attemptStatus(attempt.id()));
        assertEquals(List.of("PAID", "REFUND_PENDING", "PENDING"), itemStatuses(f));
        assertEquals("REFUND_PENDING", jdbc.queryForObject("select refund_status from invoice_items where id = ?", String.class, f.i1()));
        assertNull(jdbc.queryForObject("select refund_status from invoice_items where id = ?", String.class, f.i0()));
        assertNull(jdbc.queryForObject("select refunded_at from invoice_items where id = ?", java.sql.Timestamp.class, f.i1()));
        assertEquals(List.of("APPLIED:REFUND_PENDING_ITEM_VOID"), eventOutcomes(orderCode));
        assertEquals("OPEN", invoiceStatus(f.invoiceId()));
        cash(f, f.i2());
        assertEquals("PAID", invoiceStatus(f.invoiceId()));
    }

    @Test
    void failedCheckoutFreesTheItemsForAnotherPayment() throws Exception {
        Fixture f = threeItems();
        var attempt = checkout(f, PaymentChannel.ONLINE, f.i0());
        long orderCode = orderCode(attempt.id());
        gateway.setStatus(orderCode, "CANCELLED");
        payments.reconcile(attempt.id(), receptionist);
        cash(f, f.i0());
        assertEquals(List.of("PAID", "PENDING", "PENDING"), itemStatuses(f));
    }

    @Test
    void twoPatientRequestsForTheSameItemsShareOneCheckout() throws Exception {
        Fixture f = threeItems();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tasks = java.util.stream.IntStream.range(0, 2).mapToObj(i -> executor.submit(() -> {
                start.await();
                return checkout(f, PaymentChannel.ONLINE, f.i0()).id();
            })).toList();
            start.countDown();
            assertEquals(tasks.get(0).get(60, TimeUnit.SECONDS), tasks.get(1).get(60, TimeUnit.SECONDS));
        }
        assertEquals(1, jdbc.queryForObject("select count(*) from payment_attempts where invoice_id = ?",
                Integer.class, f.invoiceId()));
    }

    // ---- PayOS QR at the counter ----------------------------------------------------------------------------------

    @Test
    void counterQrIsOpenedByTheReceptionistOnlyAndRunsBesideAnOnlineCheckoutOnOtherItems() {
        Fixture f = threeItems();
        assertEquals("ACCESS_DENIED", code(() -> payments.createOnlineAttempt(f.invoiceId(),
                new PaymentAttemptRequest(List.of(f.i0()), PaymentChannel.COUNTER_QR), patient, "p-qr")));
        assertEquals("ACCESS_DENIED", code(() -> payments.createOnlineAttempt(f.invoiceId(),
                new PaymentAttemptRequest(List.of(f.i1()), PaymentChannel.ONLINE), receptionist, "r-online")));
        assertEquals("PAYMENT_CHANNEL_INVALID", code(() -> payments.createOnlineAttempt(f.invoiceId(),
                new PaymentAttemptRequest(List.of(f.i1()), PaymentChannel.COUNTER), receptionist, "r-counter")));
    }

    @Test
    void severalCounterQrCheckoutsCanBeOpenAndEachIsReturnedForItsOwnItems() {
        Fixture f = threeItems();
        var qrA = checkout(f, PaymentChannel.COUNTER_QR, f.i0());
        var qrB = checkout(f, PaymentChannel.COUNTER_QR, f.i1());
        assertTrue(!qrA.id().equals(qrB.id()));
        assertEquals(qrA.id(), checkout(f, PaymentChannel.COUNTER_QR, f.i0()).id());
        assertEquals(qrB.id(), checkout(f, PaymentChannel.COUNTER_QR, f.i1()).id());
        // An item already on an open QR cannot be put on another one.
        assertEquals("MSG-49", code(() -> checkout(f, PaymentChannel.COUNTER_QR, f.i0(), f.i2())));
        assertEquals(2, jdbc.queryForObject("select count(*) from payment_attempts where invoice_id = ?", Integer.class, f.invoiceId()));
    }

    @Test
    void counterQrPaymentSettlesItsItemsThroughWebhookAndReconcile() {
        Fixture f = threeItems();
        var qr = checkout(f, PaymentChannel.COUNTER_QR, f.i0());
        assertEquals("COUNTER_QR", qr.channel());
        var online = checkout(f, PaymentChannel.ONLINE, f.i1());
        assertEquals("MSG-49", code(() -> checkout(f, PaymentChannel.ONLINE, f.i0())));
        assertEquals(List.of("PAYMENT_PENDING", "PAYMENT_PENDING", "PENDING"), itemStatuses(f));

        long qrCode = orderCode(qr.id());
        payments.handleVerifiedGatewayEvent(paid(qrCode, 125_000, "ref-qr"));
        assertEquals("CONFIRMED", attemptStatus(qr.id()));

        long onlineCode = orderCode(online.id());
        gateway.statuses.put(onlineCode, new PaymentGateway.GatewayPaymentStatus("PAID", onlineCode, 50_000, 50_000,
                "link-" + onlineCode, "ref-online", "VND"));
        assertEquals("CONFIRMED", payments.reconcile(online.id(), receptionist).status());
        assertEquals(List.of("PAID", "PAID", "PENDING"), itemStatuses(f));
        assertEquals("OPEN", invoiceStatus(f.invoiceId()));
    }
}
