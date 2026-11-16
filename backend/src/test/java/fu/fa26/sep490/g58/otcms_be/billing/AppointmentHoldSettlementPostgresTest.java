package fu.fa26.sep490.g58.otcms_be.billing;

import fu.fa26.sep490.g58.otcms_be.billing.service.AppointmentHoldSettlementService.Outcome;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Releasing a hold: gateway first, then void under the invoice lock; money that arrived wins. */
class AppointmentHoldSettlementPostgresTest extends HoldBillingTestSupport {

    @Test
    void appointmentWithoutInvoiceIsNoInvoice() {
        UUID appointmentId = hold(Duration.ofMinutes(15));
        assertEquals(Outcome.NO_INVOICE, settlement.settle(appointmentId, false));
        assertEquals(Outcome.NO_INVOICE, settlement.settle(appointmentId, true));
    }

    @Test
    void expiredHoldWithoutALiveQrVoidsTheInvoiceAndClosesTheAttempt() {
        Held held = heldWithQr();
        clock.advance(Duration.ofMinutes(16));
        gateway.setStatus(held.orderCode(), "EXPIRED");

        assertEquals(Outcome.VOIDED, settlement.settle(held.appointmentId(), false));

        assertEquals("VOID", invoiceStatus(held.invoiceId()));
        assertEquals("EXPIRED", attemptStatus(held.attemptId()));
        assertEquals(Outcome.VOIDED, settlement.settle(held.appointmentId(), false)); // idempotent repeat
    }

    @Test
    void holdWithoutAnyAttemptIsVoidedByTheSweeper() {
        UUID appointmentId = hold(Duration.ofMinutes(15));
        UUID invoiceId = invoiceFor(appointmentId).id();
        assertEquals(Outcome.VOIDED, settlement.settle(appointmentId, false));
        assertEquals("VOID", invoiceStatus(invoiceId));
    }

    @Test
    void liveQrKeepsTheHoldForTheSweeperButNotForAUserRelease() {
        Held held = heldWithQr(); // gateway still reports PENDING, QR expiry is in the future
        assertEquals(Outcome.STILL_PENDING, settlement.settle(held.appointmentId(), false));
        assertEquals("OPEN", invoiceStatus(held.invoiceId()));
        assertEquals("PENDING", attemptStatus(held.attemptId()));

        assertEquals(Outcome.VOIDED, settlement.settle(held.appointmentId(), true));
        assertEquals("VOID", invoiceStatus(held.invoiceId()));
        assertEquals("CANCELLED", attemptStatus(held.attemptId()));
    }

    @Test
    void sweeperThatFindsTheQrPaidConfirmsInsteadOfVoiding() {
        Held held = heldWithQr();
        clock.advance(Duration.ofMinutes(16));
        gateway.setStatus(held.orderCode(), "PAID");

        assertEquals(Outcome.PAID, settlement.settle(held.appointmentId(), false));

        assertEquals("CONFIRMED", appointmentStatus(held.appointmentId()));
        assertEquals("PAID", invoiceStatus(held.invoiceId()));
        assertEquals("CONFIRMED", attemptStatus(held.attemptId()));
        assertEquals(1, appointmentPort.confirmedTransitions.get());
        // A second round (or a user release after payment) never voids a paid invoice and never confirms twice.
        assertEquals(Outcome.PAID, settlement.settle(held.appointmentId(), false));
        assertEquals(Outcome.PAID, settlement.settle(held.appointmentId(), true));
        assertEquals("PAID", invoiceStatus(held.invoiceId()));
        assertEquals(1, appointmentPort.confirmedTransitions.get());
    }

    @Test
    void userReleaseThatFindsTheQrPaidConfirmsToo() {
        Held held = heldWithQr();
        gateway.setStatus(held.orderCode(), "PAID");
        assertEquals(Outcome.PAID, settlement.settle(held.appointmentId(), true));
        assertEquals("CONFIRMED", appointmentStatus(held.appointmentId()));
    }

    @Test
    void unreachableGatewayDefersTheSweeperButDoesNotBlockAUserRelease() {
        Held held = heldWithQr();
        clock.advance(Duration.ofMinutes(16));
        gateway.down = true;

        assertEquals(Outcome.STILL_PENDING, settlement.settle(held.appointmentId(), false));
        assertEquals("OPEN", invoiceStatus(held.invoiceId()));

        assertEquals(Outcome.VOIDED, settlement.settle(held.appointmentId(), true));
        assertEquals("VOID", invoiceStatus(held.invoiceId()));
    }

    @Test
    void invoiceWithConfirmedMoneyIsNeverVoided() {
        UUID appointmentId = hold(Duration.ofMinutes(15));
        UUID invoiceId = invoiceFor(appointmentId).id();
        payments.recordCounterPayment(invoiceId,
                new fu.fa26.sep490.g58.otcms_be.billing.model.dto.request.CounterPaymentRequest(50_000L, "CASH"),
                receptionist, "partial-" + UUID.randomUUID());
        assertEquals(Outcome.PAID, settlement.settle(appointmentId, true));
        assertEquals("OPEN", invoiceStatus(invoiceId));
    }

    // ---- concurrency: one outcome, no deadlock -------------------------------------------------------------

    /** Webhook (PAID) against a user release on the same hold: either the payment wins or the void wins. */
    @RepeatedTest(8)
    void webhookRacingAUserReleaseEndsInExactlyOneOutcome() throws Exception {
        Held held = heldWithQr();
        Outcome[] released = new Outcome[1];
        race(() -> { payments.handleVerifiedGatewayEvent(paidEvent(held.orderCode())); return null; },
                () -> { released[0] = settlement.settle(held.appointmentId(), true); return null; });

        if ("PAID".equals(invoiceStatus(held.invoiceId()))) {
            assertEquals(Outcome.PAID, released[0]);
            assertEquals("CONFIRMED", appointmentStatus(held.appointmentId()));
            assertEquals("CONFIRMED", attemptStatus(held.attemptId()));
            assertEquals(1, appointmentPort.confirmedTransitions.get());
        } else {
            assertEquals("VOID", invoiceStatus(held.invoiceId()));
            assertEquals(Outcome.VOIDED, released[0]);
            assertEquals("RECONCILIATION_REQUIRED", attemptStatus(held.attemptId()));
            assertEquals("AWAITING_PAYMENT", appointmentStatus(held.appointmentId()));
            assertEquals(0, appointmentPort.confirmedTransitions.get());
            assertNull(jdbc.queryForObject("select confirmed_at from payment_attempts where id = ?",
                    java.sql.Timestamp.class, held.attemptId()));
        }
    }

    /** Webhook and the sweeper both see the QR paid: the loser must observe the winner's commit, not overwrite it. */
    @RepeatedTest(8)
    void webhookRacingASweeperThatAlsoSeesThePaymentConfirmsOnce() throws Exception {
        Held held = heldWithQr();
        clock.advance(Duration.ofMinutes(16));
        gateway.setStatus(held.orderCode(), "PAID");
        Outcome[] swept = new Outcome[1];
        race(() -> { payments.handleVerifiedGatewayEvent(paidEvent(held.orderCode())); return null; },
                () -> { swept[0] = settlement.settle(held.appointmentId(), false); return null; });

        assertEquals(Outcome.PAID, swept[0]);
        assertEquals("CONFIRMED", appointmentStatus(held.appointmentId()));
        assertEquals("PAID", invoiceStatus(held.invoiceId()));
        assertEquals("CONFIRMED", attemptStatus(held.attemptId()));
        assertEquals(1, appointmentPort.confirmedTransitions.get());
    }

    /** Two identical deliveries at once: the invoice lock serialises them, the loser sees the winner's fingerprint. */
    @RepeatedTest(8)
    void identicalWebhookDeliveriesRacingSettleOnce() throws Exception {
        Held held = heldWithQr();
        race(() -> { payments.handleVerifiedGatewayEvent(paidEvent(held.orderCode())); return null; },
                () -> { payments.handleVerifiedGatewayEvent(paidEvent(held.orderCode())); return null; });

        assertEquals("CONFIRMED", appointmentStatus(held.appointmentId()));
        assertEquals("PAID", invoiceStatus(held.invoiceId()));
        assertEquals("CONFIRMED", attemptStatus(held.attemptId()));
        assertEquals(1, appointmentPort.confirmedTransitions.get());
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_logs where source = 'payment_gateway_events' "
                + "and action = 'APPLIED' and entity_id = ?", Integer.class, held.attemptId()));
        assertEquals(java.util.List.of("APPLIED:"), eventOutcomes(held.orderCode())); // the replay wrote nothing
    }

    private static void race(Callable<Void> first, Callable<Void> second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier start = new CyclicBarrier(2);
            Future<Void> a = pool.submit(() -> { start.await(); return first.call(); });
            Future<Void> b = pool.submit(() -> { start.await(); return second.call(); });
            a.get(30, TimeUnit.SECONDS); // a timeout here means a deadlock or a lost wake-up
            b.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }
}
