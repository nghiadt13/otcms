package fu.fa26.sep490.g58.otcms_be.diagnostic.model;

import java.time.Instant;
import java.util.UUID;

public record DiagnosticFindingsView(UUID requestId, UUID findingsVersionId, int version,
        String state, String releaseState, String content, String correctionReason,
        UUID reportId, Instant recordedAt, Instant completedAt) { }
