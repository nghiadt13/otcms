package fu.fa26.sep490.g58.otcms_be.billing.service.impl;

import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentQueryException;
import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentBillingSourceService;
import fu.fa26.sep490.g58.otcms_be.billing.event.ServiceOrderPaidEvent;
import fu.fa26.sep490.g58.otcms_be.billing.exception.BillingException;
import fu.fa26.sep490.g58.otcms_be.billing.integration.ClinicalRoundBillingPort;
import fu.fa26.sep490.g58.otcms_be.billing.integration.VisitFeeSettlementPort;
import fu.fa26.sep490.g58.otcms_be.billing.model.InvoiceStatus;
import fu.fa26.sep490.g58.otcms_be.billing.model.PaymentAttemptStatus;
import fu.fa26.sep490.g58.otcms_be.billing.model.PaymentChannel;
import fu.fa26.sep490.g58.otcms_be.billing.model.entity.InvoiceItemJpaEntity;
import fu.fa26.sep490.g58.otcms_be.billing.model.entity.InvoiceJpaEntity;
import fu.fa26.sep490.g58.otcms_be.billing.model.entity.PaymentAttemptJpaEntity;
import fu.fa26.sep490.g58.otcms_be.billing.repository.InvoiceItemRepository;
import fu.fa26.sep490.g58.otcms_be.billing.repository.InvoiceRepository;
import fu.fa26.sep490.g58.otcms_be.billing.repository.PaymentAttemptRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Invoice settlement shared by counter payments, webhooks and reconcile. Payment is per ITEM: an item is paid when
 * its {@code payment_attempt_id} points at a CONFIRMED attempt, and the invoice is PAID when every item that is not
 * void is paid. Always runs inside the caller's transaction, with the invoice row already locked (lock order:
 * invoice, then its items in line order, then attempts; appointment last).
 */
@Component
public class InvoiceSettlementSupport {
    private static final Logger log = LoggerFactory.getLogger(InvoiceSettlementSupport.class);
    static final String DETAIL_ITEMS_CHANGED = "ITEMS_CHANGED";
    static final String DETAIL_REFUND_ITEM_VOID = "REFUND_PENDING_ITEM_VOID";
    static final String DETAIL_REFUND_APPOINTMENT = "REFUND_PENDING_APPOINTMENT_CANCELLED";
    /** Appointment states in which a paid fee still stands; any other (CANCELLED, legacy PAYMENT_EXPIRED...) owes the money back. */
    private static final Set<String> ACTIVE_APPOINTMENT_STATUSES =
            Set.of("CONFIRMED", "CHECKED_IN", "IN_PROGRESS", "COMPLETED", "NO_SHOW");

    private final InvoiceRepository invoices;
    private final InvoiceItemRepository items;
    private final PaymentAttemptRepository attempts;
    private final ClinicalRoundBillingPort clinicalRounds;
    private final AppointmentBillingSourceService appointments;
    private final VisitFeeSettlementPort visitFees;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher events;

    public InvoiceSettlementSupport(InvoiceRepository invoices, InvoiceItemRepository items,
            PaymentAttemptRepository attempts, ClinicalRoundBillingPort clinicalRounds,
            AppointmentBillingSourceService appointments, VisitFeeSettlementPort visitFees, EntityManager entityManager,
            ApplicationEventPublisher events) {
        this.invoices = invoices;
        this.items = items;
        this.attempts = attempts;
        this.clinicalRounds = clinicalRounds;
        this.appointments = appointments;
        this.visitFees = visitFees;
        this.entityManager = entityManager;
        this.events = events;
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

    BigDecimal confirmed(UUID invoiceId) {
        BigDecimal amount = attempts.sumByInvoiceAndStatus(invoiceId, PaymentAttemptStatus.CONFIRMED);
        return amount == null ? BigDecimal.ZERO : amount;
    }

    /**
     * Locks all items of the invoice and loads the attempts they point to. Call it right after
     * {@link #lockInvoiceFresh} and before modifying any attempt or item of this transaction.
     */
    InvoiceItemSnapshot lockItems(UUID invoiceId, Instant now) {
        return InvoiceItemSnapshot.lock(items, attempts, entityManager, invoiceId, now);
    }

    /**
     * Refuses (MSG-49) an invoice whose confirmed money cannot be matched to items: that is money taken before
     * payment was per item (a partial amount), which no item can claim. Staff settle those by hand.
     */
    void requireConsistentAllocation(UUID invoiceId, InvoiceItemSnapshot snapshot) {
        if (confirmed(invoiceId).compareTo(snapshot.paidItemsTotal()) != 0) throw new BillingException("MSG-49");
    }

    /** What {@link #settleByItems} concluded about the money. */
    enum Settled {
        /** Some items are still unpaid: the invoice stays OPEN. */
        PARTIAL,
        /** Every item is paid and the invoice is PAID. */
        PAID,
        /** The invoice is PAID but its appointment was cancelled: the money is owed back (refund by hand). */
        APPOINTMENT_NOT_ACTIVE
    }

    /**
     * Recomputes the invoice after items were paid in this transaction and runs the source hooks (clinical round
     * paid, appointment confirmed, walk-in fee paid) once, when the invoice becomes PAID.
     */
    Settled settleByItems(InvoiceJpaEntity invoice, InvoiceItemSnapshot snapshot, PaymentAttemptJpaEntity payment) {
        if (!snapshot.allPaid()) return Settled.PARTIAL;
        Settled result = Settled.PAID;
        // A counter payment is taken in front of the receptionist, who sees the appointment; an online one may arrive late.
        if ("APPOINTMENT".equals(invoice.getSourceType()) && payment != null
                && payment.getChannel() != PaymentChannel.COUNTER && !appointmentStillOn(invoice.getAppointmentId())) {
            result = Settled.APPOINTMENT_NOT_ACTIVE;
        }
        invoice.setStatus(InvoiceStatus.PAID);
        invoices.save(invoice);
        if ("CLINICAL_ROUND".equals(invoice.getSourceType())) clinicalRounds.markRoundPaid(invoice.getClinicalOrderingRoundId());
        if ("VISIT".equals(invoice.getSourceType())) visitFees.onVisitInvoicePaid(invoice.getVisitId());
        return result;
    }

    private boolean appointmentStillOn(UUID appointmentId) {
        if (appointmentId == null) return false;
        try {
            return ACTIVE_APPOINTMENT_STATUSES.contains(appointments.requireSource(appointmentId).status());
        } catch (AppointmentQueryException missing) {
            return false;
        }
    }

    /** Tells the clinical side that these items (those of service orders) are now paid. */
    void publishServiceOrdersPaid(UUID invoiceId, List<InvoiceItemJpaEntity> paidItems) {
        for (InvoiceItemJpaEntity item : paidItems) {
            if (item.getServiceOrderId() != null && item.getVoidedAt() == null) {
                events.publishEvent(new ServiceOrderPaidEvent(item.getServiceOrderId(), item.getId(), invoiceId));
            }
        }
    }

    /** Result of {@link #applyProviderPayment}; {@code outcome}/{@code detail} go to the webhook audit row. */
    record ProviderPayment(String outcome, String detail) { }

    /**
     * The provider confirmed the money of {@code attempt}. The attempt pays exactly the items that still point at it:
     * <ul>
     * <li>none, or their total differs from the attempt amount (the items were taken by another payment): the money
     *     cannot be mapped, so the attempt is flagged RECONCILIATION_REQUIRED for staff;</li>
     * <li>otherwise the attempt is CONFIRMED and those items are paid. Items that were voided meanwhile, and all
     *     items when the appointment is no longer active, get {@code refund_status = REFUND_PENDING} (no automatic
     *     refund; an admin confirms the manual refund).</li>
     * </ul>
     * The caller holds the invoice lock and has not modified {@code attempt} yet.
     */
    ProviderPayment applyProviderPayment(InvoiceJpaEntity invoice, PaymentAttemptJpaEntity attempt,
            String transactionReference, Instant now) {
        InvoiceItemSnapshot snapshot = lockItems(invoice.getId(), now);
        List<InvoiceItemJpaEntity> linked = snapshot.linkedTo(attempt.getId());
        if (linked.isEmpty() || InvoiceItemSnapshot.total(linked).compareTo(attempt.getAmount()) != 0) {
            holdForReconciliation(attempt, "Paid attempt no longer matches its invoice items", transactionReference);
            return new ProviderPayment("RECONCILIATION_REQUIRED", DETAIL_ITEMS_CHANGED);
        }
        attempt.confirm(transactionReference, now);
        attempts.save(attempt);
        String detail = null;
        for (InvoiceItemJpaEntity item : linked) {
            if (item.getVoidedAt() != null) {
                item.requestRefund("Paid after the item was voided");
                detail = DETAIL_REFUND_ITEM_VOID;
            }
        }
        Settled settled = settleByItems(invoice, snapshot, attempt);
        if (settled == Settled.APPOINTMENT_NOT_ACTIVE) {
            linked.forEach(item -> item.requestRefund("Paid for an appointment that was cancelled"));
            detail = DETAIL_REFUND_APPOINTMENT;
        } else {
            publishServiceOrdersPaid(invoice.getId(), linked);
        }
        return new ProviderPayment("APPLIED", detail);
    }

    /**
     * Flags money that cannot be applied (invoice VOID / items no longer match). The partial unique index
     * {@code uk_payment_attempt_active_online} allows one PENDING/RECONCILIATION_REQUIRED ONLINE attempt per invoice,
     * so when another attempt already holds that slot this attempt only keeps the reason on a closed status.
     *
     * @return true when the attempt ended as RECONCILIATION_REQUIRED
     */
    boolean holdForReconciliation(PaymentAttemptJpaEntity payment, String reason, String transactionReference) {
        boolean slotTaken = payment.getChannel() == PaymentChannel.ONLINE
                && payment.getStatus() != PaymentAttemptStatus.RECONCILIATION_REQUIRED
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
