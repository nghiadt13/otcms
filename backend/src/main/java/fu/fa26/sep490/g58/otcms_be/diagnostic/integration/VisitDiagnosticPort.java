package fu.fa26.sep490.g58.otcms_be.diagnostic.integration;

import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.NoShowDecision;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.VisitEvidence;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.FindingsReleaseEvent;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ReleaseAcknowledgement;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.VisitHistoryAuthorization;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.VisitStatusAuthorization;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.VisitStatusResponsibility;
import java.time.Instant;
import java.util.UUID;

/** Public Visit-owned operations consumed by FEAT-003. */
public interface VisitDiagnosticPort extends VisitDiagnosticReadPort {
    VisitEvidence requireImagingOrderEligibility(UUID visitId, UUID actorAccountId);

    NoShowDecision acceptDeferredNoShowCancellation(UUID requestId, UUID visitId,
            Instant deadline, UUID eventId);

    void publishResultReady(UUID requestId, UUID visitId, UUID eventId);

    ReleaseAcknowledgement publishFindingsReleased(FindingsReleaseEvent event);

    /**
     * Returns canonical Visit/Patient linkage for history. For a Doctor, authorized must include durable
     * evidence that this actor examined the Visit, even when closed. For a Patient actor, this is only a
     * Visit-to-Patient match lookup; PatientDiagnosticHistoryPort must independently authorize own/dependent access.
     */
    VisitHistoryAuthorization authorizeImagingHistory(UUID visitId, UUID actorId);

    VisitStatusAuthorization authorizeImagingStatus(UUID visitId, UUID actorId,
            VisitStatusResponsibility responsibility);
}
