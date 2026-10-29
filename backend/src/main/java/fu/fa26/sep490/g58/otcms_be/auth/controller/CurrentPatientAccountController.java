package fu.fa26.sep490.g58.otcms_be.auth.controller;

import fu.fa26.sep490.g58.otcms_be.auth.exception.CurrentPatientAccountException;
import fu.fa26.sep490.g58.otcms_be.auth.service.CurrentPatientAccountService;
import fu.fa26.sep490.g58.otcms_be.common.api.StrictRequestBody;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** Self-service identity projection used by the Patient portal. */
@RestController
public class CurrentPatientAccountController {
    private final CurrentPatientAccountService account;

    public CurrentPatientAccountController(CurrentPatientAccountService account) {
        this.account = account;
    }

    @GetMapping("/api/v1/me")
    @PreAuthorize("hasAuthority('ROLE_PATIENT')")
    public CurrentPatientAccountService.CurrentPatientAccount get(
            @AuthenticationPrincipal AuthenticatedAccount actor) {
        if (actor == null) throw new CurrentPatientAccountException("FORBIDDEN");
        return account.get(actor);
    }

    @PatchMapping("/api/v1/me")
    @PreAuthorize("hasAuthority('ROLE_PATIENT')")
    public CurrentPatientAccountService.CurrentPatientAccount update(
            @AuthenticationPrincipal AuthenticatedAccount actor,
            @Valid @RequestBody UpdateCurrentPatientAccountRequest request) {
        if (actor == null || request == null) throw new CurrentPatientAccountException("FORBIDDEN");
        return account.updateDisplayName(actor, request.getDisplayName());
    }

    public static class UpdateCurrentPatientAccountRequest extends StrictRequestBody {
        @NotBlank
        @Size(min = 2, max = 100)
        private String displayName;

        public String getDisplayName() { return displayName; }

        public void setDisplayName(JsonNode displayName) {
            this.displayName = strictText(displayName);
        }
    }
}
