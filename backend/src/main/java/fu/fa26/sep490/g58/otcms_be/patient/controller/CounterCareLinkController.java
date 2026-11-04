package fu.fa26.sep490.g58.otcms_be.patient.controller;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.patient.model.dto.DependentDtos.*;
import fu.fa26.sep490.g58.otcms_be.patient.service.CounterCareLinkService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/care-links")
@PreAuthorize("hasAuthority('ROLE_RECEPTIONIST')")
public class CounterCareLinkController {
    private final CounterCareLinkService links;
    public CounterCareLinkController(CounterCareLinkService links) { this.links = links; }

    @GetMapping("/pending")
    @Operation(summary = "Find pending counter requests by normalized name or document number")
    public ResponseEntity<Items<CounterItem>> pending(@AuthenticationPrincipal AuthenticatedAccount actor,
            @RequestParam(required = false) String query) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(links.pending(actor.accountId(), query));
    }
    @PostMapping("/{linkId}/counter-confirmation")
    @Operation(summary = "Confirm an identity-checked pending counter request")
    public ResponseEntity<LinkResult> confirm(@AuthenticationPrincipal AuthenticatedAccount actor,
            @PathVariable UUID linkId, @Valid @RequestBody CounterConfirmationRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(links.confirmCounter(actor.accountId(), linkId, request.identityChecked()));
    }
    @PostMapping("/{linkId}/counter-rejection")
    @Operation(summary = "Reject a pending counter request, retaining its history")
    public ResponseEntity<Void> reject(@AuthenticationPrincipal AuthenticatedAccount actor, @PathVariable UUID linkId) {
        links.rejectCounter(actor.accountId(), linkId);
        return ResponseEntity.noContent().build();
    }
}
