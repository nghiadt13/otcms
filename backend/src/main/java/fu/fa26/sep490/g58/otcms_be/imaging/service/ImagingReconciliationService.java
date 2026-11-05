package fu.fa26.sep490.g58.otcms_be.imaging.service;

import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingReconciliationDecisionCommand;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingReconciliationQueue;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingReconciliationClassification;
import java.util.UUID;
import org.springframework.data.domain.Page;

/** Public reconciliation use cases for imaging workflows and authorized operators. */
public interface ImagingReconciliationService {
    void openOperation(UUID operationId, String category, String safeReason);
    void openOperation(UUID operationId, String category,
            ImagingReconciliationClassification classification, String safeReason);
    void openEvent(UUID eventId, String category, String safeReason);
    void openEvent(UUID eventId, String category,
            ImagingReconciliationClassification classification, String safeReason);
    void openEvent(UUID eventId, String category, ImagingReconciliationClassification classification,
            String safeEvidenceSha256, String safeReason);
    Page<ImagingReconciliationQueue> listQueue(UUID actorId, ImagingReconciliationQuery query,
            String correlationId);
    void recordDecision(UUID actorId, UUID caseId, ImagingReconciliationDecisionCommand command,
            String correlationId);
    UUID reconcileOpenBatch(UUID afterCaseId);
}
