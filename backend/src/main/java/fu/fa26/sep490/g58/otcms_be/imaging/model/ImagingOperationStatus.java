package fu.fa26.sep490.g58.otcms_be.imaging.model;

import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationState;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Safe operation projection; contains no DICOM tags, PHI, or archive URL. */
public record ImagingOperationStatus(UUID operationId, ImagingOperationState state,
        Instant createdAt, Instant updatedAt, String safeOutcomeCode, Integer instanceCount) {
    public ImagingOperationStatus(UUID operationId, ImagingOperationState state,
            Instant createdAt, Instant updatedAt, String safeOutcomeCode) {
        this(operationId, state, createdAt, updatedAt, safeOutcomeCode, null);
    }

    public ImagingOperationStatus {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not precede createdAt");
        }
        if (instanceCount != null && instanceCount < 0) {
            throw new IllegalArgumentException("instanceCount must not be negative");
        }
        if (safeOutcomeCode != null && !safeOutcomeCode.matches("[A-Z][A-Z0-9_]{0,63}")) {
            throw new IllegalArgumentException("safeOutcomeCode must be a stable code");
        }
    }
}
