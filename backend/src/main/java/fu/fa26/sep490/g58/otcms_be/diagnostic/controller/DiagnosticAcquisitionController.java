package fu.fa26.sep490.g58.otcms_be.diagnostic.controller;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticAcquisitionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("isAuthenticated()")
public class DiagnosticAcquisitionController {
    private final DiagnosticAcquisitionService acquisitions;

    public DiagnosticAcquisitionController(DiagnosticAcquisitionService acquisitions) {
        this.acquisitions = acquisitions;
    }

    @PostMapping("/api/v1/imaging-requests/{requestId}/acquisition-starts")
    public AcquisitionStartResponse start(@AuthenticationPrincipal AuthenticatedAccount principal,
            @PathVariable UUID requestId) {
        return new AcquisitionStartResponse(acquisitions.start(
                DiagnosticActor.user(principal.accountId(), principal.accountRole()), requestId));
    }

    /** UC-46 AF3: the technician cannot perform the order; the requesting doctor decides what happens to it. */
    @PostMapping("/api/v1/imaging-requests/{requestId}/cannot-perform")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cannotPerform(@AuthenticationPrincipal AuthenticatedAccount principal, @PathVariable UUID requestId,
            @Valid @RequestBody CannotPerformRequest body) {
        acquisitions.reportCannotPerform(DiagnosticActor.user(principal.accountId(), principal.accountRole()),
                requestId, body.reason());
    }

    public record AcquisitionStartResponse(UUID attemptId) { }

    public record CannotPerformRequest(@NotBlank @Size(max = 2000) String reason) { }
}
