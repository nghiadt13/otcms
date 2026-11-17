package fu.fa26.sep490.g58.otcms_be.billing.controller;

import fu.fa26.sep490.g58.otcms_be.billing.model.dto.request.RefundConfirmationRequest;
import fu.fa26.sep490.g58.otcms_be.billing.service.BillingReadService.ResultPage;
import fu.fa26.sep490.g58.otcms_be.billing.service.BillingRefundService;
import fu.fa26.sep490.g58.otcms_be.billing.service.BillingRefundService.Refund;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.CacheControl;
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
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_OWNER')")
public class BillingRefundController {
    private final BillingRefundService refunds;
    public BillingRefundController(BillingRefundService refunds) { this.refunds = refunds; }

    @GetMapping("/admin/refunds")
    public ResponseEntity<ResultPage<Refund>> list(@AuthenticationPrincipal AuthenticatedAccount actor,
            @RequestParam(defaultValue = "PENDING") String status, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(refunds.listRefunds(actor, status, page, size));
    }

    @PostMapping("/invoice-items/{itemId}/refund-confirmation")
    public ResponseEntity<Refund> confirm(@AuthenticationPrincipal AuthenticatedAccount actor, @PathVariable UUID itemId,
            @Valid @RequestBody(required = false) RefundConfirmationRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(refunds.confirmRefund(itemId, request == null ? null : request.note(), actor));
    }
}
