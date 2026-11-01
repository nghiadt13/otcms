package fu.fa26.sep490.g58.otcms_be.review.controller;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.review.model.dto.ReviewDtos.*;
import fu.fa26.sep490.g58.otcms_be.review.service.ReviewService;
import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@PreAuthorize("hasAuthority('ROLE_PATIENT')")
@Tag(name = "Patient reviews", description = "UC59: reviews of the authenticated Patient's own profile; dependents are excluded.")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Patient role required, or MSG-56: visit/profile outside own scope",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "MSG-04: request processing failed",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))})
public class PatientReviewController {
    private final ReviewService reviews;
    public PatientReviewController(ReviewService reviews) { this.reviews = reviews; }
    @GetMapping("/api/v1/me/reviews/eligible-visits")
    @Operation(summary = "List visits eligible for review", description = "Returns own CLOSED visits without an existing review. CLOSED corresponds to SRS Completed. No account/profile selector is accepted.")
    @ApiResponse(responseCode = "200", description = "Unpaged items of eligible visits, newest visit date first")
    public Items<EligibleVisit> eligible(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedAccount actor) {
        return reviews.eligible(actor.accountId());
    }
    @GetMapping("/api/v1/me/reviews")
    @Operation(summary = "List my submitted reviews", description = "Returns reviews for the current account's own profile, newest submission first, including all moderation states and any clinic reply.")
    @ApiResponse(responseCode = "200", description = "Unpaged items; patient review content is named text")
    public Items<MyReview> mine(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedAccount actor) { return reviews.mine(actor.accountId()); }
    @PostMapping("/api/v1/reviews")
    @Operation(summary = "Submit a clinic review", description = "Creates one PENDING review per own CLOSED visit, SUBMIT history and notifications to active Admins atomically. Text is trimmed; integer rating is 1–5.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Review created in PENDING state"),
            @ApiResponse(responseCode = "400", description = "MSG-57: invalid fields; fieldErrors is at the response root",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "MSG-56: visit not CLOSED, duplicate visit review or concurrent conflict",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))})
    public ResponseEntity<MyReview> submit(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedAccount actor,
            @RequestBody SubmitReviewRequest body) {
        var review = reviews.submit(actor.accountId(), body);
        return ResponseEntity.created(URI.create("/api/v1/me/reviews")).body(review);
    }
}
