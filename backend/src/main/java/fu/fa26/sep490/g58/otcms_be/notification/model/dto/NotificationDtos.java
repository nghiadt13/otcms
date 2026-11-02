package fu.fa26.sep490.g58.otcms_be.notification.model.dto;

import fu.fa26.sep490.g58.otcms_be.notification.model.NotificationRelatedKind;
import java.time.OffsetDateTime;
import java.util.*;
import io.swagger.v3.oas.annotations.media.Schema;

public final class NotificationDtos {
    private NotificationDtos() { }
    @Schema(name = "NotificationRelatedRecord", description = "Polymorphic reference, not related-record data. REVIEW is supported by backend; FE union update is deferred.")
    public record RelatedRecord(NotificationRelatedKind kind, UUID id,
            @Schema(description = "Computed on read: record exists AND current account can access it; never stored") boolean available) { }
    @Schema(name = "NotificationItem", description = "Recipient-only inbox projection; persistence fields type/read_at/recipient_account_id are not exposed")
    public record Item(UUID id, String title, String content, OffsetDateTime createdAt, boolean read,
            RelatedRecord relatedRecord,
            @Schema(description = "Unread: [markRead]; read: []; calculated by backend") List<String> allowedActions) { }
    @Schema(name = "NotificationPage", description = "Zero-based page with newest createdAt/id first; totalPages is at least one")
    public record NotificationPage(List<Item> items, int page, int size, long totalItems, int totalPages) { }
    @Schema(name = "UnreadNotificationCount")
    public record UnreadCount(long unreadCount) { }
    @Schema(name = "MarkAllNotificationsReadResult")
    public record ReadAllResult(int updatedCount) { }
}
