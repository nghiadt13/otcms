package fu.fa26.sep490.g58.otcms_be.billing.service.impl;

import fu.fa26.sep490.g58.otcms_be.billing.exception.BillingException;
import fu.fa26.sep490.g58.otcms_be.billing.model.InvoiceItemStatus;
import fu.fa26.sep490.g58.otcms_be.billing.model.entity.InvoiceItemJpaEntity;
import fu.fa26.sep490.g58.otcms_be.billing.model.entity.InvoiceJpaEntity;
import fu.fa26.sep490.g58.otcms_be.billing.repository.BillingReadRepository;
import fu.fa26.sep490.g58.otcms_be.billing.repository.InvoiceItemRepository;
import fu.fa26.sep490.g58.otcms_be.billing.repository.InvoiceRepository;
import fu.fa26.sep490.g58.otcms_be.billing.service.BillingReadService.ResultPage;
import fu.fa26.sep490.g58.otcms_be.billing.service.BillingRefundService;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditEntry;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditLogWriter;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.notification.model.NotificationMessages;
import fu.fa26.sep490.g58.otcms_be.notification.service.NotificationPublisher;
import fu.fa26.sep490.g58.otcms_be.patient.service.PatientClinicalIdentityQueryService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Manual refund workflow on invoice items. Same lock order as payments (invoice, its items, attempts), so a refund
 * request cannot interleave with a payment of the same invoice.
 */
@Service
public class BillingRefundServiceImpl implements BillingRefundService {
    private static final int MAX_TEXT = 500;
    private final InvoiceItemRepository items;
    private final InvoiceRepository invoices;
    private final InvoiceSettlementSupport settlement;
    private final BillingReadRepository reads;
    private final PatientClinicalIdentityQueryService identities;
    private final AuditLogWriter audit;
    private final NotificationPublisher notifications;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public BillingRefundServiceImpl(InvoiceItemRepository items, InvoiceRepository invoices, InvoiceSettlementSupport settlement,
            BillingReadRepository reads, PatientClinicalIdentityQueryService identities, AuditLogWriter audit,
            NotificationPublisher notifications, PlatformTransactionManager transactionManager, Clock clock) {
        this.items = items;
        this.invoices = invoices;
        this.settlement = settlement;
        this.reads = reads;
        this.identities = identities;
        this.audit = audit;
        this.notifications = notifications;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Override
    public void requestRefund(UUID itemId, String reason) {
        requestRefund(itemId, reason, null);
    }

    @Override
    public void requestRefund(UUID itemId, String reason, Instant clinicCancelledAppointmentStart) {
        String cleanReason = clean(reason);
        transactions.executeWithoutResult(status -> {
            Locked locked = lockItem(itemId);
            Instant now = Instant.now(clock);
            switch (locked.snapshot().status(locked.item())) {
                case PAID -> {
                    locked.item().requestRefund(cleanReason);
                    if (clinicCancelledAppointmentStart != null) {
                        notifications.publishToPatient(locked.invoice().getPatientProfileId(),
                                NotificationMessages.refundAwaitingClinicCancellation(locked.invoice().getId(),
                                        clinicCancelledAppointmentStart));
                    }
                }
                case PENDING, PAYMENT_PENDING -> {
                    // Never delivered and never paid: void it. The invoice may now be fully paid without it.
                    locked.item().markVoided(now);
                    settlement.settleByItems(locked.invoice(), locked.snapshot(), null);
                }
                case VOID, REFUND_PENDING, REFUNDED -> { }
            }
        });
    }

    @Override
    public void voidUnpaidServiceOrder(UUID serviceOrderId) {
        UUID itemId = serviceOrderId == null ? null
                : items.findFirstByServiceOrderId(serviceOrderId).map(InvoiceItemJpaEntity::getId).orElse(null);
        if (itemId == null) return;
        transactions.executeWithoutResult(status -> {
            Locked locked = lockItem(itemId);
            switch (locked.snapshot().status(locked.item())) {
                case PENDING, PAYMENT_PENDING -> {
                    locked.item().markVoided(Instant.now(clock));
                    settlement.settleByItems(locked.invoice(), locked.snapshot(), null);
                }
                // Paid items stay paid (BR-53: no refund when the order is cancelled for an Incomplete visit).
                case PAID, VOID, REFUND_PENDING, REFUNDED -> { }
            }
        });
    }

    @Override
    public void voidUnpaidAppointmentFee(UUID appointmentId) {
        UUID invoiceId = invoiceOfAppointment(appointmentId);
        if (invoiceId == null) return;
        transactions.executeWithoutResult(status -> {
            InvoiceJpaEntity invoice = settlement.lockInvoiceFresh(invoiceId).orElse(null);
            if (invoice == null) return;
            Instant now = Instant.now(clock);
            InvoiceItemSnapshot snapshot = settlement.lockItems(invoiceId, now);
            boolean voided = false;
            for (InvoiceItemJpaEntity item : snapshot.items()) {
                InvoiceItemStatus itemStatus = snapshot.status(item);
                // Paid (or refund-related) items stay as they are: the clinic never refunds on its own (D7).
                if (itemStatus == InvoiceItemStatus.PENDING || itemStatus == InvoiceItemStatus.PAYMENT_PENDING) {
                    item.markVoided(now);
                    voided = true;
                }
            }
            if (voided) settlement.settleByItems(invoice, snapshot, null);
        });
    }

    @Override
    public void refundAppointmentFee(UUID appointmentId, String reason, Instant appointmentStart) {
        UUID invoiceId = invoiceOfAppointment(appointmentId);
        if (invoiceId == null) return;
        transactions.executeWithoutResult(status -> {
            for (InvoiceItemJpaEntity item : items.findAllByInvoiceIdOrderByLineNumber(invoiceId)) {
                requestRefund(item.getId(), reason, appointmentStart);
            }
        });
    }

    private UUID invoiceOfAppointment(UUID appointmentId) {
        return appointmentId == null ? null
                : invoices.findFirstByAppointmentId(appointmentId).map(InvoiceJpaEntity::getId).orElse(null);
    }

    @Override
    public Refund confirmRefund(UUID itemId, String note, AuthenticatedAccount actor) {
        requireAdmin(actor);
        String cleanNote = clean(note);
        return transactions.execute(status -> {
            Locked locked = lockItem(itemId);
            InvoiceItemJpaEntity item = locked.item();
            if (!InvoiceItemStatus.REFUND_PENDING_VALUE.equals(item.getRefundStatus())) throw new BillingException("MSG-42");
            item.confirmRefund(Instant.now(clock), actor.accountId());
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("invoiceId", locked.invoice().getId().toString());
            metadata.put("amount", amountOf(item.getUnitPriceSnapshot(), item.getQuantity()));
            metadata.put("requestReason", item.getRefundReason());
            metadata.put("note", cleanNote);
            audit.record(AuditEntry.builder("invoice_items", "BILLING", "REFUND_CONFIRMED", "INVOICE_ITEM")
                    .entityId(item.getId()).recordLabel(locked.invoice().getInvoiceNumber())
                    .actorAccountId(actor.accountId()).actorType("ACCOUNT")
                    .beforeState(Map.of("refundStatus", InvoiceItemStatus.REFUND_PENDING_VALUE))
                    .afterState(Map.of("refundStatus", InvoiceItemStatus.REFUNDED_VALUE))
                    .metadata(metadata).build());
            return toRefund(item, locked.invoice(), nameOf(locked.invoice().getPatientProfileId()));
        });
    }

    @Override
    public ResultPage<Refund> listRefunds(AuthenticatedAccount actor, String status, int page, int size) {
        requireAdmin(actor);
        String itemStatus = switch (status == null || status.isBlank() ? "PENDING" : status) {
            case "PENDING" -> InvoiceItemStatus.REFUND_PENDING_VALUE;
            case "REFUNDED" -> InvoiceItemStatus.REFUNDED_VALUE;
            default -> throw new BillingException("MSG-54");
        };
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) throw new BillingException("MSG-39");
        var result = reads.refunds(itemStatus, PageRequest.of(page, size));
        Map<UUID, PatientClinicalIdentityQueryService.ClinicalIdentity> names = identities.findClinicalIdentities(
                new HashSet<>(result.stream().map(BillingReadRepository.RefundRow::getPatientId).toList()));
        return new ResultPage<>(result.stream().map(row -> new Refund(row.getItemId(), row.getInvoiceId(),
                row.getInvoiceNumber(), row.getPatientId(),
                names.containsKey(row.getPatientId()) ? names.get(row.getPatientId()).fullName() : null,
                row.getDescription(), amountOf(row.getUnitPrice(), row.getQuantity()), row.getRefundStatus(),
                row.getRefundReason(), row.getRefundedAt(), row.getRefundedByAccountId())).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    /** Invoice lock first, then every item of it, then the wanted item taken from the locked snapshot. */
    private Locked lockItem(UUID itemId) {
        if (itemId == null) throw new BillingException("MSG-41");
        // Unlocked read only to learn the invoice; the item itself is read again from the locked snapshot.
        UUID invoiceId = items.findFirstById(itemId).orElseThrow(() -> new BillingException("MSG-41")).getInvoiceId();
        InvoiceJpaEntity invoice = settlement.lockInvoiceFresh(invoiceId).orElseThrow(() -> new BillingException("MSG-41"));
        InvoiceItemSnapshot snapshot = settlement.lockItems(invoiceId, Instant.now(clock));
        InvoiceItemJpaEntity item = snapshot.items().stream().filter(candidate -> itemId.equals(candidate.getId()))
                .findFirst().orElseThrow(() -> new BillingException("MSG-41"));
        return new Locked(invoice, snapshot, item);
    }

    private String nameOf(UUID patientId) {
        var identity = identities.findClinicalIdentities(java.util.List.of(patientId)).get(patientId);
        return identity == null ? null : identity.fullName();
    }

    private static Refund toRefund(InvoiceItemJpaEntity item, InvoiceJpaEntity invoice, String patientName) {
        return new Refund(item.getId(), invoice.getId(), invoice.getInvoiceNumber(), invoice.getPatientProfileId(),
                patientName, item.getDescriptionSnapshot(), amountOf(item.getUnitPriceSnapshot(), item.getQuantity()),
                item.getRefundStatus(), item.getRefundReason(), item.getRefundedAt(), item.getRefundedByAccountId());
    }

    private static BigDecimal amountOf(BigDecimal unitPrice, int quantity) {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    private static void requireAdmin(AuthenticatedAccount actor) {
        if (actor == null || (actor.accountRole() != AccountRole.ADMIN && actor.accountRole() != AccountRole.OWNER)) {
            throw new BillingException("ACCESS_DENIED");
        }
    }

    private static String clean(String text) {
        if (text == null || text.isBlank()) return null;
        String trimmed = text.trim();
        return trimmed.length() <= MAX_TEXT ? trimmed : trimmed.substring(0, MAX_TEXT);
    }

    private record Locked(InvoiceJpaEntity invoice, InvoiceItemSnapshot snapshot, InvoiceItemJpaEntity item) { }
}
