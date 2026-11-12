package fu.fa26.sep490.g58.otcms_be.common.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;

/** An audit snapshot supplied by the owning feature, in its business transaction. */
@Builder(builderMethodName = "")
public record AuditEntry(String source, String category, String action, String recordType,
        UUID entityId, String recordLabel, String correctionReason, boolean visibleInActivityLog,
        UUID actorAccountId, String actorType, Object beforeState, Object afterState, Map<String, Object> metadata,
        String clientIp, String userAgent, String identifierHmac, String outcome,
        Instant occurredAt, String dedupKey) {
    public static AuditEntryBuilder builder(String source, String category, String action, String recordType) {
        return new AuditEntryBuilder().source(source).category(category).action(action).recordType(recordType)
                .visibleInActivityLog(true);
    }
}
