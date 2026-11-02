package fu.fa26.sep490.g58.otcms_be.visit.model.dto.response;

import java.time.Instant;
import java.util.UUID;

public record VisitProgressResponse(UUID visitId, String source, String status, String currentStage,
        Instant arrivedAt, Integer queuePosition, String currentRoom) { }
