package fu.fa26.sep490.g58.otcms_be.billing.service.impl;

import fu.fa26.sep490.g58.otcms_be.billing.integration.AppointmentPaymentPort;
import fu.fa26.sep490.g58.otcms_be.billing.integration.ClinicalRoundBillingPort;
import fu.fa26.sep490.g58.otcms_be.billing.integration.VisitFeeSettlementPort;
import fu.fa26.sep490.g58.otcms_be.billing.model.InvoiceStatus;
import fu.fa26.sep490.g58.otcms_be.billing.model.PaymentAttemptStatus;
import fu.fa26.sep490.g58.otcms_be.billing.model.PaymentChannel;
import fu.fa26.sep490.g58.otcms_be.billing.model.entity.InvoiceJpaEntity;
import fu.fa26.sep490.g58.otcms_be.billing.model.entity.PaymentAttemptJpaEntity;
import fu.fa26.sep490.g58.otcms_be.billing.repository.InvoiceItemRepository;
import fu.fa26.sep490.g58.otcms_be.billing.repository.InvoiceRepository;
import fu.fa26.sep490.g58.otcms_be.billing.repository.PaymentAttemptRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Invoice settlement shared by counter payments, webhooks and reconcile: recomputes the invoice status after a
 * confirmed attempt and runs the source hooks (clinical round paid, appointment hold confirmed, walk-in fee paid).
 * Always runs inside the caller's transaction, with the invoice row already locked (lock order: invoice first,
 * appointment second).
 */
@Component
public class InvoiceSettlementSupport {
    private static final Logger log = LoggerFactory.getLogger(InvoiceSettlementSupport.class);
    private static final String PAID_AFTER_HOLD_RELEASED = "Paid after appointment hold released";

    private final InvoiceRepository invoices;
    private final InvoiceItemRepository items;
    private final PaymentAttemptRepository attempts;
    private final ClinicalRoundBillingPort clinicalRounds;
    private final AppointmentPaymentPort appointments;
    private final VisitFeeSettlementPort visitFees;
    private final EntityManager entityManager;

    public InvoiceSettlementSupport(InvoiceRepository invoices, InvoiceItemRepository items,
            PaymentAttemptRepository attempts, ClinicalRoundBillingPort clinicalRounds,
            AppointmentPaymentPort appointments, VisitFeeSettlementPort visitFees, EntityManager entityManager) {
        this.invoices = invoices;
        this.items = items;
        this.attempts = attempts;
        this.clinicalRounds = clinicalRounds;
        this.appointments = appointments;
        this.visitFees = visitFees;
        this.entityManager = entityManager;
    }

    /**
     * Locks the invoice row, then re-reads it and any attempt loaded before the lock. Without the refresh a
     * transaction that waited for a concurrent webhook/settle would keep acting on the stale snapshot cached in the
     * persistence context (open-in-view shares it for the whole HTTP request) and could overwrite the winner.
     */
    Optional<InvoiceJpaEntity> lockInvoiceFresh(UUID invoiceId, PaymentAttemptJpaEntity... staleAttempts) {
        // Lock with refresh(PESSIMISTIC_WRITE), not a locking query: when the invoice is already managed with an
        // older @Version (another transaction committed meanwhile), Hibernate's locking query checks that version
        // and throws an optimistic-lock failure before we can re-read. refresh(..., lock) issues SELECT ... FOR UPDATE
        // and loads the committed state, so the caller always decides on the fresh row it holds the lock on.
        InvoiceJpaEntity invoice = entityManager.find(InvoiceJpaEntity.class, invoiceId);
        if (invoice == null) return Optional.empty();
        entityManager.refresh(invoice, LockModeType.PESSIMISTIC_WRITE);
        for (PaymentAttemptJpaEntity attempt : staleAttempts) refresh(attempt);
        return Optional.of(invoice);
    }

    private void refresh(Object entity) {
        if (entityManager.contains(entity)) entityManager.refresh(entity);
    }

    BigDecimal outstanding(UUID invoiceId) {
        BigDecimal total = items.findAllByInvoiceIdOrderByLineNumber(invoiceId).stream()
                .map(item -> item.getUnitPriceSnapshot().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.subtract(confirmed(invoiceId)).max(BigDecimal.ZERO);
    }

    BigDecimal confirmed(UUID invoiceId) {
        BigDecimal amount = attempts.sumByInvoiceAndStatus(invoiceId, PaymentAttemptStatus.CONFIRMED);
        return amount == null ? BigDecimal.ZERO : amount;
    }

    /**
     * Call after {@code payment} was confirmed. Returns false when an online payment arrived for an appointment whose
     * hold was already released: the attempt is then flagged RECONCILIATION_REQUIRED and the invoice stays untouched.
     * Never throws for that case (the webhook must still answer 2xx).
     */
    boolean settleInvoice(InvoiceJpaEntity invoice, PaymentAttemptJpaEntity payment) {
        boolean paid = outstanding(invoice.getId()).signum() <= 0;
        if (paid && "APPOINTMENT".equals(invoice.getSourceType())) {
            var result = appointments.confirmPaid(invoice.getAppointmentId());
            if (result == AppointmentPaymentPort.ConfirmResult.NOT_AWAITING
                    && payment.getChannel() == PaymentChannel.ONLINE) {
                holdForReconciliation(payment, PAID_AFTER_HOLD_RELEASED, payment.getProviderTransactionReference());
                return false;
            }
        }
        invoice.setStatus(paid ? InvoiceStatus.PAID : InvoiceStatus.OPEN);
        invoices.save(invoice);
        if (invoice.getStatus() == InvoiceStatus.PAID && "CLINICAL_ROUND".equals(invoice.getSourceType())) {
            clinicalRounds.markRoundPaid(invoice.getClinicalOrderingRoundId());
        }
        if (invoice.getStatus() == InvoiceStatus.PAID && "VISIT".equals(invoice.getSourceType())) {
            visitFees.onVisitInvoicePaid(invoice.getVisitId());
        }
        return true;
    }

    /**
     * Flags money that cannot be applied (invoice VOID / hold released). The partial unique index
     * {@code uk_payment_attempt_active_online} allows one PENDING/RECONCILIATION_REQUIRED online attempt per invoice,
     * so when another attempt already holds that slot this attempt only keeps the reason on a closed status.
     *
     * @return true when the attempt ended as RECONCILIATION_REQUIRED
     */
    boolean holdForReconciliation(PaymentAttemptJpaEntity payment, String reason, String transactionReference) {
        boolean slotTaken = payment.getStatus() != PaymentAttemptStatus.RECONCILIATION_REQUIRED
                && attempts.findAllByInvoiceIdOrderByCreatedAtDesc(payment.getInvoiceId()).stream()
                        .anyMatch(other -> !other.getId().equals(payment.getId())
                                && other.getChannel() == PaymentChannel.ONLINE
                                && (other.getStatus() == PaymentAttemptStatus.PENDING
                                        || other.getStatus() == PaymentAttemptStatus.RECONCILIATION_REQUIRED));
        if (slotTaken) {
            boolean closed = payment.getStatus() == PaymentAttemptStatus.CANCELLED
                    || payment.getStatus() == PaymentAttemptStatus.EXPIRED
                    || payment.getStatus() == PaymentAttemptStatus.FAILED;
            payment.fail(closed ? payment.getStatus() : PaymentAttemptStatus.CANCELLED,
                    reason + " (another attempt of this invoice is already awaiting reconciliation)");
            attempts.save(payment);
            logHeld("closed without reconciliation slot", payment, reason);
            return false;
        }
        payment.requireReconciliation(reason, transactionReference);
        attempts.save(payment);
        logHeld("marked RECONCILIATION_REQUIRED", payment, reason);
        return true;
    }

    /** Ops trail for late payments (ids, amount and reason only: no personal data, no checkout URL / QR). */
    private static void logHeld(String action, PaymentAttemptJpaEntity payment, String reason) {
        log.warn("Late/unapplied online payment {}: orderCode={} invoiceId={} attemptId={} amount={} reason={}",
                action, payment.getProviderOrderCode(), payment.getInvoiceId(), payment.getId(),
                payment.getAmount(), reason);
    }
}
