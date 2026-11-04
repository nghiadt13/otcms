package fu.fa26.sep490.g58.otcms_be.diagnostic.integration;

import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.*;
import java.util.UUID;

/** Read capability independent of Visit result-delivery/coordination commands. Actor IDs are account IDs. */
public interface VisitDiagnosticReadPort {
    VisitEvidence requireImagingOrderEligibility(UUID visitId, UUID actorAccountId);
    VisitStatusAuthorization authorizeImagingStatus(UUID visitId, UUID actorAccountId,
            VisitStatusResponsibility responsibility);
}
