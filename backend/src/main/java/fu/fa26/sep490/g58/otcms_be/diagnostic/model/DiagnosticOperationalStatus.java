package fu.fa26.sep490.g58.otcms_be.diagnostic.model;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticRequestStatus;
import java.time.Instant;
import java.util.UUID;

public record DiagnosticOperationalStatus(UUID requestId, UUID visitId, String imagingType,
        UUID roomId, DiagnosticRequestStatus status, long version, Instant createdAt, Instant updatedAt) { }
