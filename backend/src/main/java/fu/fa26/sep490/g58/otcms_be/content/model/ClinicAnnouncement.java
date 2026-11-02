package fu.fa26.sep490.g58.otcms_be.content.model;

import java.time.Instant;
import java.util.UUID;

public record ClinicAnnouncement(
    UUID id,
    String title,
    String body,
    String audience,
    String recipientRole,
    Instant publishedAt,
    UUID createdBy,
    String publishedByName,
    long version
) {
}
