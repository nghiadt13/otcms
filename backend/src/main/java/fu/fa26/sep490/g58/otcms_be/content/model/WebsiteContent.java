package fu.fa26.sep490.g58.otcms_be.content.model;

import java.time.Instant;
import java.util.UUID;

public record WebsiteContent(
    UUID id,
    ContentType type,
    String title,
    String summary,
    WebsiteBody body,
    UUID imageId,
    String status,
    Instant publishedAt,
    Instant updatedAt,
    UUID createdBy,
    UUID updatedBy,
    String publishedBy,
    long version
) {
}
