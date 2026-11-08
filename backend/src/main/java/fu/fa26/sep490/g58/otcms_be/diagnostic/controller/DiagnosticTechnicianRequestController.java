package fu.fa26.sep490.g58.otcms_be.diagnostic.controller;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.dto.TechnicianImagingRequestResponse;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticTechnicianRequestQueryService;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class DiagnosticTechnicianRequestController {
    private final DiagnosticTechnicianRequestQueryService query;
    public DiagnosticTechnicianRequestController(DiagnosticTechnicianRequestQueryService query) { this.query = query; }
    @GetMapping("/api/v1/imaging-requests/{requestId}/clinical-details")
    @PreAuthorize("hasAuthority('ROLE_IMAGING_TECHNICIAN')")
    public ResponseEntity<TechnicianImagingRequestResponse> get(@PathVariable UUID requestId,
            @AuthenticationPrincipal AuthenticatedAccount account) {
        if (account == null) throw new DiagnosticOperationException(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(query.getPendingRequest(DiagnosticActor.user(account.accountId(), account.accountRole()), requestId));
    }
}
