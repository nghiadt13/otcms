package fu.fa26.sep490.g58.otcms_be.diagnostic.integration.clinical;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalServiceType;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ServiceOrderPaymentStatus;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ServiceOrderStatus;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalOrderingService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalOrderingService.OrderView;
import fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ClinicalImagingOrder;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClinicalImagingOrderAdapterTest {
    private final UUID orderId = UUID.randomUUID();
    private final UUID visitId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();
    private ClinicalOrderingService ordering;
    private ClinicalImagingOrderAdapter adapter;

    @BeforeEach
    void setUp() {
        ordering = mock(ClinicalOrderingService.class);
        adapter = new ClinicalImagingOrderAdapter(ordering);
    }

    @Test
    void mapsAnOrderedImagingOrderToAnOpenOrder() {
        when(ordering.getImagingOrder(orderId)).thenReturn(view(ServiceOrderStatus.ORDERED));

        ClinicalImagingOrder order = adapter.requireOpenImagingOrder(orderId);

        assertEquals(new ClinicalImagingOrder(orderId, visitId, serviceId, true), order);
    }

    @Test
    void anOrderWithResultsIsReturnedButNotOpen() {
        when(ordering.getImagingOrder(orderId)).thenReturn(view(ServiceOrderStatus.RESULTS_AVAILABLE));

        assertFalse(adapter.requireOpenImagingOrder(orderId).open());
    }

    @Test
    void aMissingNonImagingOrCancelledOrderIsReportedAsNull() {
        when(ordering.getImagingOrder(orderId)).thenThrow(new ClinicalOperationException("MSG-08"));

        assertNull(adapter.requireOpenImagingOrder(orderId));
    }

    @Test
    void marksTheResultThroughTheClinicalService() {
        adapter.markResultAvailable(orderId);

        verify(ordering).markImagingResultAvailable(orderId);
    }

    @Test
    void aClinicalRefusalWhileMarkingTheResultBecomesAStateConflict() {
        doThrow(new ClinicalOperationException("MSG-08")).when(ordering).markImagingResultAvailable(orderId);

        DiagnosticOperationException error = assertThrows(DiagnosticOperationException.class,
                () -> adapter.markResultAvailable(orderId));

        assertEquals(DiagnosticOperationOutcome.STATE_CONFLICT, error.outcome());
    }

    @Test
    void cancelsOnlyAnOpenOrder() {
        when(ordering.getImagingOrder(orderId)).thenReturn(view(ServiceOrderStatus.ORDERED));

        adapter.cancelOrderIfOpen(orderId, "Patient declined");

        verify(ordering).cancel(orderId, "Patient declined");
    }

    @Test
    void doesNotCancelAnOrderThatAlreadyHasResultsOrIsGone() {
        when(ordering.getImagingOrder(orderId)).thenReturn(view(ServiceOrderStatus.RESULTS_AVAILABLE));
        adapter.cancelOrderIfOpen(orderId, "Patient declined");

        when(ordering.getImagingOrder(orderId)).thenThrow(new ClinicalOperationException("MSG-08"));
        adapter.cancelOrderIfOpen(orderId, "Patient declined");

        verify(ordering, never()).cancel(any(), any());
    }

    private OrderView view(ServiceOrderStatus status) {
        return new OrderView(orderId, "ORD-1", visitId, UUID.randomUUID(), UUID.randomUUID(), serviceId, "MRI knee",
                ClinicalServiceType.IMAGING, UUID.randomUUID(), "Pain", "Knee", null, new BigDecimal("500000"),
                status, ServiceOrderPaymentStatus.PENDING_PAYMENT);
    }
}
