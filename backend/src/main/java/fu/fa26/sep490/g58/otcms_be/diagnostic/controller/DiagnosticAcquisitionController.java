package fu.fa26.sep490.g58.otcms_be.diagnostic.controller;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticAcquisitionService;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    public record AcquisitionStartResponse(UUID attemptId) { }
}
