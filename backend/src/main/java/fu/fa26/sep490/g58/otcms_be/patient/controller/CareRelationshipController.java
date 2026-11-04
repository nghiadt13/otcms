package fu.fa26.sep490.g58.otcms_be.patient.controller;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.patient.model.dto.DependentDtos.*;
import fu.fa26.sep490.g58.otcms_be.patient.model.dto.response.PatientProfileDetailResponse;
import fu.fa26.sep490.g58.otcms_be.patient.service.DependentProfileService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me")
@PreAuthorize("hasAuthority('ROLE_PATIENT')")
public class CareRelationshipController {
    private final DependentProfileService dependents;
    public CareRelationshipController(DependentProfileService dependents) { this.dependents = dependents; }

    @PostMapping("/dependents")
    @Operation(summary = "Add a dependent: create and link, email OTP, or counter verification")
    public ResponseEntity<LinkResult> add(@AuthenticationPrincipal AuthenticatedAccount actor,
            @Valid @RequestBody AddDependentRequest request) {
        var result = dependents.add(actor.accountId(), request);
        return ResponseEntity.status("LINKED".equals(result.result()) ? 201 : 202)
                .cacheControl(CacheControl.noStore()).body(result);
    }
    @PostMapping("/dependents/links/{linkId}/otp/verify")
    @Operation(summary = "Verify the dependent account email OTP for a requester-owned link")
    public ResponseEntity<LinkResult> verify(@AuthenticationPrincipal AuthenticatedAccount actor,
            @PathVariable UUID linkId, @Valid @RequestBody VerifyOtpRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(dependents.verify(actor.accountId(), linkId, request.code()));
    }
    @PostMapping("/dependents/links/{linkId}/otp/resend")
    @Operation(summary = "Replace and resend the current dependent OTP; requester only")
    public ResponseEntity<LinkResult> resend(@AuthenticationPrincipal AuthenticatedAccount actor, @PathVariable UUID linkId) {
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(dependents.resend(actor.accountId(), linkId));
    }
    @GetMapping("/patient-profiles")
    @Operation(summary = "List self first, then confirmed and pending dependents in stable order")
    public ResponseEntity<Items<ProfileItem>> list(@AuthenticationPrincipal AuthenticatedAccount actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(dependents.list(actor.accountId()));
    }
    @GetMapping("/dependents/{profileId}")
    @Operation(summary = "Read administrative fields of a confirmed dependent")
    public ResponseEntity<PatientProfileDetailResponse> detail(@AuthenticationPrincipal AuthenticatedAccount actor,
            @PathVariable UUID profileId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(dependents.detail(actor.accountId(), profileId));
    }
    @PutMapping("/dependents/{profileId}")
    @Operation(summary = "Update administrative fields without changing account linkage; no client version required")
    public ResponseEntity<PatientProfileDetailResponse> update(@AuthenticationPrincipal AuthenticatedAccount actor,
            @PathVariable UUID profileId, @Valid @RequestBody UpdateDependentRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(dependents.update(actor.accountId(), profileId, request));
    }
    @DeleteMapping("/dependents/{profileId}")
    @Operation(summary = "Revoke management access while retaining all clinic records")
    public ResponseEntity<Void> unlink(@AuthenticationPrincipal AuthenticatedAccount actor, @PathVariable UUID profileId) {
        dependents.unlink(actor.accountId(), profileId);
        return ResponseEntity.noContent().build();
    }
}
