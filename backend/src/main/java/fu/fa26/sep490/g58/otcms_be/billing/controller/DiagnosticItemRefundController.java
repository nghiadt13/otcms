package fu.fa26.sep490.g58.otcms_be.billing.controller;

import fu.fa26.sep490.g58.otcms_be.billing.service.DiagnosticItemRefundService;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class DiagnosticItemRefundController {
    private final DiagnosticItemRefundService refunds;
    public DiagnosticItemRefundController(DiagnosticItemRefundService refunds) { this.refunds = refunds; }

    @PostMapping("/invoices/{invoiceId}/items/{lineNumber}/refund-requests")
    @PreAuthorize("hasAuthority('ROLE_RECEPTIONIST')")
    public DiagnosticItemRefundService.RefundView request(@PathVariable UUID invoiceId,
            @PathVariable @Min(1) int lineNumber, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody RefundRequest body, @AuthenticationPrincipal AuthenticatedAccount actor) {
        return refunds.request(invoiceId, lineNumber, body.incidentId(), body.reason(), key, actor);
    }

    @GetMapping("/admin/diagnostic-refund-requests")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public List<DiagnosticItemRefundService.RefundView> pending(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal AuthenticatedAccount actor) { return refunds.pending(page, size, actor); }

    @GetMapping("/reception/diagnostic-refunds/approved")
    @PreAuthorize("hasAuthority('ROLE_RECEPTIONIST')")
    public List<DiagnosticItemRefundService.RefundView> approved(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal AuthenticatedAccount actor) { return refunds.approved(page, size, actor); }

    @PostMapping("/admin/diagnostic-refund-requests/{refundId}/decisions")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public DiagnosticItemRefundService.RefundView decide(@PathVariable UUID refundId,
            @Valid @RequestBody DecisionRequest body, @AuthenticationPrincipal AuthenticatedAccount actor) {
        return refunds.decide(refundId, body.decision(), body.reason(), actor);
    }

    @PostMapping("/diagnostic-refund-requests/{refundId}/execution")
    @PreAuthorize("hasAuthority('ROLE_RECEPTIONIST')")
    public DiagnosticItemRefundService.RefundView recordExecution(@PathVariable UUID refundId,
            @Valid @RequestBody RefundExecutionRequest body, @AuthenticationPrincipal AuthenticatedAccount actor) {
        return refunds.recordExecution(refundId, body.providerReference(), actor);
    }

    public record RefundRequest(@NotNull UUID incidentId, @NotBlank @Size(max = 1000) String reason) { }
    public record DecisionRequest(@NotBlank @Size(max = 16) String decision,
            @NotBlank @Size(max = 1000) String reason) { }
    public record RefundExecutionRequest(@NotBlank @Size(max = 128) String providerReference) { }
}
