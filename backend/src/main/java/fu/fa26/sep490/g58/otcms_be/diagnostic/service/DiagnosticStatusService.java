package fu.fa26.sep490.g58.otcms_be.diagnostic.service;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticAssignedUploadWorkPage;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticOperationalStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticOperationalStatusPage;
import java.util.UUID;

public interface DiagnosticStatusService {
    DiagnosticOperationalStatusPage listVisitStatus(DiagnosticActor actor, UUID visitId, int page, int size);

    DiagnosticAssignedUploadWorkPage listTechnicianUploadWork(
            DiagnosticActor actor, UUID roomId, int page, int size);

    DiagnosticOperationalStatus getStatus(DiagnosticActor actor, UUID requestId);
}
