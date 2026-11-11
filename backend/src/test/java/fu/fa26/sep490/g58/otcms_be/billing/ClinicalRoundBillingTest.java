package fu.fa26.sep490.g58.otcms_be.billing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import fu.fa26.sep490.g58.otcms_be.billing.exception.BillingException;
import fu.fa26.sep490.g58.otcms_be.billing.integration.AppointmentPaymentPort;
import fu.fa26.sep490.g58.otcms_be.billing.integration.ClinicalRoundBillingPort;
import fu.fa26.sep490.g58.otcms_be.billing.integration.VisitFeeSettlementPort;
import fu.fa26.sep490.g58.otcms_be.billing.mapper.BillingMapper;
import fu.fa26.sep490.g58.otcms_be.billing.model.*;
import fu.fa26.sep490.g58.otcms_be.billing.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.billing.model.dto.request.CounterPaymentRequest;
import fu.fa26.sep490.g58.otcms_be.billing.repository.*;
import fu.fa26.sep490.g58.otcms_be.billing.service.*;
import fu.fa26.sep490.g58.otcms_be.billing.service.impl.*;
import fu.fa26.sep490.g58.otcms_be.common.security.*;
import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentBillingSourceService;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitBillingSourceService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.ClinicServiceBookingQueryService;
import fu.fa26.sep490.g58.otcms_be.patient.service.AuthorizePatientBillingProfileService;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import fu.fa26.sep490.g58.otcms_be.clinical.integration.billing.ClinicalRoundBillingAdapter;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalServiceType;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ServiceOrderPaymentStatus;
import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.ServiceOrderJpaEntity;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.OrderingRoundRepository;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.ServiceOrderRepository;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class ClinicalRoundBillingTest {
    final InvoiceRepository invoices = mock(InvoiceRepository.class);
    final InvoiceItemRepository items = mock(InvoiceItemRepository.class);
    final PaymentAttemptRepository attempts = mock(PaymentAttemptRepository.class);
    final PaymentGatewayEventRepository events = mock(PaymentGatewayEventRepository.class);
    final ClinicalRoundBillingPort clinical = mock(ClinicalRoundBillingPort.class);
    final VisitBillingSourceService visits = mock(VisitBillingSourceService.class);
    final ClinicServiceBookingQueryService catalog = mock(ClinicServiceBookingQueryService.class);
    final AuthorizePatientBillingProfileService authorization = mock(AuthorizePatientBillingProfileService.class);
    final PaymentGateway gateway = mock(PaymentGateway.class);
    final PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
    final UUID roundId = UUID.randomUUID(), visitId = UUID.randomUUID(), patient = UUID.randomUUID(), serviceId = UUID.randomUUID();
    final AuthenticatedAccount receptionist = new AuthenticatedAccount(UUID.randomUUID(), AccountRole.RECEPTIONIST, UUID.randomUUID());
    final Clock clock = Clock.fixed(Instant.parse("2026-11-06T12:00:00Z"), ZoneOffset.UTC);
    final Map<UUID, InvoiceJpaEntity> invoiceRows = new HashMap<>();
    final Map<UUID, List<InvoiceItemJpaEntity>> itemRows = new HashMap<>();
    final Map<UUID, PaymentAttemptJpaEntity> paymentRows = new HashMap<>();
    final AppointmentPaymentPort appointmentPort = mock(AppointmentPaymentPort.class);
    InvoiceService invoiceService; PaymentService paymentService;
    InvoiceSettlementSupport settlementSupport(ClinicalRoundBillingPort clinicalPort) {
        return new InvoiceSettlementSupport(invoices, items, attempts, clinicalPort, appointmentPort,
                mock(VisitFeeSettlementPort.class), mock(jakarta.persistence.EntityManager.class));
    }
    @BeforeEach void setup() {
        when(tx.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        when(gateway.provider()).thenReturn("PAYOS");
        when(appointmentPort.confirmPaid(any())).thenReturn(AppointmentPaymentPort.ConfirmResult.ALREADY_CONFIRMED);
        var support = settlementSupport(clinical);
        invoiceService = new InvoiceServiceImpl(invoices, items, attempts, mock(AppointmentBillingSourceService.class),
                visits, catalog, authorization, new BillingMapper(), tx, clock, clinical, appointmentPort,
                new AppointmentInvoiceVoider(invoices, attempts, support, tx, clock));
        paymentService = new PaymentServiceImpl(invoices, attempts, events, new PaymentGatewayRegistry(List.of(gateway)),
                authorization, new BillingMapper(), tx, clock, support, appointmentPort);
        when(clinical.requirePayableRound(roundId)).thenReturn(new ClinicalRoundBillingPort.ClinicalRoundBillingSource(roundId, visitId, patient,
                List.of(new ClinicalRoundBillingPort.PayableOrder(UUID.randomUUID(), serviceId, "Snapshot X-ray", new BigDecimal("150000")),
                        new ClinicalRoundBillingPort.PayableOrder(UUID.randomUUID(), serviceId, "Snapshot MRI", new BigDecimal("350000")))));
        when(invoices.findFirstByClinicalOrderingRoundId(roundId)).thenAnswer(call -> invoiceRows.values().stream().findFirst());
        when(invoices.saveAndFlush(any())).thenAnswer(call -> { InvoiceJpaEntity row = call.getArgument(0); invoiceRows.put(row.getId(), row); return row; });
        when(invoices.findById(any())).thenAnswer(call -> Optional.ofNullable(invoiceRows.get(call.getArgument(0))));
        when(invoices.findByIdForUpdate(any())).thenAnswer(call -> Optional.ofNullable(invoiceRows.get(call.getArgument(0))));
        when(items.saveAll(any())).thenAnswer(call -> { List<InvoiceItemJpaEntity> rows = call.getArgument(0); itemRows.put(rows.getFirst().getInvoiceId(), rows); return rows; });
        when(items.findAllByInvoiceIdOrderByLineNumber(any())).thenAnswer(call -> itemRows.getOrDefault(call.getArgument(0), List.of()));
        when(attempts.saveAndFlush(any())).thenAnswer(call -> { PaymentAttemptJpaEntity row = call.getArgument(0); paymentRows.put(row.getId(), row); return row; });
        when(attempts.save(any())).thenAnswer(call -> call.getArgument(0));
        when(attempts.findById(any())).thenAnswer(call -> Optional.ofNullable(paymentRows.get(call.getArgument(0))));
        when(attempts.findFirstByProviderOrderCode(anyLong())).thenAnswer(call -> paymentRows.values().stream().filter(row -> Objects.equals(row.getProviderOrderCode(), call.getArgument(0))).findFirst());
        when(attempts.findFirstByCreatedByAccountIdAndIdempotencyKey(any(), any())).thenAnswer(call -> paymentRows.values().stream()
                .filter(row -> row.getCreatedByAccountId().equals(call.getArgument(0)) && row.getIdempotencyKey().equals(call.getArgument(1))).findFirst());
        when(attempts.sumByInvoiceAndStatus(any(), eq(PaymentAttemptStatus.CONFIRMED))).thenAnswer(call -> paymentRows.values().stream()
                .filter(row -> row.getInvoiceId().equals(call.getArgument(0)) && row.getStatus() == PaymentAttemptStatus.CONFIRMED)
                .map(PaymentAttemptJpaEntity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
    }
    @Test void receptionCreatesSnapshotInvoiceWithoutRequiringClosedVisitOrCatalogAndReusesIt() {
        var invoice = invoiceService.createForClinicalRound(roundId, receptionist);
        assertEquals(patient, invoice.patientProfileId()); assertNull(invoice.visitId()); assertNull(invoice.appointmentId());
        assertEquals("CLINICAL_ROUND", invoice.sourceType()); assertEquals(roundId, invoice.clinicalOrderingRoundId());
        assertEquals(0, new BigDecimal("500000").compareTo(invoice.totalAmount()));
        assertEquals(2, invoice.items().size()); assertTrue(invoice.items().stream().allMatch(item -> item.quantity() == 1));
        assertEquals("Snapshot X-ray", invoice.items().getFirst().description());
        assertEquals(invoice.id(), invoiceService.createForClinicalRound(roundId, receptionist).id());
        verifyNoInteractions(visits, catalog); verify(invoices, times(1)).saveAndFlush(any());
    }
    @Test void everyNonReceptionistRoleIsDeniedBeforeReadingClinicalSource() {
        for (var role : AccountRole.values()) if (role != AccountRole.RECEPTIONIST)
            assertThrows(BillingException.class, () -> invoiceService.createForClinicalRound(roundId, new AuthenticatedAccount(UUID.randomUUID(), role, UUID.randomUUID())));
        verifyNoInteractions(clinical);
    }
    @Test void partialCounterPaymentLeavesOrdersPendingAndFullPaymentPropagatesOnlyOnceAcrossRetry() {
        UUID id = invoiceService.createForClinicalRound(roundId, receptionist).id();
        paymentService.recordCounterPayment(id, new CounterPaymentRequest(200000L, "CASH"), receptionist, "partial");
        assertEquals(InvoiceStatus.OPEN, invoiceRows.get(id).getStatus()); verify(clinical, never()).markRoundPaid(any());
        var full = paymentService.recordCounterPayment(id, new CounterPaymentRequest(300000L, "CASH"), receptionist, "remaining");
        assertEquals(InvoiceStatus.PAID, invoiceRows.get(id).getStatus());
        assertEquals(full.id(), paymentService.recordCounterPayment(id, new CounterPaymentRequest(300000L, "CASH"), receptionist, "remaining").id());
        verify(clinical, times(1)).markRoundPaid(roundId);
    }
    PaymentAttemptJpaEntity online(UUID invoiceId) {
        var row = PaymentAttemptJpaEntity.pendingOnline(UUID.randomUUID(), invoiceId, new BigDecimal("500000"), receptionist.accountId(), "online", "hash", 123L);
        paymentRows.put(row.getId(), row); return row;
    }
    @Test void successfulGatewaySettlementMarksRoundPaidAndRepeatedEventDoesNotRepeatSettlement() {
        UUID id = invoiceService.createForClinicalRound(roundId, receptionist).id(); online(id);
        var event = new PaymentGateway.VerifiedGatewayEvent("PAYOS", 123L, 500000L, "VND", null, "transaction", "00", true);
        paymentService.handleVerifiedGatewayEvent(event); paymentService.handleVerifiedGatewayEvent(event);
        assertEquals(InvoiceStatus.PAID, invoiceRows.get(id).getStatus()); verify(clinical, times(1)).markRoundPaid(roundId);
    }
    @Test void successfulReconciliationUsesSameSettlementPath() {
        UUID id = invoiceService.createForClinicalRound(roundId, receptionist).id(); var payment = online(id);
        when(gateway.query(123L)).thenReturn(new PaymentGateway.GatewayPaymentStatus("PAID", 123L, 500000L, 500000L, null, "transaction", "VND"));
        paymentService.reconcile(payment.getId(), receptionist); paymentService.reconcile(payment.getId(), receptionist);
        assertEquals(InvoiceStatus.PAID, invoiceRows.get(id).getStatus()); verify(clinical, times(1)).markRoundPaid(roundId);
    }
    /** Persistence probe distinguishes managed mutations from writes flushed by the owning service. */
    private final class ClinicalSettlementOwner {
        final OrderingRoundRepository rounds = mock(OrderingRoundRepository.class);
        final ServiceOrderRepository orders = mock(ServiceOrderRepository.class);
        final CoordinationGuardService guard = mock(CoordinationGuardService.class);
        final ServiceOrderJpaEntity first = order("FIRST", "150000");
        final ServiceOrderJpaEntity second = order("SECOND", "350000");
        final ServiceOrderJpaEntity cancelled = order("CANCELLED", "990000");
        final List<ServiceOrderJpaEntity> rows = List.of(first, second, cancelled);
        final Map<UUID, ServiceOrderPaymentStatus> flushedPayments = new HashMap<>();
        final ClinicalRoundBillingAdapter adapter = new ClinicalRoundBillingAdapter(rounds, orders, guard, clock);

        ClinicalSettlementOwner() {
            cancelled.cancel("Declined", clock.instant());
            rows.forEach(row -> flushedPayments.put(row.getId(), row.getPaymentStatus()));
            when(rounds.existsById(roundId)).thenReturn(true);
            when(orders.findByOrderingRoundId(roundId)).thenReturn(rows);
            when(orders.saveAllAndFlush(anyList())).thenAnswer(call -> {
                List<ServiceOrderJpaEntity> submitted = call.getArgument(0);
                submitted.forEach(row -> flushedPayments.put(row.getId(), row.getPaymentStatus()));
                return submitted;
            });
            paymentService = new PaymentServiceImpl(invoices, attempts, events, new PaymentGatewayRegistry(List.of(gateway)),
                    authorization, new BillingMapper(), tx, clock, settlementSupport(adapter), appointmentPort);
        }
        ServiceOrderJpaEntity order(String code, String price) {
            return new ServiceOrderJpaEntity(code, roundId, visitId, patient, serviceId, "Snapshot " + code,
                    ClinicalServiceType.IMAGING, UUID.randomUUID(), "Reason", "KNEE", null, new BigDecimal(price),
                    clock.instant().minusSeconds(60));
        }
        void assertPaidWasPersisted() {
            assertEquals(ServiceOrderPaymentStatus.PAID, flushedPayments.get(first.getId()));
            assertEquals(ServiceOrderPaymentStatus.PAID, flushedPayments.get(second.getId()));
            assertEquals(ServiceOrderPaymentStatus.PENDING_PAYMENT, flushedPayments.get(cancelled.getId()));
            assertEquals(ServiceOrderPaymentStatus.PENDING_PAYMENT, cancelled.getPaymentStatus());
            verify(orders).findByOrderingRoundId(roundId);
            verify(orders).saveAllAndFlush(List.of(first, second));
            verify(orders, never()).saveAll(any());
        }
    }

    @Test void fullCounterSettlementFlushesEffectiveClinicalOrdersBeforeReturning() {
        UUID id = invoiceService.createForClinicalRound(roundId, receptionist).id();
        var owner = new ClinicalSettlementOwner();
        assertTrue(owner.rows.stream().allMatch(row -> row.getPaymentStatus() == ServiceOrderPaymentStatus.PENDING_PAYMENT));
        assertEquals(roundId, invoiceRows.get(id).getClinicalOrderingRoundId());
        paymentService.recordCounterPayment(id, new CounterPaymentRequest(200000L, "CASH"), receptionist, "partial-owner");
        assertEquals(InvoiceStatus.OPEN, invoiceRows.get(id).getStatus());
        verifyNoInteractions(owner.rounds, owner.orders, owner.guard);
        assertTrue(owner.flushedPayments.values().stream().allMatch(status -> status == ServiceOrderPaymentStatus.PENDING_PAYMENT));
        paymentService.recordCounterPayment(id, new CounterPaymentRequest(300000L, "CASH"), receptionist, "full-owner");
        assertEquals(InvoiceStatus.PAID, invoiceRows.get(id).getStatus());
        owner.assertPaidWasPersisted();
    }

    @Test void verifiedWebhookFlushesThroughRealClinicalSettlementOwner() {
        UUID id = invoiceService.createForClinicalRound(roundId, receptionist).id(); online(id);
        var owner = new ClinicalSettlementOwner();
        var event = new PaymentGateway.VerifiedGatewayEvent("PAYOS", 123L, 500000L, "VND", null, "transaction", "00", true);
        paymentService.handleVerifiedGatewayEvent(event);
        paymentService.handleVerifiedGatewayEvent(event);
        assertEquals(InvoiceStatus.PAID, invoiceRows.get(id).getStatus());
        owner.assertPaidWasPersisted();
    }

    @Test void reconciliationFlushesThroughRealClinicalSettlementOwner() {
        UUID id = invoiceService.createForClinicalRound(roundId, receptionist).id(); var payment = online(id);
        var owner = new ClinicalSettlementOwner();
        when(gateway.query(123L)).thenReturn(new PaymentGateway.GatewayPaymentStatus("PAID", 123L, 500000L, 500000L, null, "transaction", "VND"));
        paymentService.reconcile(payment.getId(), receptionist);
        paymentService.reconcile(payment.getId(), receptionist);
        assertEquals(InvoiceStatus.PAID, invoiceRows.get(id).getStatus());
        owner.assertPaidWasPersisted();
    }

    @Test void repeatedOwnerPropagationKeepsPaidAndCancelledStateAndTimestampsUnchanged() {
        var owner = new ClinicalSettlementOwner();
        owner.adapter.markRoundPaid(roundId);
        Instant updated = owner.first.getUpdatedAt();
        Instant cancelledUpdated = owner.cancelled.getUpdatedAt();
        new ClinicalRoundBillingAdapter(owner.rounds, owner.orders, owner.guard, Clock.offset(clock, Duration.ofMinutes(1)))
                .markRoundPaid(roundId);
        assertEquals(updated, owner.first.getUpdatedAt());
        assertEquals(cancelledUpdated, owner.cancelled.getUpdatedAt());
        assertEquals(ServiceOrderPaymentStatus.PAID, owner.flushedPayments.get(owner.first.getId()));
        assertEquals(ServiceOrderPaymentStatus.PAID, owner.flushedPayments.get(owner.second.getId()));
        assertEquals(ServiceOrderPaymentStatus.PENDING_PAYMENT, owner.flushedPayments.get(owner.cancelled.getId()));
        verify(owner.orders, times(2)).saveAllAndFlush(List.of(owner.first, owner.second));
    }

    @ParameterizedTest
    @CsvSource({"VISIT,COUNTER", "APPOINTMENT,COUNTER", "VISIT,WEBHOOK", "APPOINTMENT,WEBHOOK", "VISIT,RECONCILE", "APPOINTMENT,RECONCILE"})
    void otherInvoiceSourcesNeverPropagateClinicalPayment(String source, String channel) {
        UUID id = UUID.randomUUID();
        var invoice = "VISIT".equals(source)
                ? InvoiceJpaEntity.visit(id, "VISIT-TEST", patient, visitId, receptionist.accountId(), "hash")
                : InvoiceJpaEntity.appointment(id, "APPOINTMENT-TEST", patient, UUID.randomUUID(), receptionist.accountId(), "hash");
        invoiceRows.put(id, invoice);
        itemRows.put(id, List.of(new InvoiceItemJpaEntity(id, 1, serviceId, "Snapshot", 1, new BigDecimal("500000"))));
        if ("COUNTER".equals(channel)) {
            paymentService.recordCounterPayment(id, new CounterPaymentRequest(500000L, "CASH"), receptionist, "other-full");
        } else {
            var payment = online(id);
            if ("WEBHOOK".equals(channel)) {
                paymentService.handleVerifiedGatewayEvent(new PaymentGateway.VerifiedGatewayEvent("PAYOS", 123L, 500000L, "VND", null, "transaction", "00", true));
            } else {
                when(gateway.query(123L)).thenReturn(new PaymentGateway.GatewayPaymentStatus("PAID", 123L, 500000L, 500000L, null, "transaction", "VND"));
                paymentService.reconcile(payment.getId(), receptionist);
            }
        }
        assertEquals(InvoiceStatus.PAID, invoice.getStatus());
        assertNull(invoice.getClinicalOrderingRoundId());
        verify(clinical, never()).markRoundPaid(any());
    }

}
