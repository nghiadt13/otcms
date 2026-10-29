package fu.fa26.sep490.g58.otcms_be.auth.controller;

import fu.fa26.sep490.g58.otcms_be.auth.service.PatientAccountAdminService;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.response.PatientAccountAdminResponse;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.response.PatientAccountsPageResponse;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class PatientAccountAdminController {
    private final PatientAccountAdminService accounts;

    public PatientAccountAdminController(PatientAccountAdminService accounts) { this.accounts = accounts; }

    @GetMapping("/api/v1/admin/accounts")
    public PatientAccountsPageResponse list(@AuthenticationPrincipal AuthenticatedAccount actor,
            @RequestParam(required = false) String q, @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return accounts.list(actor, q, status, page, size);
    }

    @GetMapping("/api/v1/admin/accounts/{accountId}")
    public PatientAccountAdminResponse detail(@AuthenticationPrincipal AuthenticatedAccount actor,
            @PathVariable UUID accountId) {
        return accounts.detail(actor, accountId);
    }

    @PostMapping("/api/v1/admin/accounts/{accountId}/activation")
    public PatientAccountAdminResponse activate(@AuthenticationPrincipal AuthenticatedAccount actor,
            @PathVariable UUID accountId) {
        return accounts.activate(actor, accountId);
    }

    @PostMapping("/api/v1/admin/accounts/{accountId}/deactivation")
    public PatientAccountAdminResponse deactivate(@AuthenticationPrincipal AuthenticatedAccount actor,
            @PathVariable UUID accountId) {
        return accounts.deactivate(actor, accountId);
    }
}
