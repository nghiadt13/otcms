package fu.fa26.sep490.g58.otcms_be.billing.service.impl;

import fu.fa26.sep490.g58.otcms_be.billing.exception.BillingException;
import fu.fa26.sep490.g58.otcms_be.billing.mapper.BillingMapper;
import fu.fa26.sep490.g58.otcms_be.billing.model.InvoiceStatus;
import fu.fa26.sep490.g58.otcms_be.billing.model.PaymentAttemptStatus;
import fu.fa26.sep490.g58.otcms_be.billing.model.PaymentChannel;
import fu.fa26.sep490.g58.otcms_be.billing.model.dto.request.CounterPaymentRequest;
import fu.fa26.sep490.g58.otcms_be.billing.model.dto.response.PaymentAttemptResponse;
import fu.fa26.sep490.g58.otcms_be.billing.model.entity.InvoiceItemJpaEntity;
import fu.fa26.sep490.g58.otcms_be.billing.model.entity.InvoiceJpaEntity;
import fu.fa26.sep490.g58.otcms_be.billing.model.entity.PaymentAttemptJpaEntity;
import fu.fa26.sep490.g58.otcms_be.billing.model.entity.PaymentGatewayEventJpaEntity;
import fu.fa26.sep490.g58.otcms_be.billing.repository.InvoiceItemRepository;
import fu.fa26.sep490.g58.otcms_be.billing.repository.InvoiceRepository;
import fu.fa26.sep490.g58.otcms_be.billing.repository.PaymentAttemptRepository;
import fu.fa26.sep490.g58.otcms_be.billing.repository.PaymentGatewayEventRepository;
import fu.fa26.sep490.g58.otcms_be.billing.service.PaymentGateway;
import fu.fa26.sep490.g58.otcms_be.billing.service.PaymentGatewayRegistry;
import fu.fa26.sep490.g58.otcms_be.billing.service.PaymentService;
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
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PaymentServiceImpl implements PaymentService {
    private static final String PROVIDER = "PAYOS";
    private final InvoiceRepository invoices;
    private final InvoiceItemRepository items;
    private final PaymentAttemptRepository attempts;
    private final PaymentGatewayEventRepository events;
    private final PaymentGatewayRegistry gateways;
    private final AuthorizePatientBillingProfileService patientAuthorization;
    private final BillingMapper mapper;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public PaymentServiceImpl(InvoiceRepository invoices, InvoiceItemRepository items,
            PaymentAttemptRepository attempts, PaymentGatewayEventRepository events,
            PaymentGatewayRegistry gateways, AuthorizePatientBillingProfileService patientAuthorization,
            BillingMapper mapper, PlatformTransactionManager transactionManager, Clock clock) {
        this.invoices = invoices;
        this.items = items;
        this.attempts = attempts;
        this.events = events;
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
        if (request == null || request.amount() == null || request.amount() <= 0
                || !List.of("CASH", "BANK_TRANSFER", "OTHER").contains(request.method())) {
            throw new BillingException("PAYMENT_AMOUNT_INVALID");
        }
        String fingerprint = hash(invoiceId + "|COUNTER|" + request.amount() + "|" + request.method());
        PaymentAttemptJpaEntity result = transactions.execute(status -> {
            PaymentAttemptJpaEntity prior = findIdempotent(actor, idempotencyKey, fingerprint);
            if (prior != null) return prior;
            InvoiceJpaEntity invoice = lockPayableInvoice(invoiceId);
            BigDecimal due = outstanding(invoiceId);
            BigDecimal amount = BigDecimal.valueOf(request.amount()).setScale(2);
            if (amount.signum() <= 0 || amount.compareTo(due) > 0) throw new BillingException("PAYMENT_AMOUNT_INVALID");
            PaymentAttemptJpaEntity payment = PaymentAttemptJpaEntity.counter(UUID.randomUUID(), invoiceId,
                    amount, actor.accountId(), idempotencyKey, fingerprint, request.method(), Instant.now(clock));
            attempts.saveAndFlush(payment);
            updateInvoiceStatus(invoice, invoiceId);
            return payment;
        });
        return attempts.findById(result.getId()).map(mapper::toPaymentAttempt)
                .orElseThrow(() -> new BillingException("PAYMENT_ATTEMPT_NOT_FOUND"));
    }

    @Override
    public PaymentAttemptResponse createOnlineAttempt(UUID invoiceId, AuthenticatedAccount actor, String idempotencyKey) {
        if (actor == null || actor.accountRole() != AccountRole.PATIENT) {
            throw new BillingException("ACCESS_DENIED");
        }
        InvoiceJpaEntity readable = invoices.findById(invoiceId).orElseThrow(() -> new BillingException("INVOICE_NOT_FOUND"));
        authorizeInvoice(readable, actor);
        PaymentGateway gateway = gateways.require(PROVIDER);
        String fingerprint = hash(invoiceId + "|ONLINE|" + PROVIDER);
        LinkReservation reservation = transactions.execute(status -> {
            PaymentAttemptJpaEntity prior = findIdempotent(actor, idempotencyKey, fingerprint);
            if (prior != null) return new LinkReservation(prior, false);
            lockPayableInvoice(invoiceId);
            var reconciliation = attempts.findFirstByInvoiceIdAndChannelAndStatusOrderByCreatedAtDesc(
                    invoiceId, PaymentChannel.ONLINE, PaymentAttemptStatus.RECONCILIATION_REQUIRED);
            if (reconciliation.isPresent()) throw new BillingException("PAYMENT_RECONCILIATION_FAILED");
            var active = attempts.findFirstByInvoiceIdAndChannelAndStatusOrderByCreatedAtDesc(
                    invoiceId, PaymentChannel.ONLINE, PaymentAttemptStatus.PENDING);
            if (active.isPresent()) {
                PaymentAttemptJpaEntity existing = active.get();
                if (existing.getExpiresAt() == null || existing.getExpiresAt().isAfter(Instant.now(clock))) {
                    return new LinkReservation(existing, false);
                }
                existing.fail(PaymentAttemptStatus.EXPIRED, "PayOS checkout link expired");
                attempts.saveAndFlush(existing);
            }
            BigDecimal due = outstanding(invoiceId);
            if (due.signum() <= 0) throw new BillingException("INVOICE_NOT_PAYABLE");
            long orderCode = nextOrderCode();
            PaymentAttemptJpaEntity pending = PaymentAttemptJpaEntity.pendingOnline(UUID.randomUUID(), invoiceId,
                    due, actor.accountId(), idempotencyKey, fingerprint, orderCode);
            attempts.saveAndFlush(pending);
            return new LinkReservation(pending, true);
        });
        PaymentAttemptJpaEntity attempt = reservation.attempt();
        if (attempt.getCheckoutUrl() != null) return mapper.toPaymentAttempt(attempt);
        if (!reservation.createLink()) return mapper.toPaymentAttempt(attempt);
        try {
            long checkoutAmount = attempt.getAmount().longValueExact();
            List<PaymentGateway.GatewayItem> gatewayItems = List.of(
                    new PaymentGateway.GatewayItem("Outstanding invoice balance", 1, checkoutAmount));
            Instant expiry = Instant.now(clock).plusSeconds(gateway.linkTtlSeconds());
            PaymentGateway.GatewayLink link = gateway.createLink(new PaymentGateway.GatewayPaymentCommand(
                    attempt.getProviderOrderCode(), checkoutAmount,
                    "Invoice " + invoiceId.toString().substring(0, 8),
                    attempt.getId(), expiry, gatewayItems));
            PaymentAttemptJpaEntity updated = transactions.execute(status -> {
                PaymentAttemptJpaEntity managed = attempts.findById(attempt.getId())
                        .orElseThrow(() -> new BillingException("PAYMENT_ATTEMPT_NOT_FOUND"));
                if (managed.getStatus() == PaymentAttemptStatus.PENDING && managed.getCheckoutUrl() == null) {
                    managed.attachPayOsLink(link.providerLinkId(), link.checkoutUrl(), link.qrCode(), link.expiresAt());
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
        transactions.executeWithoutResult(status -> {
            if (events.existsByProviderAndEventFingerprint(event.provider(), fingerprint)) return;
            PaymentAttemptJpaEntity payment = attempts.findFirstByProviderOrderCode(event.orderCode()).orElse(null);
            String outcome = "REJECTED";
            String detail = payment == null ? "UNKNOWN_ORDER_CODE" : "ATTEMPT_OR_AMOUNT_MISMATCH";
            if (payment != null && event.successful() && matches(payment, event)) {
                InvoiceJpaEntity invoice = invoices.findByIdForUpdate(payment.getInvoiceId())
                        .orElseThrow(() -> new BillingException("INVOICE_NOT_FOUND"));
                if (payment.getStatus() == PaymentAttemptStatus.CONFIRMED) {
                    outcome = "DUPLICATE";
                    detail = null;
                } else if (outstanding(payment.getInvoiceId()).compareTo(payment.getAmount()) != 0) {
                    payment.requireReconciliation("Provider reports payment but invoice balance changed", event.transactionReference());
                    outcome = "RECONCILIATION_REQUIRED";
                    detail = "OUTSTANDING_BALANCE_CHANGED";
                } else {
                    payment.confirm(event.transactionReference(), Instant.now(clock));
                    updateInvoiceStatus(invoice, payment.getInvoiceId());
                    outcome = "APPLIED";
                    detail = null;
                }
            } else if (payment != null && !event.successful()
                    && payment.getStatus() == PaymentAttemptStatus.PENDING) {
                payment.fail(PaymentAttemptStatus.FAILED, "PayOS reported an unsuccessful payment");
                outcome = "APPLIED";
                detail = "UNSUCCESSFUL_PROVIDER_PAYMENT";
            }
            events.save(new PaymentGatewayEventJpaEntity(UUID.randomUUID(), event.provider(), fingerprint,
                    event.orderCode(), event.transactionReference(), true, outcome, detail));
        });
    }

    @Override
    public void recordRejectedGatewayEvent(String provider, Object payload) {
        String fingerprint = hash(provider + "|UNVERIFIED|" + String.valueOf(payload));
        transactions.executeWithoutResult(status -> {
            if (!events.existsByProviderAndEventFingerprint(provider, fingerprint)) {
                events.save(new PaymentGatewayEventJpaEntity(UUID.randomUUID(), provider, fingerprint,
                        null, null, false, "REJECTED", "INVALID_SIGNATURE_OR_PAYLOAD"));
            }
        });
    }

    @Override
    public PaymentAttemptResponse reconcile(UUID attemptId, AuthenticatedAccount actor) {
        if (actor == null) throw new BillingException("ACCESS_DENIED");
        PaymentAttemptJpaEntity attempt = attempts.findById(attemptId)
                .orElseThrow(() -> new BillingException("PAYMENT_ATTEMPT_NOT_FOUND"));
        InvoiceJpaEntity readableInvoice = invoices.findById(attempt.getInvoiceId())
                .orElseThrow(() -> new BillingException("INVOICE_NOT_FOUND"));
        authorizeInvoice(readableInvoice, actor);
        if (attempt.getChannel() != PaymentChannel.ONLINE) throw new BillingException("PAYMENT_RECONCILIATION_FAILED");
        PaymentGateway.GatewayPaymentStatus providerStatus;
        try {
            providerStatus = gateways.require(attempt.getProvider()).query(attempt.getProviderOrderCode());
        } catch (RuntimeException exception) {
            throw new BillingException("PAYMENT_RECONCILIATION_FAILED", exception);
        }
        PaymentAttemptJpaEntity reconciled = transactions.execute(status -> {
            PaymentAttemptJpaEntity managed = attempts.findById(attemptId)
                    .orElseThrow(() -> new BillingException("PAYMENT_ATTEMPT_NOT_FOUND"));
            InvoiceJpaEntity invoice = invoices.findByIdForUpdate(managed.getInvoiceId())
                    .orElseThrow(() -> new BillingException("INVOICE_NOT_FOUND"));
            if ("PAID".equalsIgnoreCase(providerStatus.status())) {
                boolean providerMatches = "VND".equalsIgnoreCase(providerStatus.currency())
                        && providerStatus.amountPaid() == managed.getAmount().longValueExact()
                        && providerStatus.amount() == managed.getAmount().longValueExact()
                        && managed.getProviderOrderCode() == providerStatus.orderCode()
                        && (managed.getProviderPaymentLinkId() == null || managed.getProviderPaymentLinkId().equals(providerStatus.paymentLinkId()));
                if (!providerMatches) {
                    managed.requireReconciliation("PayOS payment does not match the recorded attempt",
                            providerStatus.transactionReference());
                } else if (managed.getStatus() != PaymentAttemptStatus.CONFIRMED) {
                    if (outstanding(managed.getInvoiceId()).compareTo(managed.getAmount()) == 0) {
                        managed.confirm(providerStatus.transactionReference(), Instant.now(clock));
                        updateInvoiceStatus(invoice, managed.getInvoiceId());
                    } else managed.requireReconciliation("Paid attempt no longer matches outstanding balance", providerStatus.transactionReference());
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
    private InvoiceJpaEntity lockPayableInvoice(UUID id) {
        InvoiceJpaEntity invoice = invoices.findByIdForUpdate(id).orElseThrow(() -> new BillingException("INVOICE_NOT_FOUND"));
        if (invoice.getStatus() != InvoiceStatus.OPEN) throw new BillingException("INVOICE_NOT_PAYABLE");
        return invoice;
    }
    private BigDecimal outstanding(UUID invoiceId) {
        BigDecimal total = items.findAllByInvoiceIdOrderByLineNumber(invoiceId).stream()
                .map(item -> item.getUnitPriceSnapshot().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.subtract(confirmed(invoiceId)).max(BigDecimal.ZERO);
    }
    private BigDecimal confirmed(UUID invoiceId) {
        BigDecimal amount = attempts.sumByInvoiceAndStatus(invoiceId, PaymentAttemptStatus.CONFIRMED);
        return amount == null ? BigDecimal.ZERO : amount;
    }
    private void updateInvoiceStatus(InvoiceJpaEntity invoice, UUID invoiceId) {
        invoice.setStatus(outstanding(invoiceId).signum() <= 0 ? InvoiceStatus.PAID : InvoiceStatus.OPEN);
        invoices.save(invoice);
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
    private record LinkReservation(PaymentAttemptJpaEntity attempt, boolean createLink) { }
}
