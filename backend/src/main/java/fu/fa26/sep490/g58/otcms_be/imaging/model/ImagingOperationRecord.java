package fu.fa26.sep490.g58.otcms_be.imaging.model;

import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationState;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationType;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingVerificationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** FEAT-004 operation persistence projection; contains owner references but no patient/tag values. */
public record ImagingOperationRecord(UUID id, String studyRef, UUID requestId, UUID attemptId,
        UUID visitId, UUID patientId, UUID actorAccountId, ImagingOperationType operationType,
        ImagingOperationState state, String payloadSha256, long payloadSizeBytes,
        String studyInstanceUid, String modality, int seriesCount, int instanceCount,
        ImagingVerificationStatus verificationStatus, List<String> verificationEvidence,
        String archiveStudyRef, String safeOutcomeCode, long version, Instant createdAt, Instant updatedAt) {
    public ImagingOperationRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(attemptId, "attemptId");
        Objects.requireNonNull(visitId, "visitId");
        Objects.requireNonNull(patientId, "patientId");
        Objects.requireNonNull(actorAccountId, "actorAccountId");
        Objects.requireNonNull(operationType, "operationType");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(verificationStatus, "verificationStatus");
        verificationEvidence = List.copyOf(verificationEvidence);
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (payloadSha256 == null || !payloadSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("payloadSha256 must be lower-case SHA-256 hex");
        }
        if (payloadSizeBytes < 0 || seriesCount < 0 || instanceCount < 0 || version < 0
                || updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("Imaging operation persistence values are invalid");
        }
        if (safeOutcomeCode != null && !safeOutcomeCode.matches("[A-Z][A-Z0-9_]{0,63}")) {
            throw new IllegalArgumentException("safeOutcomeCode must be a stable code");
        }
    }

    public ImagingOperationStatus toStatus() {
        return new ImagingOperationStatus(id, state, createdAt, updatedAt, safeOutcomeCode);
    }
}
