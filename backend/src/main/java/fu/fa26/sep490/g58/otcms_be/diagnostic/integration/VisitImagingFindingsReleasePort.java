package fu.fa26.sep490.g58.otcms_be.diagnostic.integration;

import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.FindingsReleaseEvent;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ReleaseAcknowledgement;

/** Narrow Visit-owned durable acknowledgement for releasing imaging findings to an authorized Patient. */
public interface VisitImagingFindingsReleasePort {
    ReleaseAcknowledgement publishFindingsReleased(FindingsReleaseEvent event, String payloadSha256);
}
