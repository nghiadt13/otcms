package fu.fa26.sep490.g58.otcms_be.diagnostic.service;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticHistoryPage;
import java.util.UUID;

public interface DiagnosticHistoryService {
    DiagnosticHistoryPage getImagingHistory(DiagnosticActor actor, UUID patientId, UUID visitId, int page, int size);
}
