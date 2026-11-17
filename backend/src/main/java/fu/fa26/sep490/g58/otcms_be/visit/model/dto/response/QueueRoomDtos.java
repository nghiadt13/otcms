package fu.fa26.sep490.g58.otcms_be.visit.model.dto.response;

import java.util.UUID;

public final class QueueRoomDtos {
    private QueueRoomDtos() { }

    public record Entry(UUID id, UUID visitId, Integer queueNumber, String stage, String status, long sortOrder) { }
    public record ReturnRequest(UUID beforeEntryId, String reason) { }
    public record Updated(String messageCode, Entry entry) { }
}
