package fu.fa26.sep490.g58.otcms_be.content.mapper;

import fu.fa26.sep490.g58.otcms_be.content.exception.ContentOperationException;
import fu.fa26.sep490.g58.otcms_be.content.model.ClinicAnnouncement;
import fu.fa26.sep490.g58.otcms_be.content.model.ClinicContact;
import fu.fa26.sep490.g58.otcms_be.content.model.ContentPage;
import fu.fa26.sep490.g58.otcms_be.content.model.ContentType;
import fu.fa26.sep490.g58.otcms_be.content.model.MarkdownBody;
import fu.fa26.sep490.g58.otcms_be.content.model.WebsiteBody;
import fu.fa26.sep490.g58.otcms_be.content.model.WebsiteContent;
import fu.fa26.sep490.g58.otcms_be.content.model.WebsiteContentInput;
import fu.fa26.sep490.g58.otcms_be.content.model.dto.ClinicContentDtos.AboutResponse;
import fu.fa26.sep490.g58.otcms_be.content.model.dto.ClinicContentDtos.AnnouncementDetailResponse;
import fu.fa26.sep490.g58.otcms_be.content.model.dto.ClinicContentDtos.AnnouncementSummaryResponse;
import fu.fa26.sep490.g58.otcms_be.content.model.dto.ClinicContentDtos.ArticleContentResponse;
import fu.fa26.sep490.g58.otcms_be.content.model.dto.ClinicContentDtos.ArticleSummaryResponse;
import fu.fa26.sep490.g58.otcms_be.content.model.dto.ClinicContentDtos.BodyResponse;
import fu.fa26.sep490.g58.otcms_be.content.model.dto.ClinicContentDtos.ContactBodyResponse;
import fu.fa26.sep490.g58.otcms_be.content.model.dto.ClinicContentDtos.MarkdownBodyResponse;
import fu.fa26.sep490.g58.otcms_be.content.model.dto.ClinicContentDtos.PageResponse;
import fu.fa26.sep490.g58.otcms_be.content.model.dto.ClinicContentDtos.PublicAnnouncementResponse;
import fu.fa26.sep490.g58.otcms_be.content.model.dto.ClinicContentDtos.PublicArticleResponse;
import fu.fa26.sep490.g58.otcms_be.content.model.dto.ClinicContentDtos.WebsiteContentResponse;
import fu.fa26.sep490.g58.otcms_be.content.model.dto.ClinicContentDtos.WebsiteDetailResponse;
import fu.fa26.sep490.g58.otcms_be.content.model.dto.ClinicContentDtos.WebsiteSummaryResponse;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class ClinicContentDtoMapper {
    private final ObjectMapper json;
    private final jakarta.validation.Validator validator;
    public ClinicContentDtoMapper(ObjectMapper json, jakarta.validation.Validator validator) {
        this.json = json;
        this.validator = validator;
    }
    /** Validate after method authorization, preserving the original 403-before-business-validation order. */
    public <T> void validate(T request) {
        if (request == null || !validator.validate(request).isEmpty()) {
            throw new ContentOperationException(ContentOperationException.Reason.INVALID_CONTENT);
        }
    }

    public WebsiteContentInput input(String type, String title, JsonNode body, String imageUrl) {
        try {
            WebsiteBody value = null;
            if (body != null && body.isObject()) {
                value = json.treeToValue(body, ClinicContact.class);
            }
            else if (body != null && body.isString()) {
                value = new MarkdownBody(body.asString());
            }
            return new WebsiteContentInput(type, title, value, imageUrl);
        }
        catch (RuntimeException exception) {
            throw new ContentOperationException(ContentOperationException.Reason.INVALID_CONTENT);
        }
    }
    public static String imageUrl(UUID id) {
        return id == null ? null : "/api/v1/public/website-images/" + id;
    }
    public static ContactBodyResponse contact(ClinicContact contact) {
        return new ContactBodyResponse(contact.clinicName(), contact.address(), contact.hotline(), contact.email(), contact.openingHours());
    }
    private static BodyResponse body(WebsiteBody body) {
        return body instanceof MarkdownBody markdown ? new MarkdownBodyResponse(markdown.text()) : contact((ClinicContact) body);
    }
    private static List<String> actions(WebsiteContent content) {
        return content.status().equals("PUBLISHED") ? List.of("edit", "viewOnWebsite") : List.of("edit");
    }
    public static WebsiteSummaryResponse summary(WebsiteContent content) {
        return new WebsiteSummaryResponse(content.id(), content.type(), content.title(), content.publishedAt(),
            content.updatedAt(), content.publishedBy(), actions(content));
    }
    public static WebsiteContentResponse detail(WebsiteContent content) {
        if (content.type() == ContentType.HEALTH_ARTICLE) {
            return new ArticleContentResponse(content.id(), content.type(), content.title(), content.publishedAt(),
                content.updatedAt(), content.publishedBy(), actions(content), body(content.body()), imageUrl(content.imageId()));
        }
        return new WebsiteDetailResponse(content.id(), content.type(), content.title(), content.publishedAt(),
            content.updatedAt(), content.publishedBy(), actions(content), body(content.body()));
    }
    public static AnnouncementSummaryResponse announcementSummary(ClinicAnnouncement announcement) {
        return new AnnouncementSummaryResponse(announcement.id(), announcement.title(), audience(announcement),
            List.of("IN_APP"), announcement.publishedAt(), "SENT", List.of("view"));
    }
    private static String audience(ClinicAnnouncement announcement) {
        return announcement.recipientRole() == null ? announcement.audience() : announcement.recipientRole();
    }
    public static AnnouncementDetailResponse announcementDetail(ClinicAnnouncement announcement) {
        return new AnnouncementDetailResponse(announcement.id(), announcement.title(), audience(announcement),
            List.of("IN_APP"), announcement.publishedAt(), "SENT", List.of("view"), announcement.body(), announcement.publishedByName());
    }
    public static PublicAnnouncementResponse publicAnnouncement(ClinicAnnouncement announcement) {
        return new PublicAnnouncementResponse(announcement.id(), announcement.title(), announcement.body(), announcement.publishedAt());
    }
    public static ArticleSummaryResponse articleSummary(WebsiteContent content) {
        return new ArticleSummaryResponse(content.id(), content.title(), content.publishedAt(), imageUrl(content.imageId()), content.summary());
    }
    public static PublicArticleResponse publicArticle(WebsiteContent content) {
        return new PublicArticleResponse(content.id(), content.title(), content.publishedAt(), imageUrl(content.imageId()), body(content.body()));
    }
    public static AboutResponse about(WebsiteContent content) {
        return new AboutResponse(content.title(), body(content.body()));
    }
    public static <T, R> PageResponse<R> page(ContentPage<T> page, Function<T, R> mapper) {
        return new PageResponse<>(page.items().stream().map(mapper).toList(), page.page(), page.size(), page.totalItems(), page.totalPages());
    }
}
