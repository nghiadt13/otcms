package fu.fa26.sep490.g58.otcms_be.imaging.service;

import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingAssignedWork;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Applies the owner's current assignment decision to a specific authenticated upload request. */
@Component
public class ImagingWorkAuthorizationPolicy {
    public ImagingAssignedWork requireAssignedWork(UUID actorAccountId, UUID requestId, UUID attemptId,
            ImagingAssignedWork ownerDecision) {
        Objects.requireNonNull(actorAccountId, "actorAccountId");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(attemptId, "attemptId");
        if (ownerDecision == null || !ownerDecision.currentlyAssigned()
                || !actorAccountId.equals(ownerDecision.actorAccountId())
                || !requestId.equals(ownerDecision.requestId())
                || !attemptId.equals(ownerDecision.attemptId())) {
            throw new ImagingOperationException(ImagingOperationOutcome.UNAUTHORIZED_RECORD);
        }
        return ownerDecision;
    }
}
