package fu.fa26.sep490.g58.otcms_be.diagnostic.service;

import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.FindingsReleaseEvent;
import java.util.UUID;

/** Trusted owner evidence for Visit coordination, never a client authorization shortcut. */
public interface DiagnosticResultEventQueryService {
    ResultEvent requireResultReady(UUID requestId, UUID visitId, UUID eventId);
    ResultEvent requireFindingsRelease(FindingsReleaseEvent event);

    record ResultEvent(UUID eventId, UUID requestId, UUID visitId, UUID patientId,
            String eventType, String payloadSha256) { }
}
