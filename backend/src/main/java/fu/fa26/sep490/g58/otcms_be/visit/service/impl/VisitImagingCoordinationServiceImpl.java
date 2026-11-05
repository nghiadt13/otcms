package fu.fa26.sep490.g58.otcms_be.visit.service.impl;

import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.FindingsReleaseEvent;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticResultEventQueryService;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticResultEventQueryService.ResultEvent;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.repository.*;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitImagingCoordinationService;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class VisitImagingCoordinationServiceImpl implements VisitImagingCoordinationService {
    private final ClinicVisitJpaRepository visits;
    private final VisitCareAssignmentRepository assignments;
    private final VisitImagingResultReceiptRepository receipts;
    private final DiagnosticResultEventQueryService diagnostic;
    private final Clock clock;
    public VisitImagingCoordinationServiceImpl(ClinicVisitJpaRepository visits, VisitCareAssignmentRepository assignments,
            VisitImagingResultReceiptRepository receipts, DiagnosticResultEventQueryService diagnostic, Clock clock) {
        this.visits = visits; this.assignments = assignments; this.receipts = receipts; this.diagnostic = diagnostic; this.clock = clock;
    }
    // A committed owner receipt survives an Imaging-owner failure and can be safely replayed.
    // Isolating this transaction also prevents a rejected delivery poisoning the dispatcher's retry transaction.
    @Override @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordResultReady(UUID requestId, UUID visitId, UUID eventId) {
        record(diagnostic.requireResultReady(requestId, visitId, eventId));
    }
    @Override @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String acknowledgeFindingsRelease(FindingsReleaseEvent event) {
        return record(diagnostic.requireFindingsRelease(event));
    }
    private String record(ResultEvent event) {
        var visit = visits.findById(event.visitId()).orElseThrow(() -> new VisitOperationException("RESOURCE_NOT_FOUND"));
        if (!visit.getPatientProfileId().equals(event.patientId())) throw new VisitOperationException("EVIDENCE_MISMATCH");
        // Atomic INSERT/ON CONFLICT followed by a committed-row comparison serializes duplicate event deliveries.
        receipts.insertReceipt(event.eventId(), event.requestId(), event.visitId(), event.patientId(), event.eventType(), event.payloadSha256(), clock.instant());
        var receipt = receipts.findById(event.eventId()).orElseThrow(() -> new VisitOperationException("DEPENDENCY_UNAVAILABLE"));
        if (!event.requestId().equals(receipt.getRequestId()) || !event.visitId().equals(receipt.getVisitId())
                || !event.patientId().equals(receipt.getPatientProfileId()) || !event.eventType().equals(receipt.getEventType())
                || !event.payloadSha256().equals(receipt.getPayloadSha256())) throw new VisitOperationException("IDEMPOTENCY_CONFLICT");
        return receipt.getPayloadSha256();
    }
    @Override @Transactional(readOnly = true)
    public HistoryEvidence historyEvidence(UUID visitId, UUID doctorProfileId) {
        if (visitId == null) throw new VisitOperationException("RESOURCE_NOT_FOUND");
        var visit = visits.findById(visitId).orElseThrow(() -> new VisitOperationException("RESOURCE_NOT_FOUND"));
        UUID assignment = doctorProfileId == null ? null : assignments.findFirstByVisitIdAndDoctorProfileIdAndStatusInOrderByAssignedAtDescIdDesc(
                visitId, doctorProfileId, java.util.List.of("IN_PROGRESS", "COMPLETED")).map(a -> a.getId()).orElse(null);
        return new HistoryEvidence(visit.getId(), visit.getPatientProfileId(), visit.getStatus(), assignment);
    }
}
