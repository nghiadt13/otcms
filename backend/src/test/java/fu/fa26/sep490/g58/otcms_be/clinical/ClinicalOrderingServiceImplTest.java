package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.model.*;
import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.impl.ClinicalOrderingServiceImpl;
import fu.fa26.sep490.g58.otcms_be.clinical.service.impl.ClinicalOrderingTransactionService;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class ClinicalOrderingServiceImplTest {
    private final VisitClinicalContextPort visits = mock(VisitClinicalContextPort.class);
    private final ClinicServiceCatalogPort catalog = mock(ClinicServiceCatalogPort.class);
    private final OrderingRoundRepository rounds = mock(OrderingRoundRepository.class);
    private final ServiceOrderRepository orders = mock(ServiceOrderRepository.class);
    private final UUID visitId = UUID.randomUUID();
    private final UUID patientId = UUID.randomUUID();
    private final UUID doctorId = UUID.randomUUID();
    private final UUID imagingId = UUID.randomUUID();
    private final UUID labId = UUID.randomUUID();
    private final ClinicalOrderingTransactionService writer = new ClinicalOrderingTransactionService(rounds, orders,
            Clock.fixed(Instant.parse("2026-11-04T10:00:00Z"), ZoneOffset.UTC));
    private final ClinicalOrderingServiceImpl service = new ClinicalOrderingServiceImpl(visits, catalog,
            writer, rounds, orders, Clock.fixed(Instant.parse("2026-11-04T10:00:00Z"), ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        when(visits.getVisitContext(visitId)).thenReturn(new VisitClinicalContextPort.ClinicalVisitContext(
                visitId, patientId, doctorId, ClinicalVisitStatus.IN_EXAMINATION));
        when(rounds.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(orders.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(catalog.getService(imagingId)).thenReturn(snapshot(imagingId, "X-Ray Knee", ClinicalServiceType.IMAGING,
                "1250.50", true));
        when(catalog.getService(labId)).thenReturn(snapshot(labId, "CBC", ClinicalServiceType.LABORATORY,
                "450.00", true));
        when(rounds.findTopByVisitIdOrderByRoundNumberDesc(visitId)).thenReturn(Optional.empty());
    }

    @Test
    void oneServiceCreatesOneRoundAndSnapshotsOrderFields() {
        var result = service.submit(command(request(imagingId, "Knee pain", "LEFT KNEE", "Weight-bearing")));
        assertEquals(1, result.round().roundNumber());
        assertTrue(result.requiresAwaitingConclusionTransition());
        var order = result.round().orders().getFirst();
        assertTrue(order.orderCode().length() <= 32);
        assertTrue(order.orderCode().startsWith("SO-"));
        assertEquals("X-Ray Knee", order.serviceName());
        assertEquals(ClinicalServiceType.IMAGING, order.serviceType());
        assertEquals(new BigDecimal("1250.50"), order.priceSnapshot());
        assertEquals(ServiceOrderStatus.ORDERED, order.orderStatus());
        assertEquals(ServiceOrderPaymentStatus.PENDING_PAYMENT, order.paymentStatus());
        verify(rounds).save(any());
        verify(orders).save(any());
        verify(visits, never()).markAwaitingConclusion(any(), any());
    }

    @Test
    void multipleOrdersBelongToOneRound() {
        var result = service.submit(command(request(imagingId, "Reason", "Knee", null),
                request(labId, "Reason", null, null)));
        assertEquals(1, result.round().roundNumber());
        assertEquals(2, result.round().orders().size());
        assertEquals(result.round().orders().get(0).orderingRoundId(), result.round().orders().get(1).orderingRoundId());
        verify(rounds, times(1)).save(any());
    }

    @Test
    void subsequentSubmitCreatesNextRoundAndPreservesPriorRound() {
        var previous = new OrderingRoundJpaEntity(visitId, patientId, 1, doctorId, Instant.now());
        when(rounds.findTopByVisitIdOrderByRoundNumberDesc(visitId)).thenReturn(Optional.of(previous));
        var result = service.submit(command(request(labId, "Reason", null, null)));
        assertEquals(2, result.round().roundNumber());
        verify(rounds, never()).delete(any());
        verify(rounds).save(any());
    }

    @Test
    void rejectsUnrelatedDoctor() {
        assertThrows(ClinicalOperationException.class,
                () -> service.submit(new ClinicalOrderingService.SubmitRound(visitId, UUID.randomUUID(),
                        List.of(request(labId, "Reason", null, null)))));
        verifyNoInteractions(catalog, rounds, orders);
    }

    @Test
    void rejectsInactiveService() {
        var inactive = UUID.randomUUID();
        when(catalog.getService(inactive)).thenReturn(snapshot(inactive, "Inactive", ClinicalServiceType.LABORATORY,
                "10.00", false));
        assertThrows(ClinicalOperationException.class,
                () -> service.submit(command(request(inactive, "Reason", null, null))));
        verify(rounds, never()).save(any());
    }

    @Test
    void validatesReasonAndImagingBodyAreaButNotLaboratoryBodyArea() {
        assertThrows(ClinicalOperationException.class,
                () -> service.submit(command(request(imagingId, "Reason", " ", null))));
        assertThrows(ClinicalOperationException.class,
                () -> service.submit(command(request(labId, " ", null, null))));
        var labRound = service.submit(command(request(labId, "Reason", null, null)));
        assertNull(labRound.round().orders().getFirst().bodyArea());
    }

    @Test
    void localPersistenceFailurePropagatesWithoutCallingVisitTransition() {
        when(orders.save(any())).thenThrow(new IllegalStateException("local insert failed"));
        assertThrows(IllegalStateException.class,
                () -> service.submit(command(request(labId, "Reason", null, null))));
        verify(visits, never()).markAwaitingConclusion(any(), any());
    }

    @Test
    void submitUsesLocalTransactionAndReturnsPostCommitIntent() throws Exception {
        var transactional = ClinicalOrderingTransactionService.class
                .getMethod("createRound", UUID.class, UUID.class, UUID.class, List.class)
                .getAnnotation(Transactional.class);
        assertNotNull(transactional);
        assertTrue(service.submit(command(request(labId, "Reason", null, null)))
                .requiresAwaitingConclusionTransition());
        verify(visits, never()).markAwaitingConclusion(any(), any());
    }

    @Test
    void cancelOrderedRequiresReasonAndDoesNotChangePaymentOrRefund() throws Exception {
        var order = persistedOrder(ClinicalServiceType.LABORATORY);
        setPaymentStatus(order, ServiceOrderPaymentStatus.PAID);
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));
        assertThrows(ClinicalOperationException.class, () -> service.cancel(order.getId(), " "));
        var result = service.cancel(order.getId(), "Duplicate request");
        assertEquals(ServiceOrderStatus.CANCELLED, result.orderStatus());
        assertEquals(ServiceOrderPaymentStatus.PAID, result.paymentStatus());
        assertEquals("Duplicate request", order.getCancelReason());
    }

    @Test
    void resultsAvailableOrderCannotBeCancelledAndDuplicateCancelIsRejected() {
        var resultOrder = persistedOrder(ClinicalServiceType.LABORATORY);
        resultOrder.markResult(Instant.now());
        when(orders.findById(resultOrder.getId())).thenReturn(Optional.of(resultOrder));
        assertThrows(ClinicalOperationException.class, () -> service.cancel(resultOrder.getId(), "reason"));

        var cancelled = persistedOrder(ClinicalServiceType.LABORATORY);
        cancelled.cancel("first", Instant.now());
        when(orders.findById(cancelled.getId())).thenReturn(Optional.of(cancelled));
        assertThrows(ClinicalOperationException.class, () -> service.cancel(cancelled.getId(), "again"));
    }

    @Test
    void imagingOrderProjectionIncludesContractFieldsAndRejectsOtherTypes() {
        var imaging = persistedOrder(ClinicalServiceType.IMAGING);
        when(orders.findById(imaging.getId())).thenReturn(Optional.of(imaging));
        var view = service.getImagingOrder(imaging.getId());
        assertEquals(imaging.getId(), view.orderId());
        assertEquals("X-Ray Knee", view.serviceName());
        assertEquals(imaging.getVisitId(), view.visitId());
        assertEquals(imaging.getPatientId(), view.patientId());
        assertEquals(imaging.getOrderingRoundId(), view.orderingRoundId());
        assertEquals(imaging.getServiceId(), view.serviceId());
        assertEquals(imaging.getRequestingDoctorId(), view.requestingDoctorId());
        assertEquals(imaging.getClinicalReason(), view.clinicalReason());
        assertEquals("LEFT KNEE", view.bodyArea());
        assertEquals("Weight-bearing", view.instructions());
        assertEquals(new BigDecimal("1250.50"), view.priceSnapshot());
        assertEquals(ServiceOrderStatus.ORDERED, view.orderStatus());
        assertEquals(ServiceOrderPaymentStatus.PENDING_PAYMENT, view.paymentStatus());

        var lab = persistedOrder(ClinicalServiceType.LABORATORY);
        when(orders.findById(lab.getId())).thenReturn(Optional.of(lab));
        assertThrows(ClinicalOperationException.class, () -> service.getImagingOrder(lab.getId()));
    }

    @Test
    void missingImagingOrderIsRejected() {
        assertThrows(ClinicalOperationException.class, () -> service.getImagingOrder(UUID.randomUUID()));
    }

    @Test
    void marksResultAvailableRegardlessOfPaymentAndDuplicateIsSafe() {
        var order = persistedOrder(ClinicalServiceType.IMAGING);
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));
        service.markImagingResultAvailable(order.getId());
        assertEquals(ServiceOrderStatus.RESULTS_AVAILABLE, order.getOrderStatus());
        assertEquals(ServiceOrderPaymentStatus.PENDING_PAYMENT, order.getPaymentStatus());
        service.markImagingResultAvailable(order.getId());
        verify(orders, times(2)).save(order);
    }

    @Test
    void rejectsResultForCancelledOrderAndCancelledImagingOrderQuery() {
        var order = persistedOrder(ClinicalServiceType.IMAGING);
        order.cancel("Cancelled", Instant.now());
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));
        assertThrows(ClinicalOperationException.class, () -> service.markImagingResultAvailable(order.getId()));
        assertThrows(ClinicalOperationException.class, () -> service.getImagingOrder(order.getId()));
    }

    @Test
    void readinessUsesOnlyLatestRoundAndIgnoresCancelledOrders() {
        var old = new OrderingRoundJpaEntity(visitId, patientId, 1, doctorId, Instant.now());
        var latest = new OrderingRoundJpaEntity(visitId, patientId, 2, doctorId, Instant.now());
        when(rounds.findTopByVisitIdOrderByRoundNumberDesc(visitId)).thenReturn(Optional.of(old));
        when(orders.findByOrderingRoundId(old.getId())).thenReturn(List.of(persistedOrderWithStatus(
                old.getId(), ServiceOrderStatus.RESULTS_AVAILABLE)));
        assertTrue(service.isLatestRoundReadyForReview(visitId));

        when(rounds.findTopByVisitIdOrderByRoundNumberDesc(visitId)).thenReturn(Optional.of(latest));
        var pending = persistedOrderWithStatus(latest.getId(), ServiceOrderStatus.ORDERED);
        when(orders.findByOrderingRoundId(latest.getId())).thenReturn(List.of(pending));
        assertFalse(service.isLatestRoundReadyForReview(visitId));

        var ready = persistedOrderWithStatus(latest.getId(), ServiceOrderStatus.RESULTS_AVAILABLE);
        var cancelled = persistedOrderWithStatus(latest.getId(), ServiceOrderStatus.CANCELLED);
        when(orders.findByOrderingRoundId(latest.getId())).thenReturn(List.of(ready, cancelled));
        assertTrue(service.isLatestRoundReadyForReview(visitId));
    }

    @Test
    void latestRoundMustHaveAtLeastOneEffectiveOrder() {
        var latest = new OrderingRoundJpaEntity(visitId, patientId, 1, doctorId, Instant.now());
        when(rounds.findTopByVisitIdOrderByRoundNumberDesc(visitId)).thenReturn(Optional.of(latest));
        when(orders.findByOrderingRoundId(latest.getId())).thenReturn(List.of(
                persistedOrderWithStatus(latest.getId(), ServiceOrderStatus.CANCELLED)));
        assertFalse(service.isLatestRoundReadyForReview(visitId));
        when(rounds.findTopByVisitIdOrderByRoundNumberDesc(visitId)).thenReturn(Optional.empty());
        assertFalse(service.isLatestRoundReadyForReview(visitId));
    }

    private ClinicalOrderingService.SubmitRound command(ClinicalOrderingService.OrderRequest... requests) {
        return new ClinicalOrderingService.SubmitRound(visitId, doctorId, List.of(requests));
    }

    private static ClinicalOrderingService.OrderRequest request(UUID id, String reason, String area, String instructions) {
        return new ClinicalOrderingService.OrderRequest(id, reason, area, instructions);
    }

    private static ClinicServiceCatalogPort.ClinicalServiceSnapshot snapshot(
            UUID id, String name, ClinicalServiceType type, String price, boolean active) {
        return new ClinicServiceCatalogPort.ClinicalServiceSnapshot(id, name, type, new BigDecimal(price), active);
    }

    private ServiceOrderJpaEntity persistedOrder(ClinicalServiceType type) {
        return new ServiceOrderJpaEntity("SO-" + UUID.randomUUID(), UUID.randomUUID(), visitId, patientId,
                type == ClinicalServiceType.IMAGING ? imagingId : labId,
                type == ClinicalServiceType.IMAGING ? "X-Ray Knee" : "CBC", type, doctorId,
                "Knee pain", type == ClinicalServiceType.IMAGING ? "LEFT KNEE" : null,
                type == ClinicalServiceType.IMAGING ? "Weight-bearing" : null,
                type == ClinicalServiceType.IMAGING ? new BigDecimal("1250.50") : new BigDecimal("450.00"),
                Instant.parse("2026-11-04T10:00:00Z"));
    }

    private ServiceOrderJpaEntity persistedOrderWithStatus(UUID roundId, ServiceOrderStatus status) {
        var order = new ServiceOrderJpaEntity("SO-" + UUID.randomUUID(), roundId, visitId, patientId, labId,
                "CBC", ClinicalServiceType.LABORATORY, doctorId, "Reason", null, null,
                new BigDecimal("450.00"), Instant.now());
        if (status == ServiceOrderStatus.RESULTS_AVAILABLE) order.markResult(Instant.now());
        if (status == ServiceOrderStatus.CANCELLED) order.cancel("Cancelled", Instant.now());
        return order;
    }

    private static void setPaymentStatus(ServiceOrderJpaEntity order, ServiceOrderPaymentStatus status) throws Exception {
        Field field = ServiceOrderJpaEntity.class.getDeclaredField("paymentStatus");
        field.setAccessible(true);
        field.set(order, status);
    }
}
