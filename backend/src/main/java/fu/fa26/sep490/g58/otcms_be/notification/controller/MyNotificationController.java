package fu.fa26.sep490.g58.otcms_be.notification.controller;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.notification.model.dto.NotificationDtos.*;
import fu.fa26.sep490.g58.otcms_be.notification.service.MyNotificationService;
import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/me/notifications")
@PreAuthorize("isAuthenticated()")
@Tag(name = "Personal notifications", description = "UC63: authenticated account inbox; recipients cannot be selected by the client.")
@ApiResponses({@ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "500", description = "MSG-04: retrieval or update failed",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))})
public class MyNotificationController {
    private final MyNotificationService notifications;
    public MyNotificationController(MyNotificationService notifications) { this.notifications = notifications; }
    @GetMapping
    @Operation(summary = "List my personal notifications", description = "Scopes by authenticated recipient, newest createdAt/id first. Related-record availability is recomputed against current access; unavailable records do not remove inbox items.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Notification page"),
            @ApiResponse(responseCode = "400", description = "MSG-54: malformed query",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))})
    public NotificationPage list(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedAccount actor,
            @Parameter(description = "true: unread only; false or omitted: all") @RequestParam(defaultValue = "false") boolean unread,
            @Parameter(description = "Zero-based page; clamped to an existing page") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size, clamped to 1–100") @RequestParam(defaultValue = "20") int size) {
        return notifications.list(actor, unread, page, size);
    }
    @GetMapping("/unread-count")
    @Operation(summary = "Count my unread notifications", description = "Counts only rows for the authenticated recipient with read_at IS NULL.")
    @ApiResponse(responseCode = "200", description = "Current recipient unreadCount")
    public UnreadCount count(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedAccount actor) { return notifications.unreadCount(actor); }
    @PostMapping("/{id}/read")
    @Operation(summary = "Mark my notification read", description = "Idempotent: preserves the first read timestamp and still returns 200 on repeat. Foreign and nonexistent IDs are indistinguishable. Availability is evaluated at response time.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "NotificationItem with read=true and allowedActions=[]"),
            @ApiResponse(responseCode = "404", description = "MSG-50: notification missing or belongs to another account",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))})
    public Item read(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedAccount actor,
            @Parameter(description = "Notification UUID", required = true) @PathVariable UUID id) { return notifications.markRead(actor, id); }
    @PostMapping("/read-all")
    @Operation(summary = "Mark all my notifications read", description = "Updates only the authenticated recipient's unread rows; already-read rows and other recipients are unchanged.")
    @ApiResponse(responseCode = "200", description = "updatedCount of rows changed by this request")
    public ReadAllResult readAll(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedAccount actor) { return notifications.readAll(actor); }
}
