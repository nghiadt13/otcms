package fu.fa26.sep490.g58.otcms_be.content.application;
import fu.fa26.sep490.g58.otcms_be.content.domain.ContentModels.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface ContentStore {
    record Filters(String type, String q, String audience, Instant from, Instant until, int page, int size) { }
    Page<WebsiteEntry> contents(Filters filters, boolean publishedOnly);
    Optional<WebsiteEntry> content(UUID id, boolean publishedOnly);
    Optional<WebsiteEntry> latest(ContentType type);
    WebsiteEntry insert(WebsiteEntry entry);
    WebsiteEntry update(WebsiteEntry entry, long expectedVersion);
    void image(Image image);
    Optional<Image> image(UUID id);
    Page<Announcement> announcements(Filters filters, boolean publicOnly);
    Optional<Announcement> announcement(UUID id, boolean publicOnly);
    Announcement insert(Announcement entry);
    void audit(UUID actor, String category, String action, UUID recordId, String recordLabel, Instant at);
}
