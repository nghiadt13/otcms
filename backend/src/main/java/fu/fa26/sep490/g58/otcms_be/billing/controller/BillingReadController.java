package fu.fa26.sep490.g58.otcms_be.billing.controller;

import fu.fa26.sep490.g58.otcms_be.billing.service.BillingReadService;
import fu.fa26.sep490.g58.otcms_be.billing.service.BillingReadService.*;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class BillingReadController {
    private final BillingReadService reads;
    public BillingReadController(BillingReadService reads) { this.reads = reads; }
    @GetMapping("/payments")
    @PreAuthorize("hasAnyAuthority('ROLE_PATIENT','ROLE_RECEPTIONIST','ROLE_ADMIN','ROLE_OWNER')")
    public ResponseEntity<ResultPage<Payment>> payments(@AuthenticationPrincipal AuthenticatedAccount actor,
            @RequestParam(required = false) UUID patientId, @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reads.payments(actor, patientId, from, to, page, size));
    }
    @GetMapping("/invoices/unpaid")
    @PreAuthorize("hasAnyAuthority('ROLE_RECEPTIONIST','ROLE_ADMIN','ROLE_OWNER')")
    public ResponseEntity<ResultPage<Balance>> unpaid(@AuthenticationPrincipal AuthenticatedAccount actor,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reads.unpaid(actor, page, size));
    }
    @GetMapping("/invoices/{invoiceId}/print")
    @PreAuthorize("hasAuthority('ROLE_RECEPTIONIST')")
    public ResponseEntity<String> invoice(@AuthenticationPrincipal AuthenticatedAccount actor, @PathVariable UUID invoiceId) {
        return attachment(reads.invoiceExport(actor, invoiceId), "invoice-" + invoiceId + ".txt");
    }
    @GetMapping("/payments/{paymentId}/receipt")
    @PreAuthorize("hasAuthority('ROLE_RECEPTIONIST')")
    public ResponseEntity<String> receipt(@AuthenticationPrincipal AuthenticatedAccount actor, @PathVariable UUID paymentId) {
        return attachment(reads.receiptExport(actor, paymentId), "receipt-" + paymentId + ".txt");
    }
    private static ResponseEntity<String> attachment(String content, String filename) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(new MediaType("text", "plain", java.nio.charset.StandardCharsets.UTF_8))
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString()).body(content);
    }
}
