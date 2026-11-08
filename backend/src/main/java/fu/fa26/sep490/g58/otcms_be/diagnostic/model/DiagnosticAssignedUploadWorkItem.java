package fu.fa26.sep490.g58.otcms_be.diagnostic.model;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticRequestStatus;
import java.time.Instant;
import java.util.UUID;

/** Request, attempt, and minimal patient identity for the currently assigned imaging technician. */
public record DiagnosticAssignedUploadWorkItem(UUID requestId, UUID attemptId, UUID visitId,
        UUID patientProfileId, DiagnosticPatientIdentity patient, UUID serviceOrderId, String currentStudyRef,
        UUID serviceId, String serviceName, String technicianInstructions, String imagingType,
        String bodyArea, String clinicalReason, UUID roomId,
        DiagnosticRequestStatus status, long version,
        Instant createdAt, Instant updatedAt) { }
