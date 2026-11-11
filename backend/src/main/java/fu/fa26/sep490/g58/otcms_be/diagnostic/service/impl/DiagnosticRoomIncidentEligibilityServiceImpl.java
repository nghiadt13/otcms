package fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl;

import fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticRequestStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticRequestJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticRoomIncidentEligibilityService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiagnosticRoomIncidentEligibilityServiceImpl implements DiagnosticRoomIncidentEligibilityService {
    private final DiagnosticRequestJpaRepository requests;

    public DiagnosticRoomIncidentEligibilityServiceImpl(DiagnosticRequestJpaRepository requests) {
        this.requests = requests;
    }

    @Override
    @Transactional(readOnly = true)
    public IncidentRequestContext requireReportableRequest(UUID requestId, UUID roomId) {
        if (requestId == null || roomId == null) throw unavailable();
        var request = requests.findById(requestId).orElseThrow(this::unavailable);
        if (!roomId.equals(request.getRoomId())
                || (request.getStatus() != DiagnosticRequestStatus.PENDING
                && request.getStatus() != DiagnosticRequestStatus.PENDING_PAYMENT)
                || request.getCurrentAttemptId() != null) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.STATE_CONFLICT);
        }
        return new IncidentRequestContext(request.getId(), request.getVisitId(), request.getRoomId(),
                request.getOrderingDoctorId(), request.getImagingType(), request.getStatus().name());
    }

    @Override
    @Transactional(readOnly = true)
    public IncidentRequestContext requireReportableOrder(UUID orderId) {
        if (orderId == null) throw unavailable();
        var request = requests.findFirstByServiceOrderIdOrderByCreatedAtDescIdDesc(orderId).orElseThrow(this::unavailable);
        if ((request.getStatus() != DiagnosticRequestStatus.PENDING
                && request.getStatus() != DiagnosticRequestStatus.PENDING_PAYMENT)
                || request.getCurrentAttemptId() != null) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.STATE_CONFLICT);
        }
        return new IncidentRequestContext(request.getId(), request.getVisitId(), request.getRoomId(),
                request.getOrderingDoctorId(), request.getImagingType(), request.getStatus().name());
    }

    private DiagnosticOperationException unavailable() {
        return new DiagnosticOperationException(DiagnosticOperationOutcome.DEPENDENCY_UNAVAILABLE);
    }
}
