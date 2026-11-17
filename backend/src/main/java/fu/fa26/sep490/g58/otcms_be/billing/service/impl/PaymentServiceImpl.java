package fu.fa26.sep490.g58.otcms_be.billing.service.impl;

import fu.fa26.sep490.g58.otcms_be.billing.exception.BillingException;
import fu.fa26.sep490.g58.otcms_be.billing.mapper.BillingMapper;
import fu.fa26.sep490.g58.otcms_be.billing.model.InvoiceStatus;
import fu.fa26.sep490.g58.otcms_be.billing.model.PaymentAttemptStatus;
import fu.fa26.sep490.g58.otcms_be.billing.model.PaymentChannel;
import fu.fa26.sep490.g58.otcms_be.billing.model.dto.request.CounterPaymentRequest;
import fu.fa26.sep490.g58.otcms_be.billing.model.dto.request.PaymentAttemptRequest;
import fu.fa26.sep490.g58.otcms_be.billing.model.dto.response.PaymentAttemptResponse;
import fu.fa26.sep490.g58.otcms_be.billing.model.entity.InvoiceItemJpaEntity;
import fu.fa26.sep490.g58.otcms_be.billing.model.entity.InvoiceJpaEntity;
import fu.fa26.sep490.g58.otcms_be.billing.model.entity.PaymentAttemptJpaEntity;
import fu.fa26.sep490.g58.otcms_be.billing.repository.InvoiceRepository;
import fu.fa26.sep490.g58.otcms_be.billing.repository.PaymentAttemptRepository;
import fu.fa26.sep490.g58.otcms_be.billing.service.PaymentGateway;
import fu.fa26.sep490.g58.otcms_be.billing.service.PaymentGatewayRegistry;
import fu.fa26.sep490.g58.otcms_be.billing.service.PaymentService;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditEntry;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditLogWriter;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.patient.exception.PatientAuthorizationDeniedException;
import fu.fa26.sep490.g58.otcms_be.patient.service.AuthorizePatientBillingProfileService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PaymentServiceImpl implements PaymentService {
    private static final String PROVIDER = "PAYOS";
    private static final String PAID_AFTER_VOID = "Paid after the invoice was voided";
    private final InvoiceSettlementSupport settlement;
    private final InvoiceRepository invoices;
    private final PaymentAttemptRepository attempts;
    private final AuditLogWriter audit;
    private final PaymentGatewayRegistry gateways;
    private final AuthorizePatientBillingProfileService patientAuthorization;
    private final BillingMapper mapper;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public PaymentServiceImpl(InvoiceRepository invoices, PaymentAttemptRepository attempts,
            AuditLogWriter audit, PaymentGatewayRegistry gateways,
            AuthorizePatientBillingProfileService patientAuthorization, BillingMapper mapper,
            PlatformTransactionManager transactionManager, Clock clock, InvoiceSettlementSupport settlement) {
        this.settlement = settlement;
        this.invoices = invoices;
        this.attempts = attempts;
        this.audit = audit;
        this.gateways = gateways;
        this.patientAuthorization = patientAuthorization;
        this.mapper = mapper;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Override
    public PaymentAttemptResponse recordCounterPayment(UUID invoiceId, CounterPaymentRequest request,
            AuthenticatedAccount actor, String idempotencyKey) {
        requireRole(actor, AccountRole.RECEPTIONIST);
        if (request == null || !"CASH".equals(request.method())) throw new BillingException("PAYMENT_METHOD_INVALID");
        List<UUID> itemIds = request.itemIds();
        if (itemIds == null || itemIds.isEmpty()) throw new BillingException("MSG-49");
        String fingerprint = hash(invoiceId + "|COUNTER|" + sorted(itemIds) + "|" + request.method());
        PaymentAttemptJpaEntity result = transactions.execute(status -> {
            PaymentAttemptJpaEntity prior = findIdempotent(actor, idempotencyKey, fingerprint);
            if (prior != null) return prior;
            // Lock order: invoice, then its items (inside lockItems), then attempts. Decisions use the locked snapshot.
            InvoiceJpaEntity invoice = settlement.lockInvoiceFresh(invoiceId).orElseThrow(() -> new BillingException("MSG-50"));
            // A retry that waited for the lock finds the first request's result here instead of a spurious MSG-49.
            prior = findIdempotent(actor, idempotencyKey, fingerprint);
            if (prior != null) return prior;
            if (invoice.getStatus() != InvoiceStatus.OPEN) throw new BillingException("MSG-49");
            Instant now = Instant.now(clock);
            InvoiceItemSnapshot snapshot = settlement.lockItems(invoiceId, now);
            settlement.requireConsistentAllocation(invoiceId, snapshot);
            List<InvoiceItemJpaEntity> picked = snapshot.pick(itemIds);
            PaymentAttemptJpaEntity payment = PaymentAttemptJpaEntity.counter(UUID.randomUUID(), invoiceId,
                    InvoiceItemSnapshot.total(picked), actor.accountId(), idempotencyKey, fingerprint,
                    request.method(), now);
            attempts.saveAndFlush(payment);
            snapshot.claim(picked, payment);
            settlement.settleByItems(invoice, snapshot, payment);
            settlement.publishServiceOrdersPaid(invoiceId, picked);
            return payment;
        });
        return attempts.findById(result.getId()).map(mapper::toPaymentAttempt)
                .orElseThrow(() -> new BillingException("PAYMENT_ATTEMPT_NOT_FOUND"));
    }

    @Override
    public PaymentAttemptResponse createOnlineAttempt(UUID invoiceId, PaymentAttemptRequest request,
            AuthenticatedAccount actor, String idempotencyKey) {
        PaymentChannel channel = request == null || request.channel() == null ? PaymentChannel.ONLINE : request.channel();
        // The patient pays online; a receptionist may only open the QR shown at the counter.
        if (channel == PaymentChannel.COUNTER) throw new BillingException("PAYMENT_CHANNEL_INVALID");
        requireRole(actor, channel == PaymentChannel.ONLINE ? AccountRole.PATIENT : AccountRole.RECEPTIONIST);
        List<UUID> requestedIds = request == null || request.itemIds() == null || request.itemIds().isEmpty()
                ? null : request.itemIds();
        InvoiceJpaEntity readable = invoices.findById(invoiceId).orElseThrow(() -> new BillingException("INVOICE_NOT_FOUND"));
        authorizeInvoice(readable, actor);
        PaymentGateway gateway = gateways.require(PROVIDER);
        String fingerprint = hash(invoiceId + "|" + channel + "|" + PROVIDER + "|"
                + (requestedIds == null ? "ALL" : sorted(requestedIds)));
        LinkReservation reservation = transactions.execute(status -> {
            PaymentAttemptJpaEntity prior = findIdempotent(actor, idempotencyKey, fingerprint);
            if (prior != null) return new LinkReservation(prior, false, List.of());
            InvoiceJpaEntity locked = settlement.lockInvoiceFresh(invoiceId)
                    .orElseThrow(() -> new BillingException("INVOICE_NOT_FOUND"));
            prior = findIdempotent(actor, idempotencyKey, fingerprint);
            if (prior != null) return new LinkReservation(prior, false, List.of());
            if (locked.getStatus() != InvoiceStatus.OPEN) throw new BillingException("INVOICE_NOT_PAYABLE");
            Instant now = Instant.now(clock);
            InvoiceItemSnapshot snapshot = settlement.lockItems(invoiceId, now);
            settlement.requireConsistentAllocation(invoiceId, snapshot);
            if (channel == PaymentChannel.ONLINE) {
                // uk_payment_attempt_active_online: one PENDING/RECONCILIATION_REQUIRED online attempt per invoice.
                if (attempts.findFirstByInvoiceIdAndChannelAndStatusOrderByCreatedAtDesc(invoiceId, channel,
                        PaymentAttemptStatus.RECONCILIATION_REQUIRED).isPresent()) {
                    throw new BillingException("PAYMENT_RECONCILIATION_FAILED");
                }
            }
            for (PaymentAttemptJpaEntity existing : attempts.findAllByInvoiceIdOrderByCreatedAtDesc(invoiceId)) {
                if (existing.getChannel() != channel || existing.getStatus() != PaymentAttemptStatus.PENDING) continue;
                if (existing.isOpenAt(now)) {
                    // Asking again for the same items (or for "everything") returns the open checkout; else MSG-49.
                    if (requestedIds == null || sameItems(snapshot.linkedTo(existing.getId()), requestedIds)) {
                        return new LinkReservation(existing, false, List.of());
                    }
                    if (channel == PaymentChannel.ONLINE) throw new BillingException("MSG-49");
                } else {
                    existing.fail(PaymentAttemptStatus.EXPIRED, "PayOS checkout link expired");
                    attempts.saveAndFlush(existing);
                }
            }
            List<InvoiceItemJpaEntity> picked = requestedIds == null ? snapshot.payable() : snapshot.pick(requestedIds);
            if (picked.isEmpty()) throw new BillingException("MSG-49");
            BigDecimal due = InvoiceItemSnapshot.total(picked);
            if (due.signum() <= 0) throw new BillingException("MSG-49");
            long orderCode = nextOrderCode();
            PaymentAttemptJpaEntity pending = PaymentAttemptJpaEntity.pendingProvider(channel, UUID.randomUUID(),
                    invoiceId, due, actor.accountId(), idempotencyKey, fingerprint, orderCode);
            attempts.saveAndFlush(pending);
            // The link stays after expiry/failure on purpose: a late webhook still knows which items it pays.
            snapshot.claim(picked, pending);
            List<PaymentGateway.GatewayItem> gatewayItems = picked.stream().map(item -> new PaymentGateway.GatewayItem(
                    item.getDescriptionSnapshot(), item.getQuantity(), item.getUnitPriceSnapshot().longValueExact())).toList();
            return new LinkReservation(pending, true, gatewayItems);
        });
        PaymentAttemptJpaEntity attempt = reservation.attempt();
        if (attempt.getCheckoutUrl() != null) return mapper.toPaymentAttempt(attempt);
        if (!reservation.createLink()) return mapper.toPaymentAttempt(attempt);
        Instant now = Instant.now(clock);
        Instant expiry = now.plusSeconds(gateway.linkTtlSeconds());
        try {
            long checkoutAmount = attempt.getAmount().longValueExact();
            PaymentGateway.GatewayLink link = gateway.createLink(new PaymentGateway.GatewayPaymentCommand(
                    attempt.getProviderOrderCode(), checkoutAmount,
                    "Invoice " + invoiceId.toString().substring(0, 8),
                    attempt.getId(), expiry, reservation.gatewayItems()));
            PaymentAttemptJpaEntity updated = transactions.execute(status -> {
                PaymentAttemptJpaEntity managed = attempts.findById(attempt.getId())
                        .orElseThrow(() -> new BillingException("PAYMENT_ATTEMPT_NOT_FOUND"));
                if (managed.getStatus() == PaymentAttemptStatus.PENDING && managed.getCheckoutUrl() == null) {
                    var transfer = link.transfer();
                    managed.attachPayOsLink(link.providerLinkId(), link.checkoutUrl(), link.qrCode(), link.expiresAt(),
                            transfer == null ? null : transfer.bankBin(),
                            transfer == null ? null : transfer.accountNumber(),
                            transfer == null ? null : transfer.accountName(),
                            transfer == null ? null : transfer.description());
                }
                return attempts.save(managed);
            });
            return mapper.toPaymentAttempt(updated);
        } catch (RuntimeException exception) {
            transactions.executeWithoutResult(status -> attempts.findById(attempt.getId()).ifPresent(payment ->
                    payment.fail(PaymentAttemptStatus.RECONCILIATION_REQUIRED,
                            "PayOS link creation outcome is unknown; reconcile before retrying")));
            if (exception instanceof BillingException billing) throw billing;
            throw new BillingException("GATEWAY_UNAVAILABLE", exception);
        }
    }

    @Override
    public PaymentAttemptResponse getAttempt(UUID attemptId, AuthenticatedAccount actor) {
        PaymentAttemptJpaEntity attempt = attempts.findById(attemptId)
                .orElseThrow(() -> new BillingException("PAYMENT_ATTEMPT_NOT_FOUND"));
        InvoiceJpaEntity invoice = invoices.findById(attempt.getInvoiceId())
                .orElseThrow(() -> new BillingException("INVOICE_NOT_FOUND"));
        authorizeInvoice(invoice, actor);
        return mapper.toPaymentAttempt(attempt);
    }

    @Override
    public void handleVerifiedGatewayEvent(PaymentGateway.VerifiedGatewayEvent event) {
        if (event == null || !PROVIDER.equals(event.provider())) throw new BillingException("INVALID_WEBHOOK");
        String fingerprint = hash(event.provider() + "|" + event.orderCode() + "|" + event.amount() + "|"
                + event.currency() + "|" + event.paymentLinkId() + "|" + event.transactionReference() + "|"
                + event.code() + "|" + event.successful());
        transactions.executeWithoutResult(status -> applyGatewayEvent(event, fingerprint));
    }

    /**
     * Never throws for business states (paid after VOID / hold released, duplicate, unknown order, missing invoice):
     * the webhook must answer 2xx so PayOS does not retry forever. Such money is flagged RECONCILIATION_REQUIRED.
     */
    private void applyGatewayEvent(PaymentGateway.VerifiedGatewayEvent event, String fingerprint) {
        PaymentAttemptJpaEntity payment = attempts.findFirstByProviderOrderCode(event.orderCode()).orElse(null);
        if (payment == null) {
            writeWebhookAudit(event.provider(), fingerprint, event.orderCode(), event.transactionReference(),
                    true, null, "REJECTED", "UNKNOWN_ORDER_CODE");
            return;
        }
        // Lock the invoice and re-read the attempt BEFORE the replay check: an identical delivery running in
        // parallel waits here and then sees the fingerprint the first delivery committed.
        InvoiceJpaEntity invoice = settlement.lockInvoiceFresh(payment.getInvoiceId(), payment).orElse(null);
        if (fingerprint.equals(payment.getLastEventFingerprint())) return; // replay: no state change, no audit row
        String outcome = "REJECTED";
        String detail = "ATTEMPT_OR_AMOUNT_MISMATCH";
        if (event.successful() && matches(payment, event)) {
            if (invoice == null) {
                detail = "INVOICE_NOT_FOUND";
            } else if (payment.getStatus() == PaymentAttemptStatus.CONFIRMED) {
                outcome = "DUPLICATE";
                detail = null;
            } else if (invoice.getStatus() == InvoiceStatus.VOID) {
                settlement.holdForReconciliation(payment, PAID_AFTER_VOID, event.transactionReference());
                outcome = "RECONCILIATION_REQUIRED";
                detail = "INVOICE_VOID";
            } else {
                var applied = settlement.applyProviderPayment(invoice, payment, event.transactionReference(),
                        Instant.now(clock));
                outcome = applied.outcome();
                detail = applied.detail();
            }
        } else if (!event.successful() && payment.getStatus() == PaymentAttemptStatus.PENDING) {
            payment.fail(PaymentAttemptStatus.FAILED, "PayOS reported an unsuccessful payment");
            outcome = "APPLIED";
            detail = "UNSUCCESSFUL_PROVIDER_PAYMENT";
        }
        if (!"DUPLICATE".equals(outcome)) payment.recordVerifiedEvent(fingerprint);
        writeWebhookAudit(event.provider(), fingerprint, event.orderCode(), event.transactionReference(),
                true, payment.getId(), outcome, detail);
    }

    @Override
    public void recordRejectedGatewayEvent(String provider, Object payload) {
        String fingerprint = hash(provider + "|UNVERIFIED|" + String.valueOf(payload));
        transactions.executeWithoutResult(status -> writeWebhookAudit(provider, fingerprint, null, null,
                false, null, "REJECTED", "INVALID_SIGNATURE_OR_PAYLOAD"));
    }

    /** Webhook history lives in audit_logs (hidden from the activity log); the payload itself is never stored. */
    private void writeWebhookAudit(String provider, String fingerprint, Long orderCode, String reference,
            boolean signatureVerified, UUID attemptId, String outcome, String detail) {
        Map<String, Object> metadata = new HashMap<>(); // HashMap: values may be null, Map.of rejects null
        metadata.put("provider", provider);
        metadata.put("fingerprint", fingerprint);
        metadata.put("orderCode", orderCode);
        metadata.put("transactionReference", reference);
        metadata.put("signatureVerified", signatureVerified);
        metadata.put("detail", detail);
        audit.record(AuditEntry.builder("payment_gateway_events", "PAYMENT_GATEWAY", outcome, "PAYMENT_WEBHOOK")
                .entityId(attemptId).actorType("PAYOS").outcome(outcome).visibleInActivityLog(false)
                .metadata(metadata).build());
    }

    @Override
    public PaymentAttemptResponse reconcile(UUID attemptId, AuthenticatedAccount actor) {
        if (actor == null) throw new BillingException("ACCESS_DENIED");
        PaymentAttemptJpaEntity attempt = attempts.findById(attemptId)
                .orElseThrow(() -> new BillingException("PAYMENT_ATTEMPT_NOT_FOUND"));
        InvoiceJpaEntity readableInvoice = invoices.findById(attempt.getInvoiceId())
                .orElseThrow(() -> new BillingException("INVOICE_NOT_FOUND"));
        authorizeInvoice(readableInvoice, actor);
        if (attempt.getChannel() == PaymentChannel.COUNTER) throw new BillingException("PAYMENT_RECONCILIATION_FAILED");
        PaymentGateway.GatewayPaymentStatus providerStatus;
        try {
            providerStatus = gateways.require(attempt.getProvider()).query(attempt.getProviderOrderCode());
        } catch (RuntimeException exception) {
            throw new BillingException("PAYMENT_RECONCILIATION_FAILED", exception);
        }
        return applyProviderStatus(attemptId, providerStatus);
    }

    /**
     * Applies an authoritative provider status (queried by the caller OUTSIDE any transaction) under the invoice lock.
     * Used by manual reconcile. A PAID status on a VOID invoice is never applied.
     */
    @Override
    public PaymentAttemptResponse applyProviderStatus(UUID attemptId, PaymentGateway.GatewayPaymentStatus providerStatus) {
        PaymentAttemptJpaEntity reconciled = transactions.execute(status -> {
            PaymentAttemptJpaEntity managed = attempts.findById(attemptId)
                    .orElseThrow(() -> new BillingException("PAYMENT_ATTEMPT_NOT_FOUND"));
            InvoiceJpaEntity invoice = settlement.lockInvoiceFresh(managed.getInvoiceId(), managed)
                    .orElseThrow(() -> new BillingException("INVOICE_NOT_FOUND"));
            if ("PAID".equalsIgnoreCase(providerStatus.status())) {
                boolean providerMatches = "VND".equalsIgnoreCase(providerStatus.currency())
                        && providerStatus.amountPaid() == managed.getAmount().longValueExact()
                        && providerStatus.amount() == managed.getAmount().longValueExact()
                        && managed.getProviderOrderCode() == providerStatus.orderCode()
                        && (managed.getProviderPaymentLinkId() == null || managed.getProviderPaymentLinkId().equals(providerStatus.paymentLinkId()));
                if (invoice.getStatus() == InvoiceStatus.VOID && managed.getStatus() != PaymentAttemptStatus.CONFIRMED) {
                    settlement.holdForReconciliation(managed, PAID_AFTER_VOID, providerStatus.transactionReference());
                } else if (!providerMatches) {
                    managed.requireReconciliation("PayOS payment does not match the recorded attempt",
                            providerStatus.transactionReference());
                } else if (managed.getStatus() != PaymentAttemptStatus.CONFIRMED) {
                    settlement.applyProviderPayment(invoice, managed, providerStatus.transactionReference(),
                            Instant.now(clock));
                }
            } else if (managed.getStatus() != PaymentAttemptStatus.CONFIRMED
                    && List.of("CANCELLED", "EXPIRED", "FAILED").contains(providerStatus.status().toUpperCase(Locale.ROOT))) {
                managed.fail("CANCELLED".equalsIgnoreCase(providerStatus.status()) ? PaymentAttemptStatus.CANCELLED
                        : "EXPIRED".equalsIgnoreCase(providerStatus.status()) ? PaymentAttemptStatus.EXPIRED : PaymentAttemptStatus.FAILED,
                        "Authoritative PayOS status: " + providerStatus.status());
            }
            return attempts.save(managed);
        });
        return mapper.toPaymentAttempt(reconciled);
    }

    private boolean matches(PaymentAttemptJpaEntity payment, PaymentGateway.VerifiedGatewayEvent event) {
        return payment.getProviderOrderCode() == event.orderCode()
                && payment.getAmount().longValueExact() == event.amount()
                && "VND".equalsIgnoreCase(event.currency())
                && (payment.getProviderPaymentLinkId() == null || payment.getProviderPaymentLinkId().equals(event.paymentLinkId()));
    }
    private static List<UUID> sorted(List<UUID> ids) {
        return ids.stream().sorted().toList();
    }
    private static boolean sameItems(List<InvoiceItemJpaEntity> linked, List<UUID> requestedIds) {
        return linked.size() == requestedIds.size() && linked.stream().map(InvoiceItemJpaEntity::getId)
                .collect(java.util.stream.Collectors.toSet()).equals(new java.util.HashSet<>(requestedIds));
    }
    private PaymentAttemptJpaEntity findIdempotent(AuthenticatedAccount actor, String key, String fingerprint) {
        if (key == null || key.isBlank() || key.length() > 128) throw new BillingException("IDEMPOTENCY_CONFLICT");
        return attempts.findFirstByCreatedByAccountIdAndIdempotencyKey(actor.accountId(), key).map(existing -> {
            if (!existing.getRequestFingerprint().equals(fingerprint)) throw new BillingException("IDEMPOTENCY_CONFLICT");
            return existing;
        }).orElse(null);
    }
    private void authorizeInvoice(InvoiceJpaEntity invoice, AuthenticatedAccount actor) {
        if (actor == null) throw new BillingException("ACCESS_DENIED");
        if (actor.accountRole() == AccountRole.PATIENT) {
            try { patientAuthorization.authorize(actor.accountId(), invoice.getPatientProfileId()); }
            catch (PatientAuthorizationDeniedException exception) { throw new BillingException("ACCESS_DENIED", exception); }
        } else requireAnyRole(actor, AccountRole.RECEPTIONIST, AccountRole.ADMIN, AccountRole.OWNER);
    }
    private static void requireRole(AuthenticatedAccount actor, AccountRole role) {
        if (actor == null || actor.accountRole() != role) throw new BillingException("ACCESS_DENIED");
    }
    private static void requireAnyRole(AuthenticatedAccount actor, AccountRole... roles) {
        if (actor == null || java.util.Arrays.stream(roles).noneMatch(role -> role == actor.accountRole()))
            throw new BillingException("ACCESS_DENIED");
    }
    private static long nextOrderCode() {
        // PayOS receives this value as a JSON number. Keep it within the integer
        // range that JavaScript-based JSON consumers can represent exactly.
        return ThreadLocalRandom.current().nextLong(1L, 9_007_199_254_740_992L);
    }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is required", exception); }
    }
    private record LinkReservation(PaymentAttemptJpaEntity attempt, boolean createLink,
            List<PaymentGateway.GatewayItem> gatewayItems) { }
}
