package fu.fa26.sep490.g58.otcms_be.imaging.model;

import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingReconciliationCaseJpaEntity;
import java.time.Instant;
import java.util.UUID;

/** PHI-free projection returned to authorized reconciliation operators. */
public record ImagingReconciliationQueue(UUID caseId, UUID operationId, UUID eventId, String category,
        String classification, String state, String safeReasonCode, String safeEvidenceSha256,
        String assignedOwnerQueue, Instant openedAt, Instant updatedAt, Instant resolvedAt,
        String resolutionDecision) {
    public static ImagingReconciliationQueue from(ImagingReconciliationCaseJpaEntity entity) {
        return new ImagingReconciliationQueue(entity.getId(), entity.getOperationId(), entity.getEventId(),
                entity.getCategory(), entity.getClassification(), entity.getState(), entity.getSafeReasonCode(),
                entity.getSafeEvidenceSha256(), entity.getAssignedOwnerQueue(), entity.getOpenedAt(),
                entity.getUpdatedAt(), entity.getResolvedAt(), entity.getResolutionDecision());
    }
}
