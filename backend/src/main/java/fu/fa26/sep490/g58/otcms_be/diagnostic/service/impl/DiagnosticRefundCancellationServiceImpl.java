package fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl;

import fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.RoomReservationPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticRequestStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticRequestJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticRefundCancellationService;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiagnosticRefundCancellationServiceImpl implements DiagnosticRefundCancellationService {
    private final DiagnosticRequestJpaRepository requests;
    private final ObjectProvider<RoomReservationPort> roomPorts;

    public DiagnosticRefundCancellationServiceImpl(DiagnosticRequestJpaRepository requests,
            ObjectProvider<RoomReservationPort> roomPorts) {
        this.requests = requests;
        this.roomPorts = roomPorts;
    }

    @Override
    @Transactional
    public void cancelRefundedOrder(UUID eventId, UUID invoiceId, UUID serviceOrderId, String reason) {
        if (eventId == null || invoiceId == null || serviceOrderId == null || reason == null || reason.isBlank()) {
            throw unavailable();
        }
        var request = requests.findByServiceOrderIdForUpdate(serviceOrderId).orElseThrow(this::unavailable);
        if (eventId.equals(request.getBillingRefundEventId()) && invoiceId.equals(request.getBillingInvoiceId())) return;
        if (!invoiceId.equals(request.getBillingInvoiceId()) || request.getStatus() != DiagnosticRequestStatus.PENDING
                || request.getCurrentAttemptId() != null) throw new DiagnosticOperationException(DiagnosticOperationOutcome.STATE_CONFLICT);
        RoomReservationPort rooms = roomPorts.getIfAvailable();
        if (rooms == null) throw unavailable();
        request.recordInvoiceItemRefund(eventId, reason.trim());
        requests.saveAndFlush(request);
        rooms.releaseImagingCapacity(request.getId(), "ITEM_REFUNDED");
    }

    private DiagnosticOperationException unavailable() {
        return new DiagnosticOperationException(DiagnosticOperationOutcome.DEPENDENCY_UNAVAILABLE);
    }
}
