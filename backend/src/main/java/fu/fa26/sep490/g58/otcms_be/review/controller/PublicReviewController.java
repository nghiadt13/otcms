package fu.fa26.sep490.g58.otcms_be.review.controller;

import fu.fa26.sep490.g58.otcms_be.review.model.dto.ReviewDtos.PublicReviewPage;
import fu.fa26.sep490.g58.otcms_be.review.service.ReviewService;
import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Public reviews", description = "UC60: anonymous access to published reviews without Patient identity.")
public class PublicReviewController {
    private final ReviewService reviews;
    public PublicReviewController(ReviewService reviews) { this.reviews = reviews; }
    @GetMapping("/api/v1/public/reviews")
    @Operation(summary = "List public clinic reviews", description = "Only PUBLISHED reviews. Response contains no Patient/account/visit identity; summary covers all published reviews, independent of the page, with average rounded to one decimal.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page plus global published summary"),
            @ApiResponse(responseCode = "400", description = "MSG-54: invalid query or sort",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "MSG-04: request processing failed",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))})
    public PublicReviewPage list(@Parameter(description = "Zero-based page; clamped to an existing page") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size, clamped to 1–100") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "submittedAt or rating, followed by asc or desc; default submittedAt,desc", example = "submittedAt,desc") @RequestParam(required = false) String sort) {
        return reviews.published(page, size, sort);
    }
}
