package fu.fa26.sep490.g58.otcms_be.billing.service.impl;

import fu.fa26.sep490.g58.otcms_be.billing.model.InvoiceStatus;
import fu.fa26.sep490.g58.otcms_be.billing.model.PaymentAttemptStatus;
import fu.fa26.sep490.g58.otcms_be.billing.model.PaymentChannel;
import fu.fa26.sep490.g58.otcms_be.billing.repository.InvoiceRepository;
import fu.fa26.sep490.g58.otcms_be.billing.repository.PaymentAttemptRepository;
import fu.fa26.sep490.g58.otcms_be.billing.service.InvoiceService.VoidResult;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Voids the unpaid invoice of a released appointment hold. The only writer of {@code InvoiceStatus.VOID}. */
@Component
public class AppointmentInvoiceVoider {
    private final InvoiceRepository invoices;
    private final PaymentAttemptRepository attempts;
    private final InvoiceSettlementSupport settlement;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public AppointmentInvoiceVoider(InvoiceRepository invoices, PaymentAttemptRepository attempts,
            InvoiceSettlementSupport settlement, PlatformTransactionManager transactionManager, Clock clock) {
        this.invoices = invoices;
        this.attempts = attempts;
        this.settlement = settlement;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public VoidResult voidUnpaid(UUID appointmentId) {
        if (appointmentId == null) return VoidResult.NO_INVOICE;
        return transactions.execute(status -> {
            var found = invoices.findFirstByAppointmentId(appointmentId);
            if (found.isEmpty()) return VoidResult.NO_INVOICE;
            // Lock, then re-read: a webhook that committed while we waited must win over our stale snapshot.
            var invoice = settlement.lockInvoiceFresh(found.get().getId()).orElse(null);
            if (invoice == null) return VoidResult.NO_INVOICE;
            if (invoice.getStatus() == InvoiceStatus.VOID) return VoidResult.ALREADY_VOID;
            if (invoice.getStatus() != InvoiceStatus.OPEN || settlement.confirmed(invoice.getId()).signum() > 0) {
                return VoidResult.HAS_PAYMENT;
            }
            Instant now = Instant.now(clock);
            for (var attempt : attempts.findAllByInvoiceIdOrderByCreatedAtDesc(invoice.getId())) {
                if (attempt.getChannel() != PaymentChannel.ONLINE
                        || attempt.getStatus() != PaymentAttemptStatus.PENDING) continue;
                boolean linkAlive = attempt.getExpiresAt() != null && attempt.getExpiresAt().isAfter(now);
                attempt.fail(linkAlive ? PaymentAttemptStatus.CANCELLED : PaymentAttemptStatus.EXPIRED,
                        linkAlive ? "Appointment hold released before payment" : "Appointment payment hold expired");
                attempts.save(attempt);
            }
            invoice.setStatus(InvoiceStatus.VOID);
            invoices.save(invoice);
            return VoidResult.VOIDED;
        });
    }
}
