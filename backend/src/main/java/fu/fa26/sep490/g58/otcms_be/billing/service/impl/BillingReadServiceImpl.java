package fu.fa26.sep490.g58.otcms_be.billing.service.impl;

import fu.fa26.sep490.g58.otcms_be.billing.exception.BillingException;
import fu.fa26.sep490.g58.otcms_be.billing.model.PaymentAttemptStatus;
import fu.fa26.sep490.g58.otcms_be.billing.repository.*;
import fu.fa26.sep490.g58.otcms_be.billing.service.*;
import fu.fa26.sep490.g58.otcms_be.common.security.*;
import fu.fa26.sep490.g58.otcms_be.patient.service.AuthorizePatientBillingProfileService;
import fu.fa26.sep490.g58.otcms_be.patient.exception.PatientAuthorizationDeniedException;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class BillingReadServiceImpl implements BillingReadService {
    private static final Set<AccountRole> STAFF = Set.of(AccountRole.RECEPTIONIST, AccountRole.ADMIN, AccountRole.OWNER);
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final BillingReadRepository reads;
    private final AuthorizePatientBillingProfileService patients;
    private final InvoiceService invoices;
    private final PaymentAttemptRepository payments;

    public BillingReadServiceImpl(BillingReadRepository reads, AuthorizePatientBillingProfileService patients,
            InvoiceService invoices, PaymentAttemptRepository payments) {
        this.reads = reads; this.patients = patients; this.invoices = invoices; this.payments = payments;
    }
    @Override
    public ResultPage<Payment> payments(AuthenticatedAccount actor, UUID patientId, LocalDate from, LocalDate to, int page, int size) {
        requireActor(actor);
        boolean staff = STAFF.contains(actor.accountRole());
        List<UUID> scope = List.of();
        if (!staff) {
            if (actor.accountRole() != AccountRole.PATIENT) throw error("ACCESS_DENIED");
            try { scope = patients.accessibleProfileIds(actor.accountId()); }
            catch (PatientAuthorizationDeniedException denied) { throw error("ACCESS_DENIED"); }
            if (scope.isEmpty() || patientId != null && !scope.contains(patientId)) throw error("ACCESS_DENIED");
        }
        if (patientId != null) { scope = List.of(patientId); staff = false; }
        if (from != null && to != null && from.isAfter(to)) throw error("MSG-39");
        Instant start, end;
        try {
            start = from == null ? null : from.atStartOfDay(CLINIC_ZONE).toInstant();
            end = to == null ? null : to.plusDays(1).atStartOfDay(CLINIC_ZONE).toInstant();
        } catch (DateTimeException invalid) { throw error("MSG-39"); }
        var result = reads.completedPayments(staff, scope, start, end, page(page, size));
        return new ResultPage<>(result.stream().map(p -> new Payment(p.getId(), p.getInvoiceId(), p.getPatientId(),
                p.getInvoiceNumber(), p.getAmount(), p.getCurrency(), p.getChannel(), p.getConfirmedAt())).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }
    @Override
    public ResultPage<Balance> unpaid(AuthenticatedAccount actor, int page, int size) {
        requireActor(actor);
        if (!STAFF.contains(actor.accountRole())) throw error("ACCESS_DENIED");
        var result = reads.unpaidInvoices(page(page, size));
        return new ResultPage<>(result.stream().map(i -> new Balance(i.getId(), i.getPatientId(), i.getInvoiceNumber(),
                i.getIssuedAt(), i.getTotalAmount(), i.getPaidAmount(), i.getTotalAmount().subtract(i.getPaidAmount()))).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }
    @Override
    public String invoiceExport(AuthenticatedAccount actor, UUID invoiceId) {
        requireReceptionist(actor);
        if (invoiceId == null) throw error("MSG-39");
        var invoice = invoices.get(invoiceId, actor);
        var text = new StringBuilder("INVOICE\n").append(invoice.invoiceNumber()).append("\nInvoice ID: ").append(invoice.id())
                .append("\nPatient profile: ").append(invoice.patientProfileId()).append("\nIssued: ").append(invoice.issuedAt()).append("\n");
        for (var line : invoice.items()) {
            text.append(line.lineNumber()).append(". ").append(line.description()).append(" | ")
                    .append(line.quantity()).append(" x ").append(line.unitPrice()).append(" = ").append(line.lineTotal()).append(" VND\n");
        }
        return text.append("Total: ").append(invoice.totalAmount()).append(" VND\nPaid: ").append(invoice.paidAmount())
                .append(" VND\nOutstanding: ").append(invoice.outstandingBalance()).append(" VND\n").toString();
    }
    @Override
    public String receiptExport(AuthenticatedAccount actor, UUID paymentId) {
        requireReceptionist(actor);
        if (paymentId == null) throw error("MSG-39");
        var payment = payments.findById(paymentId).orElseThrow(() -> error("PAYMENT_ATTEMPT_NOT_FOUND"));
        if (payment.getStatus() != PaymentAttemptStatus.CONFIRMED || payment.getConfirmedAt() == null) throw error("MSG-42");
        var invoice = invoices.get(payment.getInvoiceId(), actor);
        return "PAYMENT RECEIPT\nPayment ID: " + payment.getId() + "\nInvoice: " + invoice.invoiceNumber()
                + "\nPatient profile: " + invoice.patientProfileId() + "\nConfirmed: " + payment.getConfirmedAt()
                + "\nChannel: " + payment.getChannel() + "\nAmount paid: " + payment.getAmount() + " " + payment.getCurrencyCode() + "\n";
    }
    private static Pageable page(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) throw error("MSG-39");
        return PageRequest.of(page, size);
    }
    private static void requireActor(AuthenticatedAccount actor) {
        if (actor == null || actor.accountId() == null || actor.accountRole() == null) throw error("ACCESS_DENIED");
    }
    private static void requireReceptionist(AuthenticatedAccount actor) {
        requireActor(actor);
        if (actor.accountRole() != AccountRole.RECEPTIONIST) throw error("ACCESS_DENIED");
    }
    private static BillingException error(String code) { return new BillingException(code); }
}
