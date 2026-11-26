package fu.fa26.sep490.g58.otcms_be.imaging.model;

import java.util.Objects;
import java.util.UUID;

/** Owner-service result; these identifiers are authoritative and never copied from DICOM tags. */
public record ImagingAssignedWork(UUID actorAccountId, UUID requestId, UUID attemptId,
        UUID visitId, UUID patientId, String assignmentVersion, boolean currentlyAssigned,
        String dicomPatientId, String dicomPatientIssuer, String dicomAccessionNumber,
        String dicomAccessionLocalNamespace) {
    /** Compatibility constructor for tests and callers that only exercise upload authorization. */
    public ImagingAssignedWork(UUID actorAccountId, UUID requestId, UUID attemptId, UUID visitId,
            UUID patientId, String assignmentVersion, boolean currentlyAssigned) {
        this(actorAccountId, requestId, attemptId, visitId, patientId, assignmentVersion,
                currentlyAssigned, null, null, null, null);
    }

    public ImagingAssignedWork {
        Objects.requireNonNull(actorAccountId, "actorAccountId");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(attemptId, "attemptId");
        Objects.requireNonNull(visitId, "visitId");
        Objects.requireNonNull(patientId, "patientId");
        if (assignmentVersion == null || assignmentVersion.isBlank()) {
            throw new IllegalArgumentException("assignmentVersion is required");
        }
    }
}
