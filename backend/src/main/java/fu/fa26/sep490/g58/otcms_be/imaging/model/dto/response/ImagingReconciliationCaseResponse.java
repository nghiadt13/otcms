package fu.fa26.sep490.g58.otcms_be.imaging.model.dto.response;

import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingReconciliationQueue;
import java.time.Instant;
import java.util.UUID;

public record ImagingReconciliationCaseResponse(UUID caseId, UUID operationId, UUID eventId, String category,
        String classification, String state, String safeReasonCode, String safeEvidenceSha256,
        String assignedOwnerQueue, Instant openedAt, Instant updatedAt, Instant resolvedAt,
        String resolutionDecision) {
    public static ImagingReconciliationCaseResponse from(ImagingReconciliationQueue item) {
        return new ImagingReconciliationCaseResponse(item.caseId(), item.operationId(), item.eventId(),
                item.category(), item.classification(), item.state(), item.safeReasonCode(),
                item.safeEvidenceSha256(), item.assignedOwnerQueue(), item.openedAt(), item.updatedAt(),
                item.resolvedAt(), item.resolutionDecision());
    }
}
