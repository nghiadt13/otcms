package fu.fa26.sep490.g58.otcms_be.content.mapper;

import fu.fa26.sep490.g58.otcms_be.content.model.ClinicAnnouncement;
import fu.fa26.sep490.g58.otcms_be.content.model.ClinicContact;
import fu.fa26.sep490.g58.otcms_be.content.model.ContentType;
import fu.fa26.sep490.g58.otcms_be.content.model.MarkdownBody;
import fu.fa26.sep490.g58.otcms_be.content.model.WebsiteBody;
import fu.fa26.sep490.g58.otcms_be.content.model.WebsiteContent;
import fu.fa26.sep490.g58.otcms_be.content.model.entity.ClinicAnnouncementJpaEntity;
import fu.fa26.sep490.g58.otcms_be.content.model.entity.WebsiteContentJpaEntity;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ClinicContentEntityMapper {
    private final ObjectMapper json;
    public ClinicContentEntityMapper(ObjectMapper json) {
        this.json = json;
    }
    public WebsiteContent content(WebsiteContentJpaEntity entity, String author) {
        WebsiteBody body = entity.getType() == ContentType.CLINIC_INFO
        ? json.readValue(entity.getBody(), ClinicContact.class) : new MarkdownBody(entity.getBody());
        return new WebsiteContent(entity.getId(), entity.getType(), entity.getTitle(), entity.getSummary(), body,
            entity.getCoverImageId(), entity.getStatus(), persistedTime(entity.getPublishedAt()), persistedTime(entity.getUpdatedAt()),
            entity.getCreatedBy(), entity.getUpdatedBy(), author, entity.getVersion());
    }
    public ClinicAnnouncement announcement(ClinicAnnouncementJpaEntity entity, String author) {
        return new ClinicAnnouncement(entity.getId(), entity.getTitle(), entity.getBody(), entity.getAudience(),
            entity.getRecipientRole(), persistedTime(entity.getPublishedAt()), entity.getCreatedBy(), author, entity.getVersion());
    }
    public String body(WebsiteBody body) {
        return body instanceof MarkdownBody markdown ? markdown.text() : json.writeValueAsString(body);
    }
    /** PostgreSQL TIMESTAMPTZ stores microseconds; mirror the previous JDBC reload projection. */
    private java.time.Instant persistedTime(java.time.Instant value) {
        return value.plusNanos(500).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }
}
