package fu.fa26.sep490.g58.otcms_be.visit.controller;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import fu.fa26.sep490.g58.otcms_be.visit.service.RoomIncidentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class RoomIncidentController {
    private final RoomIncidentService incidents;

    public RoomIncidentController(RoomIncidentService incidents) { this.incidents = incidents; }

    @PostMapping("/imaging-requests/{requestId}/room-incidents")
    @PreAuthorize("hasAuthority('ROLE_IMAGING_TECHNICIAN')")
    public RoomIncidentService.IncidentView report(@PathVariable UUID requestId,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody ReportRequest body,
            @AuthenticationPrincipal AuthenticatedAccount actor,
            HttpServletRequest request) {
        return incidents.report(requestId, body.roomId(), actor.accountId(), key, body.incidentType(),
                body.description(), CorrelationIdFilter.current(request));
    }

    @GetMapping("/admin/room-incidents")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public List<RoomIncidentService.IncidentView> pending(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return incidents.listPending(page, size);
    }

    @PostMapping("/admin/room-incidents/{incidentId}/decisions")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public RoomIncidentService.IncidentView decide(@PathVariable UUID incidentId,
            @Valid @RequestBody DecisionRequest body,
            @AuthenticationPrincipal AuthenticatedAccount actor, HttpServletRequest request) {
        return incidents.decide(incidentId, actor.accountId(), body.decision(), body.reason(),
                CorrelationIdFilter.current(request));
    }

    @PostMapping("/admin/room-incidents/{incidentId}/restorations")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public RoomIncidentService.IncidentView restore(@PathVariable UUID incidentId,
            @Valid @RequestBody RestoreRequest body,
            @AuthenticationPrincipal AuthenticatedAccount actor, HttpServletRequest request) {
        return incidents.restore(incidentId, actor.accountId(), body.reason(), CorrelationIdFilter.current(request));
    }

    public record ReportRequest(@NotNull UUID roomId,
            @NotBlank @Size(max = 40) String incidentType,
            @NotBlank @Size(max = 2000) String description) { }
    public record DecisionRequest(@NotBlank @Size(max = 16) String decision,
            @NotBlank @Size(max = 1000) String reason) { }
    public record RestoreRequest(@NotBlank @Size(max = 1000) String reason) { }
}
