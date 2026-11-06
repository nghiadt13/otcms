package fu.fa26.sep490.g58.otcms_be.imaging.controller;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingReconciliationDecisionCommand;
import fu.fa26.sep490.g58.otcms_be.imaging.model.dto.request.ImagingReconciliationDecisionRequest;
import fu.fa26.sep490.g58.otcms_be.imaging.model.dto.response.ImagingReconciliationCaseResponse;
import fu.fa26.sep490.g58.otcms_be.imaging.model.dto.response.ImagingReconciliationPageResponse;
import fu.fa26.sep490.g58.otcms_be.imaging.service.ImagingReconciliationQuery;
import fu.fa26.sep490.g58.otcms_be.imaging.service.ImagingReconciliationService;
import fu.fa26.sep490.g58.otcms_be.imaging.service.impl.ImagingAuditPersistenceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
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

/** ADMIN-only operations for reviewing and recording imaging recovery decisions. */
@RestController
@RequestMapping("/api/v1/imaging/reconciliation-cases")
public class ImagingReconciliationController {
    private final ImagingReconciliationService reconciliation;
    private final ImagingAuditPersistenceService audit;

    public ImagingReconciliationController(ImagingReconciliationService reconciliation,
            ImagingAuditPersistenceService audit) {
        this.reconciliation = reconciliation;
        this.audit = audit;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ImagingReconciliationPageResponse> list(
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String classification,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest request) {
        String correlationId = CorrelationIdFilter.current(request);
        requireAdmin(account, correlationId);
        Page<fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingReconciliationQueue> result =
                reconciliation.listQueue(account.accountId(),
                        new ImagingReconciliationQuery(state, classification, category, page, size), correlationId);
        return ResponseEntity.ok(new ImagingReconciliationPageResponse(result.getContent().stream()
                .map(ImagingReconciliationCaseResponse::from).toList(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages()));
    }

    @PostMapping("/{caseId}/decisions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> decide(@PathVariable UUID caseId,
            @Valid @RequestBody ImagingReconciliationDecisionRequest request,
            @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest servletRequest) {
        String correlationId = CorrelationIdFilter.current(servletRequest);
        requireAdmin(account, correlationId);
        reconciliation.recordDecision(account.accountId(), caseId,
                new ImagingReconciliationDecisionCommand(request.decision(), request.resolutionRef()), correlationId);
        return ResponseEntity.noContent().build();
    }

    private void requireAdmin(AuthenticatedAccount account, String correlationId) {
        if (account == null || account.accountId() == null || account.accountRole() != AccountRole.ADMIN) {
            if (account != null && account.accountId() != null) {
                try {
                    audit.recordActor(account.accountId(), "IMAGING_RECONCILIATION_ACCESS", "DENIED",
                            null, null, null, null, null, null, correlationId,
                            Map.of("reasonCode", "ADMIN_REQUIRED", "resourceType", "IMAGING_RECONCILIATION"));
                } catch (RuntimeException ignored) {
                    // Keep access-denied responses generic if audit persistence is unavailable.
                }
            }
            throw new ImagingOperationException(ImagingOperationOutcome.UNAUTHORIZED_RECORD);
        }
    }
}
