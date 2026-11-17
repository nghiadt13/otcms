package fu.fa26.sep490.g58.otcms_be.billing.controller;

import fu.fa26.sep490.g58.otcms_be.billing.service.BillingDetailService;
import fu.fa26.sep490.g58.otcms_be.billing.service.BillingDetailService.PatientBilling;
import fu.fa26.sep490.g58.otcms_be.billing.service.BillingDetailService.PaymentDetail;
import fu.fa26.sep490.g58.otcms_be.billing.service.BillingDetailService.UnpaidItem;
import fu.fa26.sep490.g58.otcms_be.billing.service.BillingReadService.ResultPage;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** UC-53 patient billing, UC-56 payment detail, UC-57 unpaid items, UC-58 receipt (JSON and PDF). */
@RestController
@RequestMapping("/api/v1")
public class BillingDetailController {
    private final BillingDetailService details;
    public BillingDetailController(BillingDetailService details) { this.details = details; }

    @GetMapping("/reception/patients/{profileId}/billing")
    @PreAuthorize("hasAnyAuthority('ROLE_RECEPTIONIST','ROLE_ADMIN','ROLE_OWNER')")
    public ResponseEntity<PatientBilling> patientBilling(@AuthenticationPrincipal AuthenticatedAccount actor,
            @PathVariable UUID profileId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return noStore(details.patientBilling(actor, profileId, page, size));
    }

    @GetMapping("/invoice-items/unpaid")
    @PreAuthorize("hasAnyAuthority('ROLE_RECEPTIONIST','ROLE_ADMIN','ROLE_OWNER')")
    public ResponseEntity<ResultPage<UnpaidItem>> unpaidItems(@AuthenticationPrincipal AuthenticatedAccount actor,
            @RequestParam(required = false) UUID patientId, @RequestParam(required = false) UUID serviceId,
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return noStore(details.unpaidItems(actor, patientId, serviceId, from, to, page, size));
    }

    @GetMapping("/payments/{paymentId}")
    @PreAuthorize("hasAnyAuthority('ROLE_PATIENT','ROLE_RECEPTIONIST','ROLE_ADMIN','ROLE_OWNER')")
    public ResponseEntity<PaymentDetail> payment(@AuthenticationPrincipal AuthenticatedAccount actor,
            @PathVariable UUID paymentId) {
        return noStore(details.payment(actor, paymentId));
    }

    @GetMapping("/payments/{paymentId}/receipt")
    @PreAuthorize("hasAuthority('ROLE_RECEPTIONIST')")
    public ResponseEntity<PaymentDetail> receipt(@AuthenticationPrincipal AuthenticatedAccount actor,
            @PathVariable UUID paymentId) {
        return noStore(details.receipt(actor, paymentId));
    }

    @GetMapping(value = "/payments/{paymentId}/receipt.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAuthority('ROLE_RECEPTIONIST')")
    public ResponseEntity<byte[]> receiptPdf(@AuthenticationPrincipal AuthenticatedAccount actor,
            @PathVariable UUID paymentId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_PDF)
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename("receipt-" + paymentId + ".pdf").build().toString())
                .body(details.receiptPdf(actor, paymentId));
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
