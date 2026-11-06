package fu.fa26.sep490.g58.otcms_be.diagnostic.model;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticRequestStatus;
import java.time.Instant;
import java.util.UUID;

/** Minimal request/attempt projection for the currently assigned imaging technician. */
public record DiagnosticAssignedUploadWorkItem(UUID requestId, UUID attemptId, UUID visitId,
        String imagingType, UUID roomId, DiagnosticRequestStatus status, long version,
        Instant createdAt, Instant updatedAt) { }
