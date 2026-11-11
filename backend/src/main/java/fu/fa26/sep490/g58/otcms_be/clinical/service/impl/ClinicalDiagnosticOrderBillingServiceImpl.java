package fu.fa26.sep490.g58.otcms_be.clinical.service.impl;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalServiceType;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ServiceOrderPaymentStatus;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ServiceOrderStatus;
import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.ServiceOrderJpaEntity;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.ServiceOrderRepository;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalDiagnosticOrderBillingService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.VisitClinicalContextPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticPaymentPublicationService;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticRefundCancellationService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClinicalDiagnosticOrderBillingServiceImpl implements ClinicalDiagnosticOrderBillingService {
    private final ServiceOrderRepository orders;
    private final VisitClinicalContextPort visits;
    private final DiagnosticPaymentPublicationService imaging;
    private final DiagnosticRefundCancellationService refundCancellation;
    private final Clock clock;

    public ClinicalDiagnosticOrderBillingServiceImpl(ServiceOrderRepository orders, VisitClinicalContextPort visits,
            DiagnosticPaymentPublicationService imaging, DiagnosticRefundCancellationService refundCancellation,
            Clock clock) {
        this.orders = orders;
        this.visits = visits;
        this.imaging = imaging;
        this.refundCancellation = refundCancellation;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BillableOrder> requireBillableOrders(UUID visitId, UUID receptionistAccountId, List<UUID> orderIds) {
        if (visitId == null || receptionistAccountId == null || orderIds == null || orderIds.isEmpty()
                || orderIds.size() > 50 || orderIds.stream().anyMatch(java.util.Objects::isNull)
                || new HashSet<>(orderIds).size() != orderIds.size()) {
            throw new ClinicalOperationException("SOURCE_NOT_BILLABLE");
        }
        var context = visits.getVisitContext(visitId);
        if (context == null || !visitId.equals(context.visitId()) || context.patientId() == null
                || !context.visitOpen()) throw new ClinicalOperationException("SOURCE_NOT_BILLABLE");
        Map<UUID, ServiceOrderJpaEntity> byId = new HashMap<>();
        orders.findAllById(orderIds).forEach(order -> byId.put(order.getId(), order));
        if (byId.size() != orderIds.size()) throw new ClinicalOperationException("SOURCE_NOT_BILLABLE");
        return orderIds.stream().map(id -> {
            var order = byId.get(id);
            if (!visitId.equals(order.getVisitId()) || !context.patientId().equals(order.getPatientId())
                    || order.getOrderStatus() != ServiceOrderStatus.ORDERED
                    || order.getPaymentStatus() != ServiceOrderPaymentStatus.PENDING_PAYMENT
                    || (order.getServiceType() != ClinicalServiceType.IMAGING
                    && order.getServiceType() != ClinicalServiceType.LABORATORY)
                    || order.getPriceSnapshot() == null || order.getPriceSnapshot().signum() <= 0) {
                throw new ClinicalOperationException("SOURCE_NOT_BILLABLE");
            }
            return new BillableOrder(order.getId(), order.getPatientId(), order.getServiceId(),
                    order.getServiceNameSnapshot(), order.getPriceSnapshot(), order.getServiceType().name());
        }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PayableImagingOrder> listPendingImagingOrders(UUID patientProfileId) {
        if (patientProfileId == null) throw new ClinicalOperationException("SOURCE_NOT_BILLABLE");
        return orders.findAllByPatientIdAndServiceTypeAndPaymentStatusAndOrderStatusOrderByCreatedAtAscIdAsc(
                        patientProfileId, ClinicalServiceType.IMAGING, ServiceOrderPaymentStatus.PENDING_PAYMENT,
                        ServiceOrderStatus.ORDERED).stream()
                .filter(order -> isBillableVisit(order, patientProfileId))
                .map(this::payableImagingOrder)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PayableImagingOrder> listPendingImagingOrders() {
        return orders.findAllByServiceTypeAndPaymentStatusAndOrderStatusOrderByCreatedAtAscIdAsc(
                        ClinicalServiceType.IMAGING, ServiceOrderPaymentStatus.PENDING_PAYMENT,
                        ServiceOrderStatus.ORDERED).stream()
                .filter(order -> isBillableVisit(order, order.getPatientId()))
                .map(this::payableImagingOrder)
                .toList();
    }

    private boolean isBillableVisit(ServiceOrderJpaEntity order, UUID patientProfileId) {
        var context = visits.getVisitContext(order.getVisitId());
        return context != null && order.getVisitId().equals(context.visitId())
                && patientProfileId.equals(context.patientId()) && context.visitOpen();
    }

    private PayableImagingOrder payableImagingOrder(ServiceOrderJpaEntity order) {
        return new PayableImagingOrder(order.getId(), order.getOrderCode(), order.getVisitId(),
                order.getPatientId(), order.getServiceId(), order.getServiceNameSnapshot(),
                order.getClinicalReason(), order.getBodyArea(), order.getPriceSnapshot(), order.getCreatedAt());
    }

    @Override
    @Transactional
    public void acceptFullyPaidInvoice(FullyPaidInvoice event) {
        if (event == null || event.eventId() == null || event.invoiceId() == null || event.visitId() == null
                || event.patientId() == null || event.paidAmount() == null || event.paidAmount().signum() <= 0
                || event.items().isEmpty()) throw new ClinicalOperationException("SOURCE_NOT_BILLABLE");
        List<UUID> orderIds = event.items().stream().map(PaidOrderLine::orderId).toList();
        if (orderIds.stream().anyMatch(java.util.Objects::isNull) || new HashSet<>(orderIds).size() != orderIds.size()) {
            throw new ClinicalOperationException("SOURCE_NOT_BILLABLE");
        }
        Map<UUID, ServiceOrderJpaEntity> byId = new HashMap<>();
        orders.findAllById(orderIds).forEach(order -> byId.put(order.getId(), order));
        if (byId.size() != orderIds.size()) throw new ClinicalOperationException("SOURCE_NOT_BILLABLE");
        for (PaidOrderLine item : event.items()) {
            var order = byId.get(item.orderId());
            if (!event.visitId().equals(order.getVisitId()) || !event.patientId().equals(order.getPatientId())
                    || order.getOrderStatus() == ServiceOrderStatus.CANCELLED || item.allocatedAmount() == null
                    || item.allocatedAmount().compareTo(order.getPriceSnapshot()) != 0) {
                throw new ClinicalOperationException("SOURCE_NOT_BILLABLE");
            }
            if (order.getPaymentStatus() == ServiceOrderPaymentStatus.PENDING_PAYMENT) order.markPaid(clock.instant());
            else if (order.getPaymentStatus() != ServiceOrderPaymentStatus.PAID) throw new ClinicalOperationException("SOURCE_NOT_BILLABLE");
            if (order.getServiceType() == ClinicalServiceType.IMAGING) {
                imaging.publishPaidOrder(event.eventId(), event.invoiceId(), order.getId());
            }
        }
    }

    @Override
    @Transactional
    public void cancelAfterVerifiedItemRefund(UUID eventId, UUID invoiceId, int lineNumber,
            UUID orderId, String reason) {
        if (eventId == null || invoiceId == null || lineNumber < 1 || orderId == null
                || reason == null || reason.isBlank()) throw new ClinicalOperationException("SOURCE_NOT_BILLABLE");
        var order = orders.findById(orderId).orElseThrow(() -> new ClinicalOperationException("SOURCE_NOT_BILLABLE"));
        if (eventId.equals(order.getBillingRefundEventId())) return;
        if (order.getBillingRefundEventId() != null || order.getPaymentStatus() != ServiceOrderPaymentStatus.PAID
                || order.getOrderStatus() != ServiceOrderStatus.ORDERED) {
            throw new ClinicalOperationException("SOURCE_NOT_BILLABLE");
        }
        if (order.getServiceType() == ClinicalServiceType.IMAGING) {
            refundCancellation.cancelRefundedOrder(eventId, invoiceId, orderId, reason);
        }
        order.cancelForVerifiedRefund(eventId, reason.trim(), clock.instant());
    }
}
