package fu.fa26.sep490.g58.otcms_be.activitylog.model.dto;

import fu.fa26.sep490.g58.otcms_be.activitylog.model.ActivityLogEntry;
import java.time.Instant;
import java.util.List;

public final class ActivityLogDtos {
    private ActivityLogDtos() {
    }
    public record ItemResponse(String id, Instant occurredAt, String actorUsername, String actorRole,
        String action, String actionRole, String recordType, String recordLabel, String correctionReason,
        List<String> allowedActions) {
        public static ItemResponse from(ActivityLogEntry e) {
            return new ItemResponse(e.id(), e.occurredAt(), e.actorUsername(), e.actorRole(),
                e.action(), e.actionRole(), e.recordType(), e.recordLabel(), e.correctionReason(), List.of("view"));
        }
    }
    public record DetailResponse(String id, Instant occurredAt, String actorUsername, String actorRole,
        String action, String actionRole, String recordType, String recordLabel, String correctionReason,
        List<String> allowedActions, String recordCode, String systemNote) {
        public static DetailResponse from(ActivityLogEntry e) {
            return new DetailResponse(e.id(), e.occurredAt(), e.actorUsername(), e.actorRole(),
                e.action(), e.actionRole(), e.recordType(), e.recordLabel(), e.correctionReason(), List.of("view"), e.recordCode(), e.systemNote());
        }
    }
    public record PageResponse(List<ItemResponse> items, int page, int size, long totalItems, int totalPages) {
    }
}
