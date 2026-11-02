package fu.fa26.sep490.g58.otcms_be.review.model.dto;

import fu.fa26.sep490.g58.otcms_be.review.model.ReviewStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

/** Distinct HTTP projections preserve the patient/public text and admin content contracts. */
public final class ReviewDtos {
    private ReviewDtos() { }
    @Schema(name = "SubmitReviewRequest", description = "Own completed visit review. Account/profile and allowedActions are never client inputs.")
    public record SubmitReviewRequest(@Schema(description = "Own CLOSED visit UUID", requiredMode = Schema.RequiredMode.REQUIRED) UUID visitId,
            @Schema(description = "JSON integer only; decimal/string coercion is rejected", minimum = "1", maximum = "5", requiredMode = Schema.RequiredMode.REQUIRED)
            @tools.jackson.databind.annotation.JsonDeserialize(using = ReviewRatingDeserializer.class) Integer rating,
            @Schema(description = "JSON string only; 10–1000 Unicode code points after trim", minLength = 10, maxLength = 1000, requiredMode = Schema.RequiredMode.REQUIRED)
            @tools.jackson.databind.annotation.JsonDeserialize(using = ReviewTextDeserializer.class) String text) { }
    @Schema(name = "ReviewReplyRequest")
    public record ReplyRequest(@Schema(description = "JSON string only; 1–1000 Unicode code points after trim", minLength = 1, maxLength = 1000, requiredMode = Schema.RequiredMode.REQUIRED)
            @tools.jackson.databind.annotation.JsonDeserialize(using = ReviewTextDeserializer.class) String text) { }
    @Schema(name = "ReviewPatient", description = "Private projection; task scope returns relationship SELF only")
    public record Patient(String fullName, String relationship) { }
    @Schema(name = "ReviewableVisit", description = "Own CLOSED visit without a review")
    public record EligibleVisit(UUID visitId, LocalDate visitDate, String serviceName, Patient patient) { }
    @Schema(name = "MyReview", description = "Patient projection; review text is named text, including all moderation states")
    public record MyReview(UUID id, UUID visitId, LocalDate visitDate, int rating, String text,
            ReviewStatus status, String clinicReply, OffsetDateTime createdAt, Patient patient) { }
    @Schema(name = "PublicReview", description = "Published-only projection. No Patient, account or visit identifiers; clinicReply is null if absent.")
    public record PublicReview(UUID id, int rating, String text, OffsetDateTime createdAt, String clinicReply) { }
    @Schema(name = "ReviewSummary", description = "Entire PUBLISHED population; average rounded to one decimal, average/count zero when empty")
    public record Summary(double average, long count) { }
    @Schema(name = "PublicReviewPage", description = "Zero-based page, size 1–100; summary is independent of pagination")
    public record PublicReviewPage(List<PublicReview> items, int page, int size, long totalItems,
            int totalPages, Summary summary) { }
    @Schema(name = "ReviewVisit", description = "Admin-only visit context")
    public record Visit(LocalDate visitDate, String doctorName, String serviceName) { }
    @Schema(name = "ReviewReply")
    public record Reply(String text, OffsetDateTime repliedAt) { }
    @Schema(name = "ReviewListItem", description = "Admin projection uses content. Server actions: approve/hide/unhide/reply/editReply.")
    public record ReviewListItem(UUID id, OffsetDateTime submittedAt, String patientName, int rating,
            String content, ReviewStatus status, boolean replied, Visit visit, List<String> allowedActions) { }
    @Schema(name = "ReviewDetail", description = "Admin review plus nullable reply; allowedActions depend on state and reply existence")
    public record ReviewDetail(UUID id, OffsetDateTime submittedAt, String patientName, int rating,
            String content, ReviewStatus status, boolean replied, Visit visit, List<String> allowedActions, Reply reply) { }
    @Schema(name = "ReviewCounts", description = "Global counts, independent of filters and page")
    public record Counts(long pending, long published, long hidden, long all) { }
    @Schema(name = "ReviewListPage")
    public record ReviewListPage(List<ReviewListItem> items, int page, int size, long totalItems,
            int totalPages, Counts counts) { }
    public record Items<T>(List<T> items) { }
    public record FieldError(String field, String code) { }
}
