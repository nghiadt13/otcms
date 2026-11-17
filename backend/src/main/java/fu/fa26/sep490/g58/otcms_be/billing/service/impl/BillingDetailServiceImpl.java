package fu.fa26.sep490.g58.otcms_be.billing.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.service.StaffAccountDirectory;
import fu.fa26.sep490.g58.otcms_be.billing.exception.BillingException;
import fu.fa26.sep490.g58.otcms_be.billing.model.InvoiceItemStatus;
import fu.fa26.sep490.g58.otcms_be.billing.model.PaymentAttemptStatus;
import fu.fa26.sep490.g58.otcms_be.billing.model.PaymentChannel;
import fu.fa26.sep490.g58.otcms_be.billing.model.dto.response.InvoiceResponse;
import fu.fa26.sep490.g58.otcms_be.billing.model.entity.InvoiceItemJpaEntity;
import fu.fa26.sep490.g58.otcms_be.billing.model.entity.InvoiceJpaEntity;
import fu.fa26.sep490.g58.otcms_be.billing.model.entity.PaymentAttemptJpaEntity;
import fu.fa26.sep490.g58.otcms_be.billing.repository.BillingReadRepository;
import fu.fa26.sep490.g58.otcms_be.billing.repository.InvoiceItemRepository;
import fu.fa26.sep490.g58.otcms_be.billing.repository.InvoiceRepository;
import fu.fa26.sep490.g58.otcms_be.billing.repository.PaymentAttemptRepository;
import fu.fa26.sep490.g58.otcms_be.billing.service.BillingDetailService;
import fu.fa26.sep490.g58.otcms_be.billing.service.BillingReadService.ResultPage;
import fu.fa26.sep490.g58.otcms_be.billing.service.InvoiceService;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.patient.exception.PatientAuthorizationDeniedException;
import fu.fa26.sep490.g58.otcms_be.patient.service.AuthorizePatientBillingProfileService;
import fu.fa26.sep490.g58.otcms_be.patient.service.PatientClinicalIdentityQueryService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Billing detail reads: every figure comes from the stored snapshots, never from the current price list. */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class BillingDetailServiceImpl implements BillingDetailService {
    private static final Set<AccountRole> STAFF = Set.of(AccountRole.RECEPTIONIST, AccountRole.ADMIN, AccountRole.OWNER);
    private static final Set<AccountRole> REFUND_VIEWERS = Set.of(AccountRole.ADMIN, AccountRole.OWNER);
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final UUID NO_ID = new UUID(0, 0);
    private static final Instant NO_END = Instant.parse("3000-01-01T00:00:00Z");
    private static final DateTimeFormatter RECEIPT_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private final BillingReadRepository reads;
    private final PaymentAttemptRepository attempts;
    private final InvoiceRepository invoices;
    private final InvoiceItemRepository items;
    private final InvoiceService invoiceService;
    private final AuthorizePatientBillingProfileService patients;
    private final PatientClinicalIdentityQueryService identities;
    private final StaffAccountDirectory staff;
    private final ReceiptPdfRenderer pdf;
    private final Clock clock;

    public BillingDetailServiceImpl(BillingReadRepository reads, PaymentAttemptRepository attempts,
            InvoiceRepository invoices, InvoiceItemRepository items, InvoiceService invoiceService,
            AuthorizePatientBillingProfileService patients, PatientClinicalIdentityQueryService identities,
            StaffAccountDirectory staff, ReceiptPdfRenderer pdf, Clock clock) {
        this.reads = reads; this.attempts = attempts; this.invoices = invoices; this.items = items;
        this.invoiceService = invoiceService; this.patients = patients; this.identities = identities;
        this.staff = staff; this.pdf = pdf; this.clock = clock;
    }

    @Override
    public PaymentDetail payment(AuthenticatedAccount actor, UUID paymentId) {
        requireActor(actor);
        return detail(actor, paymentId);
    }

    @Override
    public PaymentDetail receipt(AuthenticatedAccount actor, UUID paymentId) {
        requireActor(actor);
        if (actor.accountRole() != AccountRole.RECEPTIONIST) throw error("ACCESS_DENIED");
        return detail(actor, paymentId);
    }

    @Override
    public byte[] receiptPdf(AuthenticatedAccount actor, UUID paymentId) {
        return pdf.render(receipt(actor, paymentId));
    }

    @Override
    public ResultPage<UnpaidItem> unpaidItems(AuthenticatedAccount actor, UUID patientId, UUID serviceId,
            LocalDate from, LocalDate to, int page, int size) {
        requireStaff(actor);
        if (from != null && to != null && from.isAfter(to)) throw error("MSG-54");
        Instant start, end;
        try {
            start = from == null ? Instant.EPOCH : from.atStartOfDay(CLINIC_ZONE).toInstant();
            end = to == null ? NO_END : to.plusDays(1).atStartOfDay(CLINIC_ZONE).toInstant();
        } catch (DateTimeException invalid) { throw error("MSG-54"); }
        var result = reads.unpaidItems(patientId == null, patientId == null ? NO_ID : patientId, serviceId == null,
                serviceId == null ? NO_ID : serviceId, start, end, pageOf(page, size));
        Map<UUID, PatientClinicalIdentityQueryService.ClinicalIdentity> names = identities.findClinicalIdentities(
                new HashSet<>(result.stream().map(row -> ((InvoiceJpaEntity) row[1]).getPatientProfileId()).toList()));
        Instant now = Instant.now(clock);
        List<UnpaidItem> rows = result.stream().map(row -> {
            var item = (InvoiceItemJpaEntity) row[0];
            var invoice = (InvoiceJpaEntity) row[1];
            var identity = names.get(invoice.getPatientProfileId());
            return new UnpaidItem(item.getId(), invoice.getId(), invoice.getInvoiceNumber(), invoice.getPatientProfileId(),
                    identity == null ? null : identity.fullName(), invoice.getSourceType(), invoice.getVisitId(),
                    invoice.getAppointmentId(), item.getServiceId(), item.getDescriptionSnapshot(), item.getQuantity(),
                    item.getUnitPriceSnapshot(), lineTotal(item),
                    InvoiceItemStatus.resolve(item, (PaymentAttemptJpaEntity) row[2], now).name(), invoice.getIssuedAt());
        }).toList();
        return new ResultPage<>(rows, page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    public PatientBilling patientBilling(AuthenticatedAccount actor, UUID patientProfileId, int page, int size) {
        requireStaff(actor);
        if (patientProfileId == null) throw error("MSG-39");
        var identity = identities.findClinicalIdentities(List.of(patientProfileId)).get(patientProfileId);
        if (identity == null) throw error("MSG-41");
        var result = reads.patientInvoices(patientProfileId, pageOf(page, size));
        List<InvoiceResponse> rows = result.stream().map(invoice -> invoiceService.get(invoice.getId(), actor)).toList();
        return new PatientBilling(patientProfileId, identity.fullName(),
                new ResultPage<>(rows, page, size, result.getTotalElements(), result.getTotalPages()));
    }

    private PaymentDetail detail(AuthenticatedAccount actor, UUID paymentId) {
        if (paymentId == null) throw error("MSG-50");
        PaymentAttemptJpaEntity payment = attempts.findById(paymentId)
                .filter(attempt -> attempt.getStatus() == PaymentAttemptStatus.CONFIRMED && attempt.getConfirmedAt() != null)
                .orElseThrow(() -> error("MSG-50"));
        InvoiceJpaEntity invoice = invoices.findById(payment.getInvoiceId()).orElseThrow(() -> error("MSG-50"));
        requireVisible(actor, invoice);
        boolean showRefundReason = REFUND_VIEWERS.contains(actor.accountRole());
        Instant now = Instant.now(clock);
        List<Line> lines = items.findAllByPaymentAttemptIdOrderByLineNumber(paymentId).stream()
                .map(item -> new Line(item.getId(), item.getLineNumber(), item.getDescriptionSnapshot(), item.getQuantity(),
                        item.getUnitPriceSnapshot(), lineTotal(item), InvoiceItemStatus.resolve(item, payment, now).name(),
                        showRefundReason ? item.getRefundReason() : null)).toList();
        var identity = identities.findClinicalIdentities(List.of(invoice.getPatientProfileId())).get(invoice.getPatientProfileId());
        boolean counter = payment.getChannel() == PaymentChannel.COUNTER;
        return new PaymentDetail(payment.getId(), receiptNumber(payment), invoice.getId(), invoice.getInvoiceNumber(),
                invoice.getPatientProfileId(), identity == null ? null : identity.fullName(), payment.getAmount(),
                payment.getCurrencyCode(), counter ? payment.getCounterMethod() : "PAYOS", payment.getChannel().name(),
                payment.getConfirmedAt(), counter ? staff.staff(payment.getCreatedByAccountId())
                        .map(StaffAccountDirectory.Entry::fullName).orElse(null) : null,
                counter ? null : payment.getProviderTransactionReference(), lines);
    }

    /** Derived from immutable payment data, so a receipt never changes after it is issued (BR-52). */
    private static String receiptNumber(PaymentAttemptJpaEntity payment) {
        return "RC-" + RECEIPT_DATE.format(payment.getConfirmedAt().atZone(CLINIC_ZONE)) + "-"
                + payment.getId().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private void requireVisible(AuthenticatedAccount actor, InvoiceJpaEntity invoice) {
        if (STAFF.contains(actor.accountRole())) return;
        if (actor.accountRole() != AccountRole.PATIENT) throw error("ACCESS_DENIED");
        boolean allowed;
        try { allowed = patients.accessibleProfileIds(actor.accountId()).contains(invoice.getPatientProfileId()); }
        catch (PatientAuthorizationDeniedException denied) { allowed = false; }
        if (!allowed) throw error("MSG-50");
    }

    private static BigDecimal lineTotal(InvoiceItemJpaEntity item) {
        return item.getUnitPriceSnapshot().multiply(BigDecimal.valueOf(item.getQuantity()));
    }

    private static Pageable pageOf(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) throw error("MSG-39");
        return PageRequest.of(page, size);
    }

    private static void requireActor(AuthenticatedAccount actor) {
        if (actor == null || actor.accountId() == null || actor.accountRole() == null) throw error("ACCESS_DENIED");
    }

    private static void requireStaff(AuthenticatedAccount actor) {
        requireActor(actor);
        if (!STAFF.contains(actor.accountRole())) throw error("ACCESS_DENIED");
    }

    private static BillingException error(String code) { return new BillingException(code); }
}
