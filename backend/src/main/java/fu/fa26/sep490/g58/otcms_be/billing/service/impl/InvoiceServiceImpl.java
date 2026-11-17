package fu.fa26.sep490.g58.otcms_be.billing.service.impl;

import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentQueryException;
import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentBillingSourceService;
import fu.fa26.sep490.g58.otcms_be.billing.exception.BillingException;
import fu.fa26.sep490.g58.otcms_be.billing.mapper.BillingMapper;
import fu.fa26.sep490.g58.otcms_be.billing.model.InvoiceItemStatus;
import fu.fa26.sep490.g58.otcms_be.billing.model.InvoiceStatus;
import fu.fa26.sep490.g58.otcms_be.billing.model.PaymentAttemptStatus;
import fu.fa26.sep490.g58.otcms_be.billing.model.dto.request.CreateVisitInvoiceRequest;
import fu.fa26.sep490.g58.otcms_be.billing.model.dto.response.InvoicePageResponse;
import fu.fa26.sep490.g58.otcms_be.billing.model.dto.response.InvoiceResponse;
import fu.fa26.sep490.g58.otcms_be.billing.model.entity.InvoiceItemJpaEntity;
import fu.fa26.sep490.g58.otcms_be.billing.model.entity.InvoiceJpaEntity;
import fu.fa26.sep490.g58.otcms_be.billing.model.entity.PaymentAttemptJpaEntity;
import fu.fa26.sep490.g58.otcms_be.billing.repository.InvoiceItemRepository;
import fu.fa26.sep490.g58.otcms_be.billing.repository.InvoiceRepository;
import fu.fa26.sep490.g58.otcms_be.billing.repository.PaymentAttemptRepository;
import fu.fa26.sep490.g58.otcms_be.billing.service.InvoiceService;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.patient.exception.PatientAuthorizationDeniedException;
import fu.fa26.sep490.g58.otcms_be.patient.service.AuthorizePatientBillingProfileService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.ClinicServiceBookingQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitBillingSourceService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class InvoiceServiceImpl implements InvoiceService {
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int LIST_SCAN_BATCH = 50;
    private final fu.fa26.sep490.g58.otcms_be.billing.integration.ClinicalRoundBillingPort clinicalRounds;
    private final InvoiceRepository invoices;
    private final InvoiceItemRepository items;
    private final PaymentAttemptRepository payments;
    private final AppointmentBillingSourceService appointmentSources;
    private final VisitBillingSourceService visitSources;
    private final ClinicServiceBookingQueryService services;
    private final AuthorizePatientBillingProfileService patientAuthorization;
    private final BillingMapper mapper;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public InvoiceServiceImpl(InvoiceRepository invoices, InvoiceItemRepository items,
            PaymentAttemptRepository payments, AppointmentBillingSourceService appointmentSources,
            VisitBillingSourceService visitSources, ClinicServiceBookingQueryService services,
            AuthorizePatientBillingProfileService patientAuthorization, BillingMapper mapper,
            PlatformTransactionManager transactionManager, Clock clock,
            fu.fa26.sep490.g58.otcms_be.billing.integration.ClinicalRoundBillingPort clinicalRounds) {
        this.clinicalRounds = clinicalRounds;
        this.invoices = invoices;
        this.items = items;
        this.payments = payments;
        this.appointmentSources = appointmentSources;
        this.visitSources = visitSources;
        this.services = services;
        this.patientAuthorization = patientAuthorization;
        this.mapper = mapper;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Override
    public InvoiceResponse createForClinicalRound(UUID roundId, AuthenticatedAccount actor) {
        requireReceptionist(actor);
        var prior = invoices.findFirstByClinicalOrderingRoundId(roundId);
        if (prior.isPresent()) return existing(prior.get());
        try {
            return transactions.execute(status -> {
                var source = clinicalRounds.requirePayableRound(roundId);
                var existing = invoices.findFirstByClinicalOrderingRoundId(roundId);
                if (existing.isPresent()) return existing(existing.get());
                var resolved = source.orders().stream().map(order -> new ResolvedItem(order.serviceId(),
                        order.serviceNameSnapshot(), 1, order.priceSnapshot())).toList();
                UUID id = UUID.randomUUID();
                var invoice = InvoiceJpaEntity.clinicalRound(id, invoiceNumber(id), source.patientId(),
                        roundId, actor.accountId(), fingerprint("CLINICAL_ROUND", roundId, resolved));
                invoices.saveAndFlush(invoice);
                var invoiceItems = createItems(id, resolved);
                for (int i = 0; i < invoiceItems.size(); i++) {
                    invoiceItems.get(i).setServiceOrderId(source.orders().get(i).orderId());
                }
                items.saveAll(invoiceItems);
                items.flush();
                return response(id);
            });
        } catch (DataIntegrityViolationException conflict) {
            return invoices.findFirstByClinicalOrderingRoundId(roundId).map(this::existing)
                    .orElseThrow(() -> conflict);
        }
    }

    @Override
    public InvoiceResponse createForVisit(UUID visitId, CreateVisitInvoiceRequest request, AuthenticatedAccount actor) {
        requireReceptionist(actor);
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new BillingException("SOURCE_NOT_BILLABLE");
        }
        // One invoice per visit: an existing one is returned as it is, so a retry after the walk-in fee was paid is safe.
        var existing = invoices.findFirstByVisitId(visitId);
        if (existing.isPresent()) return existing(existing.get());
        VisitBillingSourceService.BillingSource source;
        try {
            source = visitSources.requireBillableVisit(visitId);
        } catch (VisitOperationException exception) {
            throw new BillingException("SOURCE_NOT_BILLABLE", exception);
        }
        List<ResolvedItem> resolved = resolveItems(request.items());
        // A pending walk-in fee is invoiced as exactly one line: its fee service, quantity 1.
        if (source.requiredServiceId() != null && !isSingleFeeLine(resolved, source.requiredServiceId())) {
            throw new BillingException("SOURCE_NOT_BILLABLE");
        }
        String fingerprint = fingerprint("VISIT", visitId, resolved);
        UUID invoiceId = UUID.randomUUID();
        InvoiceJpaEntity invoice = InvoiceJpaEntity.visit(invoiceId, invoiceNumber(invoiceId),
                source.patientProfileId(), visitId, actor.accountId(), fingerprint);
        try {
            transactions.executeWithoutResult(status -> {
                invoices.saveAndFlush(invoice);
                items.saveAll(createItems(invoiceId, resolved));
                items.flush();
            });
        } catch (DataIntegrityViolationException exception) {
            return invoices.findFirstByVisitId(visitId).map(this::existing)
                    .orElseThrow(() -> exception);
        }
        return response(invoiceId);
    }

    @Override
    public InvoiceResponse createForAppointment(UUID appointmentId, AuthenticatedAccount actor) {
        if (actor == null || (actor.accountRole() != AccountRole.PATIENT
                && actor.accountRole() != AccountRole.RECEPTIONIST)) {
            throw new BillingException("ACCESS_DENIED");
        }
        AppointmentBillingSourceService.BillingSource source;
        try {
            source = appointmentSources.requireSource(appointmentId);
        } catch (AppointmentQueryException exception) {
            throw new BillingException("SOURCE_NOT_BILLABLE", exception);
        }
        if (actor.accountRole() == AccountRole.PATIENT) {
            authorizePatient(actor.accountId(), source.patientProfileId());
        }
        var existing = invoices.findFirstByAppointmentId(appointmentId);
        if (existing.isPresent()) return existing(existing.get());
        if (!isBillable(source)) throw new BillingException("SOURCE_NOT_BILLABLE");
        return createAppointmentInvoice(source, actor.accountId());
    }

    @Override
    public void openForBooking(UUID appointmentId, UUID createdByAccountId) {
        AppointmentBillingSourceService.BillingSource source;
        try {
            source = appointmentSources.requireSource(appointmentId);
        } catch (AppointmentQueryException missing) {
            return;
        }
        if (!isBillable(source) || invoices.findFirstByAppointmentId(appointmentId).isPresent()) return;
        createAppointmentInvoice(source, createdByAccountId);
    }

    /** CONFIRMED appointment of a service with a whole-VND price: the exam fee is one invoice item. */
    private static boolean isBillable(AppointmentBillingSourceService.BillingSource source) {
        return "CONFIRMED".equals(source.status()) && source.servicePrice() != null
                && source.servicePrice().signum() > 0 && source.serviceName() != null
                && !source.serviceName().isBlank() && source.servicePrice().stripTrailingZeros().scale() <= 0;
    }

    private InvoiceResponse createAppointmentInvoice(AppointmentBillingSourceService.BillingSource source,
            UUID createdByAccountId) {
        UUID appointmentId = source.appointmentId();
        List<ResolvedItem> resolved = List.of(new ResolvedItem(source.serviceId(), source.serviceName(), 1,
                source.servicePrice().setScale(2, java.math.RoundingMode.UNNECESSARY)));
        String fingerprint = fingerprint("APPOINTMENT", appointmentId, resolved);
        UUID invoiceId = UUID.randomUUID();
        InvoiceJpaEntity invoice = InvoiceJpaEntity.appointment(invoiceId, invoiceNumber(invoiceId),
                source.patientProfileId(), appointmentId, createdByAccountId, fingerprint);
        try {
            transactions.executeWithoutResult(status -> {
                invoices.saveAndFlush(invoice);
                items.saveAll(createItems(invoiceId, resolved));
                items.flush();
            });
        } catch (DataIntegrityViolationException exception) {
            return invoices.findFirstByAppointmentId(appointmentId).map(this::existing)
                    .orElseThrow(() -> exception);
        }
        return response(invoiceId);
    }

    @Override
    public InvoiceResponse get(UUID invoiceId, AuthenticatedAccount actor) {
        InvoiceJpaEntity invoice = invoices.findById(invoiceId)
                .orElseThrow(() -> new BillingException("INVOICE_NOT_FOUND"));
        authorizeInvoice(invoice, actor);
        return toResponse(invoice);
    }

    @Override
    public InvoicePageResponse listMine(AuthenticatedAccount actor, int page, int size) {
        if (actor == null || actor.accountRole() != AccountRole.PATIENT || page < 0 || size < 1 || size > 50) {
            throw new BillingException("ACCESS_DENIED");
        }
        List<UUID> profileIds = patientAuthorization.accessibleProfileIds(actor.accountId());
        // VOID invoices are excluded by the query. Pages are cut AFTER filtering, so offsets and hasMore stay exact:
        // collect visible invoices (one more than the requested page needs) scanning the DB in stable batches.
        int skip = page * size;
        int needed = skip + size + 1;
        Sort order = Sort.by(Sort.Direction.DESC, "issuedAt").and(Sort.by("id"));
        List<InvoiceJpaEntity> visible = new ArrayList<>();
        int batchIndex = 0;
        while (visible.size() < needed) {
            var batch = invoices.findAllByPatientProfileIdInAndStatusNot(profileIds, InvoiceStatus.VOID,
                    PageRequest.of(batchIndex++, LIST_SCAN_BATCH, order));
            visible.addAll(batch.getContent());
            if (!batch.hasNext()) break;
        }
        List<InvoiceResponse> pageItems = visible.stream().skip(skip).limit(size)
                .map(this::toResponse)
                .toList();
        return new InvoicePageResponse(page, size, visible.size() > skip + size, pageItems);
    }

    private List<ResolvedItem> resolveItems(List<CreateVisitInvoiceRequest.Item> requested) {
        HashSet<UUID> seen = new HashSet<>();
        List<ResolvedItem> resolved = new ArrayList<>();
        for (CreateVisitInvoiceRequest.Item item : requested) {
            if (item == null || item.serviceId() == null || item.quantity() < 1 || item.quantity() > 100
                    || !seen.add(item.serviceId())) throw new BillingException("SOURCE_NOT_BILLABLE");
            var label = services.findServiceLabel(item.serviceId())
                    .orElseThrow(() -> new BillingException("SOURCE_NOT_BILLABLE"));
            if (label.name() == null || label.name().isBlank() || label.price() == null || label.price().signum() <= 0
                    || label.price().stripTrailingZeros().scale() > 0) {
                throw new BillingException("SOURCE_NOT_BILLABLE");
            }
            resolved.add(new ResolvedItem(item.serviceId(), label.name(), item.quantity(),
                    label.price().setScale(2, java.math.RoundingMode.UNNECESSARY)));
        }
        return List.copyOf(resolved);
    }

    private static boolean isSingleFeeLine(List<ResolvedItem> resolved, UUID feeServiceId) {
        return resolved.size() == 1 && resolved.get(0).serviceId().equals(feeServiceId)
                && resolved.get(0).quantity() == 1;
    }

    private List<InvoiceItemJpaEntity> createItems(UUID invoiceId, List<ResolvedItem> resolved) {
        List<InvoiceItemJpaEntity> result = new ArrayList<>();
        for (int i = 0; i < resolved.size(); i++) {
            ResolvedItem item = resolved.get(i);
            result.add(new InvoiceItemJpaEntity(invoiceId, i + 1, item.serviceId(), item.description(),
                    item.quantity(), item.unitPrice()));
        }
        return result;
    }

    private InvoiceResponse existing(InvoiceJpaEntity invoice) {
        return toResponse(invoice);
    }

    /** Invoice with the derived status of every item (read from the attempts the items point at). */
    private InvoiceResponse toResponse(InvoiceJpaEntity invoice) {
        List<InvoiceItemJpaEntity> lines = items.findAllByInvoiceIdOrderByLineNumber(invoice.getId());
        var attemptIds = lines.stream().map(InvoiceItemJpaEntity::getPaymentAttemptId)
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        java.util.Map<UUID, PaymentAttemptJpaEntity> attemptsById = new java.util.HashMap<>();
        if (!attemptIds.isEmpty()) payments.findAllById(attemptIds).forEach(attempt -> attemptsById.put(attempt.getId(), attempt));
        Instant now = Instant.now(clock);
        java.util.Map<UUID, InvoiceItemStatus> statuses = new java.util.HashMap<>();
        lines.forEach(line -> statuses.put(line.getId(),
                InvoiceItemStatus.resolve(line, attemptsById.get(line.getPaymentAttemptId()), now)));
        return mapper.toInvoice(invoice, lines, payments.sumByInvoiceAndStatus(invoice.getId(), PaymentAttemptStatus.CONFIRMED), statuses);
    }

    private InvoiceResponse response(UUID invoiceId) {
        return invoices.findById(invoiceId).map(this::toResponse)
                .orElseThrow(() -> new BillingException("INVOICE_NOT_FOUND"));
    }

    private void authorizeInvoice(InvoiceJpaEntity invoice, AuthenticatedAccount actor) {
        if (actor == null) throw new BillingException("ACCESS_DENIED");
        if (actor.accountRole() == AccountRole.PATIENT) {
            authorizePatient(actor.accountId(), invoice.getPatientProfileId());
        } else if (actor.accountRole() != AccountRole.RECEPTIONIST && actor.accountRole() != AccountRole.ADMIN
                && actor.accountRole() != AccountRole.OWNER) {
            throw new BillingException("ACCESS_DENIED");
        }
    }

    private void authorizePatient(UUID accountId, UUID profileId) {
        try {
            patientAuthorization.authorize(accountId, profileId);
        } catch (PatientAuthorizationDeniedException exception) {
            throw new BillingException("ACCESS_DENIED", exception);
        }
    }

    private static void requireReceptionist(AuthenticatedAccount actor) {
        if (actor == null || actor.accountRole() != AccountRole.RECEPTIONIST) {
            throw new BillingException("ACCESS_DENIED");
        }
    }

    private String invoiceNumber(UUID id) {
        String date = LocalDate.now(clock.withZone(CLINIC_ZONE)).toString().replace("-", "");
        return "OTC-" + date + "-" + id.toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT);
    }

    private static String fingerprint(String kind, UUID sourceId, List<ResolvedItem> items) {
        String canonical = kind + "|" + sourceId + "|" + items.stream()
                .map(item -> item.serviceId() + ":" + item.quantity() + ":" + item.unitPrice().toPlainString())
                .reduce((a, b) -> a + "|" + b).orElse("");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    private record ResolvedItem(UUID serviceId, String description, int quantity, BigDecimal unitPrice) { }
}
