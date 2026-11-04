package fu.fa26.sep490.g58.otcms_be.patient.controller;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.patient.model.dto.DependentDtos.*;
import fu.fa26.sep490.g58.otcms_be.patient.service.DependentAddOtpService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** UC13: confirm adding a dependent with an email OTP sent to the requester's own account email. */
@RestController
@RequestMapping("/api/v1/me/dependents/add-requests")
@PreAuthorize("hasAuthority('ROLE_PATIENT')")
public class DependentAddOtpController {
    private final DependentAddOtpService addOtp;
    public DependentAddOtpController(DependentAddOtpService addOtp) { this.addOtp = addOtp; }

    @PostMapping
    @Operation(summary = "Validate a dependent-add request and email an OTP to the requester's account email")
    public ResponseEntity<AddOtpResult> request(@AuthenticationPrincipal AuthenticatedAccount actor,
            @Valid @RequestBody AddDependentRequest request) {
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(addOtp.request(actor.accountId(), request));
    }
    @PostMapping("/{requestId}/verify")
    @Operation(summary = "Verify the OTP, then add the dependent (create and link, owner OTP, or counter)")
    public ResponseEntity<LinkResult> verify(@AuthenticationPrincipal AuthenticatedAccount actor,
            @PathVariable UUID requestId, @Valid @RequestBody VerifyOtpRequest request) {
        var result = addOtp.verify(actor.accountId(), requestId, request.code());
        return ResponseEntity.status("LINKED".equals(result.result()) ? 201 : 202)
                .cacheControl(CacheControl.noStore()).body(result);
    }
    @PostMapping("/{requestId}/resend")
    @Operation(summary = "Replace and resend the OTP to the requester's account email")
    public ResponseEntity<AddOtpResult> resend(@AuthenticationPrincipal AuthenticatedAccount actor, @PathVariable UUID requestId) {
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(addOtp.resend(actor.accountId(), requestId));
    }
}
