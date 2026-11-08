package fu.fa26.sep490.g58.otcms_be.visit.service;

import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.FindingsReleaseEvent;
import java.util.UUID;

public interface VisitImagingCoordinationService {
    void recordResultReady(UUID requestId, UUID visitId, UUID eventId);
    String acknowledgeFindingsRelease(FindingsReleaseEvent event);
    HistoryEvidence historyEvidence(UUID visitId, UUID doctorProfileId);
    record HistoryEvidence(UUID visitId, UUID patientId, String status, UUID careAssignmentId) { }
}
