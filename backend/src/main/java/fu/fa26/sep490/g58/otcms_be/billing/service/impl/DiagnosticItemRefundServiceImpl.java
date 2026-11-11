package fu.fa26.sep490.g58.otcms_be.billing.service.impl;

import fu.fa26.sep490.g58.otcms_be.billing.exception.BillingException;
import fu.fa26.sep490.g58.otcms_be.billing.model.InvoiceStatus;
import fu.fa26.sep490.g58.otcms_be.billing.model.entity.DiagnosticItemRefundRequestJpaEntity;
import fu.fa26.sep490.g58.otcms_be.billing.model.entity.InvoiceJpaEntity;
import fu.fa26.sep490.g58.otcms_be.billing.repository.DiagnosticItemRefundRequestRepository;
import fu.fa26.sep490.g58.otcms_be.billing.repository.InvoiceItemRepository;
import fu.fa26.sep490.g58.otcms_be.billing.repository.InvoiceRepository;
import fu.fa26.sep490.g58.otcms_be.billing.service.DiagnosticItemRefundService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalDiagnosticOrderBillingService;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.visit.service.RoomIncidentService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiagnosticItemRefundServiceImpl implements DiagnosticItemRefundService {
    private static final List<String> ACTIVE_OR_COMPLETE = List.of("PENDING_ADMIN_REVIEW", "APPROVED", "REFUND_PENDING", "REFUNDED");
    private final DiagnosticItemRefundRequestRepository refunds;
    private final InvoiceRepository invoices;
    private final InvoiceItemRepository items;
    private final fu.fa26.sep490.g58.otcms_be.billing.repository.PaymentAttemptRepository payments;
    private final RoomIncidentService incidents;
    private final ClinicalDiagnosticOrderBillingService clinicalOrders;
    private final Clock clock;

    public DiagnosticItemRefundServiceImpl(DiagnosticItemRefundRequestRepository refunds, InvoiceRepository invoices,
            InvoiceItemRepository items,
            fu.fa26.sep490.g58.otcms_be.billing.repository.PaymentAttemptRepository payments,
            RoomIncidentService incidents, ClinicalDiagnosticOrderBillingService clinicalOrders, Clock clock) {
        this.refunds = refunds; this.invoices = invoices; this.items = items; this.payments = payments;
        this.incidents = incidents; this.clinicalOrders = clinicalOrders; this.clock = clock;
    }

    @Override
    @Transactional
    public RefundView request(UUID invoiceId, int lineNumber, UUID incidentId, String reason, String key,
            AuthenticatedAccount actor) {
        requireRole(actor, AccountRole.RECEPTIONIST);
        String normalized = requireText(reason, 1000);
        if (invoiceId == null || incidentId == null || lineNumber < 1 || key == null || key.isBlank() || key.length() > 128)
            throw new BillingException("REFUND_REQUEST_INVALID");
        String fingerprint = digest(invoiceId + "|" + lineNumber + "|" + incidentId + "|" + normalized);
        var prior = refunds.findFirstByRequestedByAccountIdAndIdempotencyKey(actor.accountId(), key);
        if (prior.isPresent()) {
            if (!fingerprint.equals(prior.get().getRequestFingerprint())) throw new BillingException("IDEMPOTENCY_CONFLICT");
            return view(prior.get());
        }
        InvoiceJpaEntity invoice = invoices.findByIdForUpdate(invoiceId).orElseThrow(() -> new BillingException("INVOICE_NOT_FOUND"));
        if (!"DIAGNOSTIC".equals(invoice.getSourceType())
                || invoice.getStatus() != InvoiceStatus.PAID && invoice.getStatus() != InvoiceStatus.PARTIALLY_REFUNDED)
            throw new BillingException("REFUND_NOT_ELIGIBLE");
        var item = items.findById(new fu.fa26.sep490.g58.otcms_be.billing.model.entity.InvoiceItemJpaEntity.Key(invoiceId, lineNumber))
                .orElseThrow(() -> new BillingException("REFUND_NOT_ELIGIBLE"));
        if (item.getClinicalOrderId() == null || !incidents.verifyNoAlternativeForRefund(incidentId, item.getClinicalOrderId()))
            throw new BillingException("REFUND_NOT_ELIGIBLE");
        if (refunds.findFirstByInvoiceIdAndLineNumberAndStatusIn(invoiceId, lineNumber, ACTIVE_OR_COMPLETE).isPresent())
            throw new BillingException("REFUND_ALREADY_REQUESTED");
        BigDecimal amount = item.getUnitPriceSnapshot().multiply(BigDecimal.valueOf(item.getQuantity()));
        var saved = refunds.saveAndFlush(new DiagnosticItemRefundRequestJpaEntity(UUID.randomUUID(), invoiceId,
                lineNumber, item.getClinicalOrderId(), incidentId, actor.accountId(), key, fingerprint, normalized, amount));
        return view(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundView> pending(int page, int size, AuthenticatedAccount actor) {
        requireRole(actor, AccountRole.ADMIN);
        if (page < 0 || size < 1 || size > 100) throw new BillingException("REFUND_REQUEST_INVALID");
        return refunds.findAllByStatusOrderByRequestedAtAscIdAsc("PENDING_ADMIN_REVIEW", PageRequest.of(page, size))
                .stream().map(DiagnosticItemRefundServiceImpl::view).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundView> approved(int page, int size, AuthenticatedAccount actor) {
        requireRole(actor, AccountRole.RECEPTIONIST);
        if (page < 0 || size < 1 || size > 100) throw new BillingException("REFUND_REQUEST_INVALID");
        return refunds.findAllByStatusOrderByRequestedAtAscIdAsc("APPROVED", PageRequest.of(page, size))
                .stream().map(DiagnosticItemRefundServiceImpl::view).toList();
    }

    @Override
    @Transactional
    public RefundView decide(UUID refundId, String decision, String reason, AuthenticatedAccount actor) {
        requireRole(actor, AccountRole.ADMIN);
        String normalized = requireText(reason, 1000);
        if (!"APPROVE".equals(decision) && !"REJECT".equals(decision)) throw new BillingException("REFUND_REQUEST_INVALID");
        var refund = refunds.findByIdForUpdate(refundId).orElseThrow(() -> new BillingException("REFUND_NOT_FOUND"));
        String target = "APPROVE".equals(decision) ? "APPROVED" : "REJECTED";
        if (!"PENDING_ADMIN_REVIEW".equals(refund.getStatus())) {
            if (target.equals(refund.getStatus()) && actor.accountId().equals(refund.getApprovedByAccountId())
                    && normalized.equals(refund.getDecisionReason())) return view(refund);
            throw new BillingException("REFUND_STATE_CONFLICT");
        }
        if ("APPROVED".equals(target) && !incidents.verifyNoAlternativeForRefund(refund.getIncidentId(), refund.getClinicalOrderId()))
            throw new BillingException("REFUND_NOT_ELIGIBLE");
        refund.decide(actor.accountId(), target, normalized, clock.instant());
        return view(refunds.saveAndFlush(refund));
    }

    @Override
    @Transactional
    public RefundView recordExecution(UUID refundId, String providerReference, AuthenticatedAccount actor) {
        requireRole(actor, AccountRole.RECEPTIONIST);
        String reference = requireText(providerReference, 128);
        var refund = refunds.findByIdForUpdate(refundId).orElseThrow(() -> new BillingException("REFUND_NOT_FOUND"));
        if ("REFUNDED".equals(refund.getStatus())) {
            if (reference.equals(refund.getProviderReference())) return view(refund);
            throw new BillingException("REFUND_STATE_CONFLICT");
        }
        if (!"APPROVED".equals(refund.getStatus())) throw new BillingException("REFUND_STATE_CONFLICT");
        if (!incidents.verifyNoAlternativeForRefund(refund.getIncidentId(), refund.getClinicalOrderId()))
            throw new BillingException("REFUND_NOT_ELIGIBLE");
        InvoiceJpaEntity invoice = invoices.findByIdForUpdate(refund.getInvoiceId())
                .orElseThrow(() -> new BillingException("INVOICE_NOT_FOUND"));
        refund.markRefunded(reference, clock.instant());
        refunds.saveAndFlush(refund);
        BigDecimal refunded = refunds.findAllByInvoiceIdAndStatus(invoice.getId(), "REFUNDED").stream()
                .map(DiagnosticItemRefundRequestJpaEntity::getAllocatedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paid = payments.sumByInvoiceAndStatus(invoice.getId(), fu.fa26.sep490.g58.otcms_be.billing.model.PaymentAttemptStatus.CONFIRMED);
        List<fu.fa26.sep490.g58.otcms_be.billing.model.entity.InvoiceItemJpaEntity> invoiceItems = items.findAllByInvoiceIdOrderByLineNumber(invoice.getId());
        BigDecimal total = invoiceItems.stream().map(line -> line.getUnitPriceSnapshot().multiply(BigDecimal.valueOf(line.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (refunded.compareTo(paid) > 0 || refunded.compareTo(total) > 0) throw new BillingException("REFUND_AMOUNT_INVALID");
        invoice.setStatus(refunded.compareTo(total) == 0 ? InvoiceStatus.REFUNDED : InvoiceStatus.PARTIALLY_REFUNDED);
        invoices.saveAndFlush(invoice);
        UUID eventId = UUID.nameUUIDFromBytes(("DiagnosticOrderRefunded:" + refund.getId() + ":" + reference)
                .getBytes(StandardCharsets.UTF_8));
        clinicalOrders.cancelAfterVerifiedItemRefund(eventId, invoice.getId(), refund.getLineNumber(),
                refund.getClinicalOrderId(), refund.getDecisionReason());
        return view(refund);
    }

    private static RefundView view(DiagnosticItemRefundRequestJpaEntity entity) {
        return new RefundView(entity.getId(), entity.getInvoiceId(), entity.getLineNumber(), entity.getClinicalOrderId(),
                entity.getIncidentId(), entity.getReason(), entity.getStatus(), entity.getAllocatedAmount(),
                entity.getRequestedByAccountId(), entity.getApprovedByAccountId(), entity.getDecisionReason(),
                entity.getProviderReference(), entity.getRequestedAt(), entity.getDecidedAt(), entity.getRefundedAt());
    }
    private static String requireText(String value, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max) throw new BillingException("REFUND_REQUEST_INVALID");
        return value.trim();
    }
    private static void requireRole(AuthenticatedAccount actor, AccountRole role) {
        if (actor == null || actor.accountRole() != role) throw new BillingException("ACCESS_DENIED");
    }
    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is required", exception); }
    }
}
