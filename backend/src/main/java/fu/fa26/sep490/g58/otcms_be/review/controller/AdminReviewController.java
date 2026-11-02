package fu.fa26.sep490.g58.otcms_be.review.controller;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.review.model.dto.ReviewDtos.*;
import fu.fa26.sep490.g58.otcms_be.review.service.ReviewService;
import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/reviews")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Review moderation", description = "UC61–UC62: Administrator moderation and clinic replies.")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Administrator role required",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "MSG-04: request processing failed",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))})
public class AdminReviewController {
    private final ReviewService reviews;
    public AdminReviewController(ReviewService reviews) { this.reviews = reviews; }
    @GetMapping
    @Operation(summary = "List reviews for moderation", description = "Filters and pagination apply to items. Counts for pending/published/hidden/all cover all reviews regardless of filters or page. Admin review text is named content.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Review page with global counts"),
            @ApiResponse(responseCode = "400", description = "MSG-54: invalid filter, from > to, or unsupported sort",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))})
    public ReviewListPage list(@Parameter(description = "Moderation state", schema = @Schema(allowableValues = {"PENDING", "PUBLISHED", "HIDDEN"})) @RequestParam(required = false) String status,
            @Parameter(description = "Rating 1–5") @RequestParam(required = false) Integer rating,
            @Parameter(description = "true: replied; false: not replied; omitted: both") @RequestParam(required = false) Boolean replied,
            @Parameter(description = "Inclusive submitted date in Asia/Ho_Chi_Minh, YYYY-MM-DD") @RequestParam(required = false) LocalDate from,
            @Parameter(description = "Inclusive submitted date, must be >= from") @RequestParam(required = false) LocalDate to,
            @Parameter(description = "Zero-based page; clamped to an existing page") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size, clamped to 1–100") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "submittedAt or rating, followed by asc or desc; default submittedAt,desc") @RequestParam(required = false) String sort) {
        return reviews.list(status, rating, replied, from, to, page, size, sort);
    }
    @GetMapping("/{id}")
    @Operation(summary = "Read review moderation detail", description = "Returns review content, visit context, clinic reply and server-calculated lowercase allowedActions.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Moderation detail"),
            @ApiResponse(responseCode = "404", description = "MSG-50: review unavailable",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))})
    public ReviewDetail detail(@Parameter(description = "Review UUID", required = true) @PathVariable UUID id) { return reviews.detail(id); }
    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve a pending review", description = "PENDING → PUBLISHED, with APPROVE history in the same transaction.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Updated moderation detail"),
            @ApiResponse(responseCode = "404", description = "MSG-50: review unavailable", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "MSG-56: invalid transition or optimistic conflict", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))})
    public ReviewDetail approve(@Parameter(description = "Review UUID", required = true) @PathVariable UUID id, @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedAccount actor) {
        return reviews.decide(id, actor.accountId(), "APPROVE");
    }
    @PostMapping("/{id}/hide")
    @Operation(summary = "Hide a patient review", description = "PENDING or PUBLISHED → HIDDEN. Preserves review/reply and appends HIDE history atomically.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Updated moderation detail"),
            @ApiResponse(responseCode = "404", description = "MSG-50: review unavailable", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "MSG-56: invalid transition or optimistic conflict", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))})
    public ReviewDetail hide(@Parameter(description = "Review UUID", required = true) @PathVariable UUID id, @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedAccount actor) {
        return reviews.decide(id, actor.accountId(), "HIDE");
    }
    @PostMapping("/{id}/unhide")
    @Operation(summary = "Republish a hidden review", description = "HIDDEN → PUBLISHED, with UNHIDE history in the same transaction.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Updated moderation detail"),
            @ApiResponse(responseCode = "404", description = "MSG-50: review unavailable", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "MSG-56: invalid transition or optimistic conflict", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))})
    public ReviewDetail unhide(@Parameter(description = "Review UUID", required = true) @PathVariable UUID id, @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedAccount actor) {
        return reviews.decide(id, actor.accountId(), "UNHIDE");
    }
    @PutMapping("/{id}/reply")
    @Operation(summary = "Save or replace the clinic reply", description = "Trims reply text to 1–1000 characters. Appends REPLY on first save or EDIT_REPLY on overwrite, in the review transaction. Reply is public only while the review is PUBLISHED.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Updated moderation detail including reply"),
            @ApiResponse(responseCode = "400", description = "MSG-57: invalid text; root fieldErrors", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "MSG-50: review unavailable", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "MSG-56: optimistic conflict", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))})
    public ReviewDetail reply(@Parameter(description = "Review UUID", required = true) @PathVariable UUID id, @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedAccount actor,
            @RequestBody ReplyRequest body) { return reviews.reply(id, actor.accountId(), body); }
}
