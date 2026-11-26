package fu.fa26.sep490.g58.otcms_be.diagnostic.model;

import java.util.UUID;

/** Minimal FEAT-003 owner decision for an imaging technician's selected request attempt. */
public record DiagnosticAssignedImagingWork(UUID requestId, UUID attemptId, UUID visitId,
        UUID patientId, String assignmentVersion, boolean currentlyAssigned,
        String dicomPatientId, String dicomPatientIssuer, String dicomAccessionNumber,
        String dicomAccessionLocalNamespace) {
    /** Compatibility constructor for callers that do not consume DICOM identity metadata. */
    public DiagnosticAssignedImagingWork(UUID requestId, UUID attemptId, UUID visitId,
            UUID patientId, String assignmentVersion, boolean currentlyAssigned) {
        this(requestId, attemptId, visitId, patientId, assignmentVersion, currentlyAssigned,
                null, null, null, null);
    }
}
