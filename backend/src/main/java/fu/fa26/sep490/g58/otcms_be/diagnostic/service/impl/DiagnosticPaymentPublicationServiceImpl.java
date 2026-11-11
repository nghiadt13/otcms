package fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl;

import fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticRequestStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticRequestJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticPaymentPublicationService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiagnosticPaymentPublicationServiceImpl implements DiagnosticPaymentPublicationService {
    private final DiagnosticRequestJpaRepository requests;

    public DiagnosticPaymentPublicationServiceImpl(DiagnosticRequestJpaRepository requests) {
        this.requests = requests;
    }

    @Override
    @Transactional
    public void publishPaidOrder(UUID eventId, UUID invoiceId, UUID serviceOrderId) {
        if (eventId == null || invoiceId == null || serviceOrderId == null) throw dependencyUnavailable();
        var request = requests.findByServiceOrderIdForUpdate(serviceOrderId).orElseThrow(this::dependencyUnavailable);
        if (request.getBillingInvoiceId() != null) {
            if (invoiceId.equals(request.getBillingInvoiceId())
                    && (request.getBillingPaidEventId() == null || eventId.equals(request.getBillingPaidEventId()))) return;
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.EVIDENCE_MISMATCH);
        }
        if (request.getStatus() != DiagnosticRequestStatus.PENDING_PAYMENT || request.getRoomId() == null) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.STATE_CONFLICT);
        }
        request.recordInvoicePaid(invoiceId, eventId);
        request.setStatus(DiagnosticRequestStatus.PENDING);
        requests.saveAndFlush(request);
    }

    private DiagnosticOperationException dependencyUnavailable() {
        return new DiagnosticOperationException(DiagnosticOperationOutcome.DEPENDENCY_UNAVAILABLE);
    }
}
