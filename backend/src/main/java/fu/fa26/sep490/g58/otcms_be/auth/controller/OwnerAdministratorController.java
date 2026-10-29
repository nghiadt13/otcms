package fu.fa26.sep490.g58.otcms_be.auth.controller;

import fu.fa26.sep490.g58.otcms_be.auth.model.dto.request.CreateAdministratorAccountRequest;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.response.AdministratorAccountResponse;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.response.CreateAdministratorAccountResponse;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.response.ReissueAdministratorPasswordResponse;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus;
import fu.fa26.sep490.g58.otcms_be.auth.service.OwnerAdministratorService;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/owner/administrator-accounts")
@PreAuthorize("hasAuthority('ROLE_OWNER')")
public class OwnerAdministratorController {
    private final OwnerAdministratorService administrators;

    public OwnerAdministratorController(OwnerAdministratorService administrators) {
        this.administrators = administrators;
    }

    @PostMapping
    public ResponseEntity<CreateAdministratorAccountResponse> create(
            @AuthenticationPrincipal AuthenticatedAccount actor,
            @Valid @RequestBody CreateAdministratorAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(administrators.create(actor, request.getUsername()));
    }

    @PostMapping("/{accountId}/activate")
    public AdministratorAccountResponse activate(@AuthenticationPrincipal AuthenticatedAccount actor,
            @PathVariable UUID accountId) {
        return administrators.activate(actor, accountId);
    }

    @PostMapping("/{accountId}/deactivate")
    public AdministratorAccountResponse deactivate(@AuthenticationPrincipal AuthenticatedAccount actor,
            @PathVariable UUID accountId) {
        return administrators.deactivate(actor, accountId);
    }

    @PostMapping("/{accountId}/password-reissues")
    public ResponseEntity<ReissueAdministratorPasswordResponse> reissue(
            @AuthenticationPrincipal AuthenticatedAccount actor, @PathVariable UUID accountId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(administrators.reissuePassword(actor, accountId));
    }

    @GetMapping
    public ResponseEntity<Page<AdministratorAccountResponse>> list(
            @AuthenticationPrincipal AuthenticatedAccount actor,
            @RequestParam(required = false) String query, @RequestParam(required = false) AccountStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(administrators.list(actor, query, status, page, size));
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AdministratorAccountResponse> detail(@AuthenticationPrincipal AuthenticatedAccount actor,
            @PathVariable UUID accountId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(administrators.detail(actor, accountId));
    }
}
