package fu.fa26.sep490.g58.otcms_be.imaging.service;

import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingOperationStatus;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingDicomIdentity;
import java.util.List;
import java.util.UUID;

/**
 * Public use cases for the imaging feature; implementations own authorization and transactions.
 * Implementations must recheck the exact request/attempt assignment before upload or SEND, and
 * apply the same current operation-owner authorization to status reads and SEND. Missing and
 * out-of-scope operation IDs must share the same safe not-found outcome.
 */
public interface ImagingIngestService {
    /** Check current owner assignment before accepting any request bytes. */
    ImagingOperationStatus upload(ImagingUploadCommand command);

    /** Return database-backed DICOM identity after rechecking the technician's current request assignment. */
    ImagingDicomIdentity getDicomIdentity(UUID actorAccountId, UUID requestId);

    /** Authorize the actor against the persisted operation owner before returning its state. */
    ImagingOperationStatus getOperationStatus(UUID actorAccountId, UUID operationId);

    /** Return the actor's newest-first operations for the currently assigned request attempt. */
    List<ImagingOperationStatus> getOperationsForAttempt(UUID actorAccountId, UUID requestId, UUID attemptId);

    /** Recheck both persisted operation ownership and current assigned request/attempt authority. */
    ImagingOperationStatus send(UUID actorAccountId, UUID operationId, String idempotencyKey);
}
