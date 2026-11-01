package fu.fa26.sep490.g58.otcms_be.content.domain;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
public final class ContentModels {
    private ContentModels() { }
    public enum ContentType { ABOUT_PAGE, HEALTH_ARTICLE, CLINIC_INFO }
    public record Contact(String clinicName, String address, String hotline, String email, List<String> openingHours) { }
    /** Body is plain text/Markdown or Contact. Never executable HTML. */
    public record WebsiteEntry(UUID id, ContentType type, String title, String summary, Object body,
            UUID imageId, String status, Instant publishedAt, Instant updatedAt, UUID createdBy,
            UUID updatedBy, String publishedBy, long version) { }
    public record Announcement(UUID id, String title, String body, String audience, String recipientRole,
            Instant publishedAt, UUID createdBy, String publishedByName, long version) { }
    public record Image(UUID id, byte[] bytes, String contentType) { }
    public record Page<T>(List<T> items, int page, int size, long totalItems, int totalPages) { }
}
