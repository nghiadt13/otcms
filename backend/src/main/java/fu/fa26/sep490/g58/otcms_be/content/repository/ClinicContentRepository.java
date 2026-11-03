package fu.fa26.sep490.g58.otcms_be.content.repository;

import fu.fa26.sep490.g58.otcms_be.content.model.ClinicAnnouncement;
import fu.fa26.sep490.g58.otcms_be.content.model.ContentPage;
import fu.fa26.sep490.g58.otcms_be.content.model.ContentType;
import fu.fa26.sep490.g58.otcms_be.content.model.WebsiteContent;
import fu.fa26.sep490.g58.otcms_be.content.model.WebsiteImage;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ClinicContentRepository {
    record Filters(String type, String q, String audience, Instant from, Instant until, int page, int size) {
    }
    ContentPage<WebsiteContent> contents(Filters filters, boolean publishedOnly);
    Optional<WebsiteContent> content(UUID id, boolean publishedOnly);
    Optional<WebsiteContent> latest(ContentType type);
    WebsiteContent insert(WebsiteContent entry);
    WebsiteContent update(WebsiteContent entry, long expectedVersion);
    void image(WebsiteImage image);
    Optional<WebsiteImage> image(UUID id);
    ContentPage<ClinicAnnouncement> announcements(Filters filters, boolean publicOnly);
    Optional<ClinicAnnouncement> announcement(UUID id, boolean publicOnly);
    ClinicAnnouncement insert(ClinicAnnouncement entry);
    void audit(UUID actor, String category, String action, UUID recordId, String recordLabel, Instant at);
}
