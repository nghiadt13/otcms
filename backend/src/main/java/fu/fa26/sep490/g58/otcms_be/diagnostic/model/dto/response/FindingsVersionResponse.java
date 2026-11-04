package fu.fa26.sep490.g58.otcms_be.diagnostic.model.dto.response;

import java.time.Instant;
import java.util.UUID;

public record FindingsVersionResponse(UUID requestId, UUID findingsVersionId, int version,
        String status, String releaseState, String content, String correctionReason,
        UUID reportId, Instant recordedAt, Instant completedAt) { }
