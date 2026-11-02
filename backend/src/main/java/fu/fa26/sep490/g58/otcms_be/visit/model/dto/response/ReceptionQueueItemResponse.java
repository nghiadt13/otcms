package fu.fa26.sep490.g58.otcms_be.visit.model.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ReceptionQueueItemResponse(UUID queueEntryId, UUID visitId, Instant arrivedAt,
        String status, int position, String currentStage, String currentRoom) { }
