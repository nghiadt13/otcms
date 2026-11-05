package fu.fa26.sep490.g58.otcms_be.diagnostic.service;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.dto.TechnicianImagingRequestResponse;
import java.util.UUID;

public interface DiagnosticTechnicianRequestQueryService {
    TechnicianImagingRequestResponse getPendingRequest(DiagnosticActor actor, UUID requestId);
}
