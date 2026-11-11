package fu.fa26.sep490.g58.otcms_be.clinical.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Clinical-owned service used by Billing; Billing never reads clinical_service_orders directly. */
public interface ClinicalDiagnosticOrderBillingService {
    List<BillableOrder> requireBillableOrders(UUID visitId, UUID receptionistAccountId, List<UUID> orderIds);
    List<PayableImagingOrder> listPendingImagingOrders(UUID patientProfileId);
    List<PayableImagingOrder> listPendingImagingOrders();
    void acceptFullyPaidInvoice(FullyPaidInvoice event);
    void cancelAfterVerifiedItemRefund(UUID eventId, UUID invoiceId, int lineNumber,
            UUID orderId, String reason);

    record BillableOrder(UUID orderId, UUID patientId, UUID serviceId, String serviceName,
            BigDecimal unitPrice, String serviceType) { }
    record PayableImagingOrder(UUID orderId, String orderCode, UUID visitId, UUID patientProfileId,
            UUID serviceId, String serviceName, String clinicalReason, String bodyArea,
            BigDecimal unitPrice, java.time.Instant orderedAt) { }
    record PaidOrderLine(int lineNumber, UUID orderId, BigDecimal allocatedAmount) { }
    record FullyPaidInvoice(UUID eventId, UUID invoiceId, UUID visitId, UUID patientId,
            BigDecimal paidAmount, List<PaidOrderLine> items) {
        public FullyPaidInvoice { items = List.copyOf(items); }
    }
}
