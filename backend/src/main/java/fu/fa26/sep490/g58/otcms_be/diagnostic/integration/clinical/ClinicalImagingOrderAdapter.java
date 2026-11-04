package fu.fa26.sep490.g58.otcms_be.diagnostic.integration.clinical;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ServiceOrderStatus;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalOrderingService;
import fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.ClinicalImagingOrderPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ClinicalImagingOrder;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Adapts the clinical module's public ordering service to the diagnostic module's port. */
@Component
public class ClinicalImagingOrderAdapter implements ClinicalImagingOrderPort {
    private final ClinicalOrderingService ordering;

    public ClinicalImagingOrderAdapter(ClinicalOrderingService ordering) {
        this.ordering = Objects.requireNonNull(ordering, "ordering");
    }

    @Override
    public ClinicalImagingOrder requireOpenImagingOrder(UUID orderId) {
        try {
            var order = ordering.getImagingOrder(orderId);
            return new ClinicalImagingOrder(order.orderId(), order.visitId(), order.serviceId(),
                    order.orderStatus() == ServiceOrderStatus.ORDERED);
        } catch (ClinicalOperationException notUsable) {
            // Missing, not an imaging order, or cancelled: the caller decides how to report it.
            return null;
        }
    }

    @Override
    public void markResultAvailable(UUID orderId) {
        try {
            ordering.markImagingResultAvailable(orderId);
        } catch (ClinicalOperationException conflict) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.STATE_CONFLICT);
        }
    }

    @Override
    public void cancelOrderIfOpen(UUID orderId, String reason) {
        ClinicalImagingOrder order = requireOpenImagingOrder(orderId);
        if (order == null || !order.open()) return;
        try {
            ordering.cancel(orderId, reason);
        } catch (ClinicalOperationException conflict) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.STATE_CONFLICT);
        }
    }
}
