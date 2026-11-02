package fu.fa26.sep490.g58.otcms_be.content.model.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.validation.constraints.NotNull;
import fu.fa26.sep490.g58.otcms_be.content.model.ContentType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public final class ClinicContentDtos {
    private ClinicContentDtos() {
    }
    public record CreateWebsiteContentRequest(@NotNull String type, @NotNull String title, @NotNull JsonNode body, String imageUrl) {
    }
    public record UpdateWebsiteContentRequest(String type, @NotNull String title, @NotNull JsonNode body, String imageUrl) {
    }
    public record PublishAnnouncementRequest(@NotNull String title, @NotNull String body, @NotNull String audience, @NotNull List<String> channels) {
    }
    public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {
    }
    public sealed interface BodyResponse permits MarkdownBodyResponse, ContactBodyResponse {
    }
    public record MarkdownBodyResponse(@JsonValue String value) implements BodyResponse {
    }
    public record ContactBodyResponse(String clinicName, String address, String hotline, String email,
        List<String> openingHours) implements BodyResponse {
    }
    public sealed interface WebsiteContentResponse permits WebsiteSummaryResponse, WebsiteDetailResponse, ArticleContentResponse {
    }
    public record WebsiteSummaryResponse(UUID id, ContentType type, String title, Instant publishedAt,
        Instant updatedAt, String publishedBy, List<String> allowedActions) implements WebsiteContentResponse {
    }
    public record WebsiteDetailResponse(UUID id, ContentType type, String title, Instant publishedAt,
        Instant updatedAt, String publishedBy, List<String> allowedActions, BodyResponse body) implements WebsiteContentResponse {
    }
    public record ArticleContentResponse(UUID id, ContentType type, String title, Instant publishedAt,
        Instant updatedAt, String publishedBy, List<String> allowedActions, BodyResponse body, String imageUrl) implements WebsiteContentResponse {
    }
    public record ImageUploadResponse(String url) {
    }
    public record AnnouncementOptionsResponse(
        List<String> audiences,
        List<String> channels,
        List<String> allowedActions
    ) {
    }
    public record AnnouncementSummaryResponse(UUID id, String title, String audience, List<String> channels,
        Instant publishedAt, String deliveryStatus, List<String> allowedActions) {
    }
    public record AnnouncementDetailResponse(UUID id, String title, String audience, List<String> channels,
        Instant publishedAt, String deliveryStatus, List<String> allowedActions, String body, String publishedByName) {
    }
    public record PublicAnnouncementResponse(UUID id, String title, String body, Instant publishedAt) {
    }
    public record ArticleSummaryResponse(UUID id, String title, Instant publishedAt, String imageUrl, String summary) {
    }
    public record PublicArticleResponse(
        UUID id,
        String title,
        Instant publishedAt,
        String imageUrl,
        BodyResponse body
    ) {
    }
    public record AboutResponse(String title, BodyResponse body) {
    }
    public record HeroResponse(String title, String description, List<String> highlights) {
    }
    public record HomeResponse(ContactBodyResponse contact, HeroResponse hero, List<ArticleSummaryResponse> articles) {
    }
}
