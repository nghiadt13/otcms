package fu.fa26.sep490.g58.otcms_be.diagnostic.service;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import java.util.UUID;

public interface DiagnosticAcquisitionService {
    UUID start(DiagnosticActor actor, UUID requestId);
}
