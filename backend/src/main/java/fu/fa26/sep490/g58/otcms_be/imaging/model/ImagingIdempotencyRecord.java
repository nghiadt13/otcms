package fu.fa26.sep490.g58.otcms_be.imaging.model;

import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationType;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Scoped replay fingerprint; never stores upload bytes or DICOM tag values. */
public record ImagingIdempotencyRecord(UUID id, UUID actorAccountId, UUID requestId, UUID attemptId,
        ImagingOperationType operationType, String idempotencyKey, String payloadSha256,
        UUID targetOperationId, String safeOutcomeCode, Instant createdAt) {
    public ImagingIdempotencyRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(actorAccountId, "actorAccountId");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(attemptId, "attemptId");
        Objects.requireNonNull(operationType, "operationType");
        Objects.requireNonNull(targetOperationId, "targetOperationId");
        Objects.requireNonNull(createdAt, "createdAt");
        if (idempotencyKey == null || !idempotencyKey.matches("[\\x21-\\x7E]{1,128}")) {
            throw new IllegalArgumentException("idempotencyKey must be 1-128 visible ASCII characters");
        }
        if (payloadSha256 == null || !payloadSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("payloadSha256 must be lower-case SHA-256 hex");
        }
        if (safeOutcomeCode != null && !safeOutcomeCode.matches("[A-Z][A-Z0-9_]{0,63}")) {
            throw new IllegalArgumentException("safeOutcomeCode must be a stable code");
        }
    }
}
