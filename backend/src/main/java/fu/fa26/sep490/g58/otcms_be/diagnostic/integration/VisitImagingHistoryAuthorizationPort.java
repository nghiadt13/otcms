package fu.fa26.sep490.g58.otcms_be.diagnostic.integration;

import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.VisitHistoryAuthorization;
import java.util.UUID;

/** Visit-owned, read-only authorization evidence for Doctor imaging history and viewer grants. */
public interface VisitImagingHistoryAuthorizationPort {
    VisitHistoryAuthorization authorizeImagingHistory(UUID visitId, UUID actorAccountId);
}
