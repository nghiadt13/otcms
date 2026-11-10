package fu.fa26.sep490.g58.otcms_be.billing.service.impl;

import fu.fa26.sep490.g58.otcms_be.billing.model.InvoiceStatus;
import fu.fa26.sep490.g58.otcms_be.billing.model.PaymentAttemptStatus;
import fu.fa26.sep490.g58.otcms_be.billing.model.PaymentChannel;
import fu.fa26.sep490.g58.otcms_be.billing.model.entity.PaymentAttemptJpaEntity;
import fu.fa26.sep490.g58.otcms_be.billing.repository.InvoiceRepository;
import fu.fa26.sep490.g58.otcms_be.billing.repository.PaymentAttemptRepository;
import fu.fa26.sep490.g58.otcms_be.billing.service.AppointmentHoldSettlementService;
import fu.fa26.sep490.g58.otcms_be.billing.service.InvoiceService;
import fu.fa26.sep490.g58.otcms_be.billing.service.PaymentGateway;
import fu.fa26.sep490.g58.otcms_be.billing.service.PaymentGatewayRegistry;
import fu.fa26.sep490.g58.otcms_be.billing.service.PaymentService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Releases an AWAITING_PAYMENT hold on the billing side. Deliberately NOT transactional: the gateway is queried
 * with no transaction and no row lock open; locks are only taken inside the short write transactions of
 * {@link PaymentService#applyProviderStatus} and {@link InvoiceService#voidUnpaidAppointmentInvoice}
 * (lock order: invoice first, appointment second - the caller locks the appointment only after this returns).
 */
@Service
public class AppointmentHoldSettlementServiceImpl implements AppointmentHoldSettlementService {
    private static final Logger log = LoggerFactory.getLogger(AppointmentHoldSettlementServiceImpl.class);
    private static final String PROVIDER = "PAYOS";
    /** An attempt without recorded expiry is a link creation still in flight; give it this long before giving up. */
    private static final long IN_FLIGHT_LINK_SECONDS = 120;

    private final InvoiceRepository invoices;
    private final PaymentAttemptRepository attempts;
    private final PaymentGatewayRegistry gateways;
    private final PaymentService payments;
    private final InvoiceService invoiceService;
    private final Clock clock;

    public AppointmentHoldSettlementServiceImpl(InvoiceRepository invoices, PaymentAttemptRepository attempts,
            PaymentGatewayRegistry gateways, PaymentService payments, InvoiceService invoiceService, Clock clock) {
        this.invoices = invoices;
        this.attempts = attempts;
        this.gateways = gateways;
        this.payments = payments;
        this.invoiceService = invoiceService;
        this.clock = clock;
    }

    @Override
    public Outcome settle(UUID appointmentId, boolean force) {
        if (appointmentId == null) return Outcome.NO_INVOICE;
        var invoice = invoices.findFirstByAppointmentId(appointmentId).orElse(null);
        if (invoice == null) return Outcome.NO_INVOICE;
        if (invoice.getStatus() == InvoiceStatus.VOID) return Outcome.VOIDED;
        UUID invoiceId = invoice.getId();

        // (a) Ask the gateway about every live attempt, no transaction open. PAID is applied (and confirms the
        // appointment through the billing hook) before anything is voided.
        boolean gatewayUnavailable = false;
        for (PaymentAttemptJpaEntity attempt : pendingOnlineAttempts(invoiceId)) {
            PaymentGateway.GatewayPaymentStatus providerStatus;
            try {
                providerStatus = gateways.require(PROVIDER).query(attempt.getProviderOrderCode());
            } catch (RuntimeException exception) {
                log.warn("Gateway status query failed while settling appointment hold; orderCode={}",
                        attempt.getProviderOrderCode(), exception);
                gatewayUnavailable = true;
                continue;
            }
            var applied = payments.applyProviderStatus(attempt.getId(), providerStatus);
            if (PaymentAttemptStatus.CONFIRMED.name().equals(applied.status())) return Outcome.PAID;
        }

        // (b) Sweeper: a payment link that may still be paid keeps the hold. Unknown gateway state counts as alive.
        if (!force && (gatewayUnavailable || hasLiveAttempt(invoiceId))) return Outcome.STILL_PENDING;

        // (c) Void under the invoice lock. Money that arrived meanwhile wins: the invoice is never voided then.
        return switch (invoiceService.voidUnpaidAppointmentInvoice(appointmentId)) {
            case VOIDED, ALREADY_VOID -> Outcome.VOIDED;
            case NO_INVOICE -> Outcome.NO_INVOICE;
            case HAS_PAYMENT -> Outcome.PAID;
        };
    }

    private List<PaymentAttemptJpaEntity> pendingOnlineAttempts(UUID invoiceId) {
        return attempts.findAllByInvoiceIdOrderByCreatedAtDesc(invoiceId).stream()
                .filter(a -> a.getChannel() == PaymentChannel.ONLINE && a.getStatus() == PaymentAttemptStatus.PENDING
                        && a.getProviderOrderCode() != null)
                .toList();
    }

    /** Re-reads attempts after applyProviderStatus: expired/cancelled ones were closed by the provider status. */
    private boolean hasLiveAttempt(UUID invoiceId) {
        Instant now = Instant.now(clock);
        return attempts.findAllByInvoiceIdOrderByCreatedAtDesc(invoiceId).stream()
                .filter(a -> a.getChannel() == PaymentChannel.ONLINE && a.getStatus() == PaymentAttemptStatus.PENDING)
                .anyMatch(a -> a.getExpiresAt() != null
                        ? a.getExpiresAt().isAfter(now)
                        // Link creation still in flight (no expiry recorded yet): alive for one TTL after creation.
                        : a.getCreatedAt() == null || a.getCreatedAt().plusSeconds(IN_FLIGHT_LINK_SECONDS).isAfter(now));
    }
}
