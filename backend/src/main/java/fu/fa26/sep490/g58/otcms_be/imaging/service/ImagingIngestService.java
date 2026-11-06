package fu.fa26.sep490.g58.otcms_be.imaging.service;

import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingOperationStatus;
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

    /** Authorize the actor against the persisted operation owner before returning its state. */
    ImagingOperationStatus getOperationStatus(UUID actorAccountId, UUID operationId);

    /** Recheck both persisted operation ownership and current assigned request/attempt authority. */
    ImagingOperationStatus send(UUID actorAccountId, UUID operationId, String idempotencyKey);
}
