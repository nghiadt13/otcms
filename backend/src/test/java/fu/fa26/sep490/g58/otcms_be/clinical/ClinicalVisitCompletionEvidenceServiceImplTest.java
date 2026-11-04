package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalExaminationStatus;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalServiceType;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ServiceOrderPaymentStatus;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ServiceOrderStatus;
import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.ClinicalExaminationJpaEntity;
import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.ServiceOrderJpaEntity;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.ClinicalExaminationRepository;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.ServiceOrderRepository;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalVisitCompletionEvidenceService.ClinicalCompletionEvidence;
import fu.fa26.sep490.g58.otcms_be.clinical.service.impl.ClinicalVisitCompletionEvidenceServiceImpl;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClinicalVisitCompletionEvidenceServiceImplTest {
    private final ClinicalExaminationRepository examinations = mock(ClinicalExaminationRepository.class);
    private final ServiceOrderRepository orders = mock(ServiceOrderRepository.class);
    private final ClinicalVisitCompletionEvidenceServiceImpl service =
            new ClinicalVisitCompletionEvidenceServiceImpl(examinations, orders);
    private final UUID visitId = UUID.randomUUID();
    private final UUID firstRoundId = UUID.randomUUID();
    private final UUID secondRoundId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-11-04T10:00:00Z");

    @BeforeEach
    void setUp() {
        when(examinations.findByVisitId(visitId)).thenReturn(Optional.empty());
        when(orders.findByVisitId(visitId)).thenReturn(List.of());
    }

    @Test
    void noClinicalRecordsReturnEmptyEvidenceWithoutProvingVisitExistence() {
        var evidence = service.getCompletionEvidence(visitId);
        assertEquals(visitId, evidence.visitId());
        assertFalse(evidence.examinationExists());
        assertFalse(evidence.examinationCompleted());
        assertCounts(evidence, 0, 0, true);
        assertEquals(List.of(), evidence.imagingOrderIds());
    }

    @Test
    void draftExaminationExistsButIsNotCompletedEvenWithAdvice() {
        var draft = examination(ClinicalExaminationStatus.DRAFT);
        draft.updateAfterVisitAdvice("Home care", null, null, null, "Follow up", now);
        when(examinations.findByVisitId(visitId)).thenReturn(Optional.of(draft));
        var evidence = service.getCompletionEvidence(visitId);
        assertTrue(evidence.examinationExists());
        assertFalse(evidence.examinationCompleted());
    }

    @Test
    void completedStatusAloneEstablishesExaminationCompletion() {
        when(examinations.findByVisitId(visitId)).thenReturn(Optional.of(examination(ClinicalExaminationStatus.COMPLETED)));
        var evidence = service.getCompletionEvidence(visitId);
        assertTrue(evidence.examinationExists());
        assertTrue(evidence.examinationCompleted());
    }

    @Test
    void examinationWithoutOrdersHasNoOutstandingClinicalResults() {
        when(examinations.findByVisitId(visitId)).thenReturn(Optional.of(examination(ClinicalExaminationStatus.DRAFT)));
        assertCounts(service.getCompletionEvidence(visitId), 0, 0, true);
    }

    @Test
    void orderedOrderIsActiveAndOutstanding() {
        returnedOrders(order(ClinicalServiceType.LABORATORY, ServiceOrderStatus.ORDERED, firstRoundId, now));
        assertCounts(service.getCompletionEvidence(visitId), 1, 1, false);
    }

    @Test
    void availableResultsAreActiveWithoutOutstandingWork() {
        returnedOrders(order(ClinicalServiceType.LABORATORY, ServiceOrderStatus.RESULTS_AVAILABLE, firstRoundId, now));
        assertCounts(service.getCompletionEvidence(visitId), 1, 0, true);
    }

    @Test
    void cancelledOrderIsIgnored() {
        returnedOrders(order(ClinicalServiceType.IMAGING, ServiceOrderStatus.CANCELLED, firstRoundId, now));
        var evidence = service.getCompletionEvidence(visitId);
        assertCounts(evidence, 0, 0, true);
        assertTrue(evidence.imagingOrderIds().isEmpty());
    }

    @Test
    void mixedOrderStatusesCountOnlyNonCancelledOrders() {
        returnedOrders(order(ClinicalServiceType.LABORATORY, ServiceOrderStatus.RESULTS_AVAILABLE, firstRoundId, now),
                order(ClinicalServiceType.IMAGING, ServiceOrderStatus.ORDERED, firstRoundId, now),
                order(ClinicalServiceType.LABORATORY, ServiceOrderStatus.CANCELLED, firstRoundId, now));
        assertCounts(service.getCompletionEvidence(visitId), 2, 1, false);
    }

    @Test
    void outstandingEarlierRoundBlocksAllResultsEvenWhenLatestRoundHasResults() {
        returnedOrders(order(ClinicalServiceType.LABORATORY, ServiceOrderStatus.ORDERED, firstRoundId, now),
                order(ClinicalServiceType.IMAGING, ServiceOrderStatus.RESULTS_AVAILABLE, secondRoundId, now.plusSeconds(1)));
        assertCounts(service.getCompletionEvidence(visitId), 2, 1, false);
        verify(orders).findByVisitId(visitId);
        verifyNoMoreInteractions(orders);
    }

    @Test
    void outstandingLatestRoundAlsoBlocksAllResults() {
        returnedOrders(order(ClinicalServiceType.LABORATORY, ServiceOrderStatus.RESULTS_AVAILABLE, firstRoundId, now),
                order(ClinicalServiceType.IMAGING, ServiceOrderStatus.ORDERED, secondRoundId, now.plusSeconds(1)));
        assertCounts(service.getCompletionEvidence(visitId), 2, 1, false);
    }

    @Test
    void imagingIdsIncludeOrderedAndAvailableButExcludeCancelledLaboratoryAndOther() {
        var ordered = order(ClinicalServiceType.IMAGING, ServiceOrderStatus.ORDERED, firstRoundId, now);
        var available = order(ClinicalServiceType.IMAGING, ServiceOrderStatus.RESULTS_AVAILABLE, secondRoundId, now.plusSeconds(1));
        returnedOrders(ordered, available,
                order(ClinicalServiceType.IMAGING, ServiceOrderStatus.CANCELLED, firstRoundId, now),
                order(ClinicalServiceType.LABORATORY, ServiceOrderStatus.ORDERED, firstRoundId, now),
                order(ClinicalServiceType.OTHER, ServiceOrderStatus.RESULTS_AVAILABLE, firstRoundId, now));
        assertEquals(List.of(ordered.getId(), available.getId()), service.getCompletionEvidence(visitId).imagingOrderIds());
    }

    @Test
    void imagingIdsAreSortedByCreationTimeThenUuidRegardlessOfRepositoryOrder() {
        var earlier = order(ClinicalServiceType.IMAGING, ServiceOrderStatus.ORDERED, firstRoundId, now.minusSeconds(1));
        var tiedFirst = order(ClinicalServiceType.IMAGING, ServiceOrderStatus.ORDERED, firstRoundId, now);
        var tiedSecond = order(ClinicalServiceType.IMAGING, ServiceOrderStatus.RESULTS_AVAILABLE, secondRoundId, now);
        var tiedIds = List.of(tiedFirst.getId(), tiedSecond.getId()).stream()
                .sorted(java.util.Comparator.comparing(UUID::toString)).toList();
        returnedOrders(tiedSecond, earlier, tiedFirst);
        var expected = List.of(earlier.getId(), tiedIds.get(0), tiedIds.get(1));
        assertEquals(expected, service.getCompletionEvidence(visitId).imagingOrderIds());
        returnedOrders(tiedFirst, tiedSecond, earlier);
        assertEquals(expected, service.getCompletionEvidence(visitId).imagingOrderIds());
    }

    @Test
    void evidenceDoesNotMutateEntitiesOrRepositoryListAndReturnsImmutableIds() {
        var ordered = order(ClinicalServiceType.IMAGING, ServiceOrderStatus.ORDERED, firstRoundId, now);
        var available = order(ClinicalServiceType.IMAGING, ServiceOrderStatus.RESULTS_AVAILABLE, secondRoundId, now.plusSeconds(1));
        var repositoryList = new ArrayList<>(List.of(available, ordered));
        when(orders.findByVisitId(visitId)).thenReturn(repositoryList);
        var evidence = service.getCompletionEvidence(visitId);
        assertEquals(List.of(available, ordered), repositoryList);
        assertEquals(ServiceOrderStatus.ORDERED, ordered.getOrderStatus());
        assertEquals(ServiceOrderStatus.RESULTS_AVAILABLE, available.getOrderStatus());
        assertEquals(now, ordered.getUpdatedAt());
        assertThrows(UnsupportedOperationException.class, () -> evidence.imagingOrderIds().clear());
        repositoryList.clear();
        assertEquals(List.of(ordered.getId(), available.getId()), evidence.imagingOrderIds());
        verify(orders).findByVisitId(visitId);
        verifyNoMoreInteractions(orders);
    }

    @Test
    void recordDefensivelyCopiesIdsSuppliedByOtherCallers() {
        var ids = new ArrayList<>(List.of(UUID.randomUUID()));
        var evidence = new ClinicalCompletionEvidence(visitId, false, false, 1, 1, false, ids);
        var original = List.copyOf(ids);
        ids.clear();
        assertEquals(original, evidence.imagingOrderIds());
        assertThrows(UnsupportedOperationException.class, () -> evidence.imagingOrderIds().add(UUID.randomUUID()));
    }

    @Test
    void pendingPaymentDoesNotMakeAvailableClinicalResultsOutstanding() {
        var available = order(ClinicalServiceType.IMAGING, ServiceOrderStatus.RESULTS_AVAILABLE, firstRoundId, now);
        assertEquals(ServiceOrderPaymentStatus.PENDING_PAYMENT, available.getPaymentStatus());
        returnedOrders(available);
        assertCounts(service.getCompletionEvidence(visitId), 1, 0, true);
    }

    @Test
    void nullVisitIdUsesClinicalValidationErrorWithoutRepositoryAccess() {
        assertEquals("MSG-39", assertThrows(ClinicalOperationException.class,
                () -> service.getCompletionEvidence(null)).code());
        verifyNoInteractions(examinations, orders);
    }

    @Test
    void examinationReadFailureIsNotConvertedToEmptyEvidence() {
        var failure = new IllegalStateException("Examination read unavailable");
        when(examinations.findByVisitId(visitId)).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> service.getCompletionEvidence(visitId)));
        verifyNoInteractions(orders);
    }

    @Test
    void orderReadFailureIsNotConvertedToCompletedEvidence() {
        var failure = new IllegalStateException("Order read unavailable");
        when(orders.findByVisitId(visitId)).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> service.getCompletionEvidence(visitId)));
    }

    private ClinicalExaminationJpaEntity examination(ClinicalExaminationStatus status) {
        return ClinicalExaminationJpaEntity.create(visitId, null, null, null, null, null, null, status, now);
    }

    private ServiceOrderJpaEntity order(ClinicalServiceType type, ServiceOrderStatus status, UUID roundId, Instant createdAt) {
        var order = new ServiceOrderJpaEntity("SO-test", roundId, visitId, UUID.randomUUID(), UUID.randomUUID(),
                "Service", type, UUID.randomUUID(), "Clinical reason", "Knee", null, BigDecimal.TEN, createdAt);
        if (status == ServiceOrderStatus.CANCELLED) order.cancel("Cancelled", createdAt);
        if (status == ServiceOrderStatus.RESULTS_AVAILABLE) order.markResult(createdAt);
        return order;
    }

    private void returnedOrders(ServiceOrderJpaEntity... values) {
        when(orders.findByVisitId(visitId)).thenReturn(List.of(values));
    }

    private static void assertCounts(ClinicalCompletionEvidence evidence, int active, int outstanding, boolean allResults) {
        assertEquals(active, evidence.activeOrderCount());
        assertEquals(outstanding, evidence.outstandingOrderCount());
        assertEquals(allResults, evidence.allNonCancelledOrdersHaveResults());
    }
}
