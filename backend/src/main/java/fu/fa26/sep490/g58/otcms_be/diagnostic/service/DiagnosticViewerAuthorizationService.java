package fu.fa26.sep490.g58.otcms_be.diagnostic.service;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ViewAuthorization;
import java.util.UUID;

/** Current FEAT-003 owner decision for a user requesting one diagnostic study. */
public interface DiagnosticViewerAuthorizationService {
    ViewAuthorization authorize(UUID actorId, AccountRole role, String studyRef, String purpose);
}
