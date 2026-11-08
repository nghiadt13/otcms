package fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalOrderingService;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.dto.TechnicianImagingRequestResponse;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticRequestJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class DiagnosticTechnicianRequestQueryServiceImpl implements DiagnosticTechnicianRequestQueryService {
    private final DiagnosticStatusService statuses;
    private final DiagnosticRequestJpaRepository requests;
    private final ClinicalOrderingService orders;
    public DiagnosticTechnicianRequestQueryServiceImpl(DiagnosticStatusService statuses,
            DiagnosticRequestJpaRepository requests, ClinicalOrderingService orders) {
        this.statuses = statuses; this.requests = requests; this.orders = orders;
    }
    @Override public TechnicianImagingRequestResponse getPendingRequest(DiagnosticActor actor, UUID requestId) {
        if (actor == null || actor.kind() != DiagnosticActor.Kind.USER || actor.role() != AccountRole.IMAGING_TECHNICIAN) {
            throw error(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD);
        }
        if (requestId == null) throw error(DiagnosticOperationOutcome.RECORD_NOT_FOUND);
        // Reuse current-shift and active-upload-state authorization before clinical details are read.
        var authorized = statuses.getStatus(actor, requestId);
        var request = requests.findById(requestId).orElseThrow(() -> error(DiagnosticOperationOutcome.RECORD_NOT_FOUND));
        if (authorized == null || !requestId.equals(authorized.requestId())
                || !request.getVisitId().equals(authorized.visitId()) || request.getRoomId() == null
                || !request.getRoomId().equals(authorized.roomId()) || request.getStatus() != authorized.status()) {
            throw error(DiagnosticOperationOutcome.EVIDENCE_MISMATCH);
        }
        String instructions = null;
        if (request.getServiceOrderId() != null) {
            final ClinicalOrderingService.OrderView order;
            try { order = orders.getImagingOrder(request.getServiceOrderId()); }
            catch (ClinicalOperationException ex) { throw error(DiagnosticOperationOutcome.DEPENDENCY_UNAVAILABLE); }
            if (order == null || !request.getServiceOrderId().equals(order.orderId())
                    || !request.getVisitId().equals(order.visitId()) || !request.getPatientId().equals(order.patientId())
                    || !request.getServiceId().equals(order.serviceId())) {
                throw error(DiagnosticOperationOutcome.EVIDENCE_MISMATCH);
            }
            instructions = order.instructions();
        }
        return new TechnicianImagingRequestResponse(request.getId(), request.getVisitId(), request.getPatientId(),
                request.getOrderingDoctorId(), request.getServiceId(), request.getRoomId(), request.getImagingType(),
                request.getBodyArea(), request.getClinicalReason(), instructions, request.getStatus());
    }
    private static DiagnosticOperationException error(DiagnosticOperationOutcome outcome) { return new DiagnosticOperationException(outcome); }
}
