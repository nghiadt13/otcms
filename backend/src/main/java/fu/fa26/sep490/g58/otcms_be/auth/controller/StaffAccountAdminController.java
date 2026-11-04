package fu.fa26.sep490.g58.otcms_be.auth.controller;

import fu.fa26.sep490.g58.otcms_be.auth.model.dto.StaffAccountDtos;
import fu.fa26.sep490.g58.otcms_be.auth.service.StaffAccountAdminService;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** R3 staff account transport boundary. No credential is returned by CREATE. */
@RestController
@RequestMapping("/api/v1/admin/staff-accounts")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class StaffAccountAdminController {
    private final StaffAccountAdminService accounts;

    public StaffAccountAdminController(StaffAccountAdminService accounts) {
        this.accounts = accounts;
    }

    @PostMapping
    @Operation(summary = "Create a Clinic Staff username account with an Admin-supplied initial password",
            description = "Roles: RECEPTIONIST, ORTHOPEDIC_DOCTOR, IMAGING_TECHNICIAN, LAB_TECHNICIAN. "
                    + "The initial password is never returned. First-login password change is required.")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Created; no-store"),
            @ApiResponse(responseCode = "400", description = "MSG-12 invalid input"),
            @ApiResponse(responseCode = "403", description = "MSG-14 Admin required"),
            @ApiResponse(responseCode = "409", description = "MSG-13 username taken")})
    public ResponseEntity<StaffAccountDtos.Created> create(@AuthenticationPrincipal AuthenticatedAccount actor,
            @Valid @RequestBody StaffAccountDtos.CreateRequest request, HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(accounts.create(
                actor, request.role(), request.username(), request.initialPassword(), CorrelationIdFilter.current(http)));
    }

    @GetMapping
    @Operation(summary = "Search Clinic Staff accounts", description = "Literal username substring; external role; "
            + "ACTIVE/INACTIVE status. Zero-based page, size 1..100 (default 20), newest createdAt then id first.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Paged results; empty is successful"),
            @ApiResponse(responseCode = "400", description = "MSG-54 invalid search criteria"),
            @ApiResponse(responseCode = "403", description = "MSG-14 Admin required")})
    public ResponseEntity<StaffAccountDtos.Page> list(@AuthenticationPrincipal AuthenticatedAccount actor,
            @RequestParam(required = false) String q, @RequestParam(required = false) String role,
            @RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(accounts.list(actor, q, role, status, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Read a Clinic Staff account with its optional basic profile")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Account detail; no-store"),
            @ApiResponse(responseCode = "404", description = "MSG-14 target unavailable or outside scope")})
    public ResponseEntity<StaffAccountDtos.Item> detail(@AuthenticationPrincipal AuthenticatedAccount actor,
            @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(accounts.detail(actor, id));
    }

    @PostMapping("/{id}/activation")
    @Operation(summary = "Activate a DISABLED Clinic Staff account", description = "Clears failed attempts and lock time.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Activated"),
            @ApiResponse(responseCode = "404", description = "MSG-14 target unavailable"),
            @ApiResponse(responseCode = "409", description = "MSG-42 transition unavailable")})
    public ResponseEntity<StaffAccountDtos.Item> activate(@AuthenticationPrincipal AuthenticatedAccount actor,
            @PathVariable UUID id, HttpServletRequest http) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(accounts.activate(actor, id, CorrelationIdFilter.current(http)));
    }

    @PostMapping("/{id}/deactivation")
    @Operation(summary = "Deactivate an ACTIVE Clinic Staff account and revoke all sessions")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Deactivated; linked records retained"),
            @ApiResponse(responseCode = "404", description = "MSG-14 target unavailable"),
            @ApiResponse(responseCode = "409", description = "MSG-42 transition unavailable")})
    public ResponseEntity<StaffAccountDtos.Item> deactivate(@AuthenticationPrincipal AuthenticatedAccount actor,
            @PathVariable UUID id, HttpServletRequest http) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(accounts.deactivate(actor, id, CorrelationIdFilter.current(http)));
    }

    @PostMapping("/{id}/password-reissues")
    @Operation(summary = "Reissue a Clinic Staff temporary password once", description = "Revokes sessions and reset "
            + "challenges; requires password change. Does not activate or unlock the account. Never cache the response.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "One-time temporary password; no-store"),
            @ApiResponse(responseCode = "404", description = "MSG-14 target unavailable")})
    public ResponseEntity<StaffAccountDtos.Reissued> reissue(@AuthenticationPrincipal AuthenticatedAccount actor,
            @PathVariable UUID id, HttpServletRequest http) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(accounts.reissuePassword(actor, id, CorrelationIdFilter.current(http)));
    }
}
