package fu.fa26.sep490.g58.otcms_be.imaging.integration.diagnostic;

import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingAssignedWork;
import java.util.UUID;

/** Adapter boundary to FEAT-003's public assigned-work service. */
@FunctionalInterface
public interface ImagingWorkAuthorizationPort {
    ImagingAssignedWork requireAssignedImagingWork(UUID actorAccountId, UUID requestId, UUID attemptId);

    default ImagingAssignedWork requireAssignedImagingWork(UUID actorAccountId, UUID requestId) {
        throw new fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException(
                fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome.UNAUTHORIZED_RECORD);
    }

    default void requireStatusRead(UUID actorAccountId, UUID requestId) {
        throw new fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException(
                fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome.DEPENDENCY_UNAVAILABLE);
    }
}
