package fu.fa26.sep490.g58.otcms_be.activitylog.domain;
import java.time.Instant;
public record ActivityLogEntry(String id, Instant occurredAt, String actorUsername, String actorRole,
        String action, String actionRole, String recordType, String recordLabel,
        String correctionReason, String recordCode, String systemNote) { }
