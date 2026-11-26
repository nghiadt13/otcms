package fu.fa26.sep490.g58.otcms_be.clinical.controller;

import fu.fa26.sep490.g58.otcms_be.clinical.security.ClinicalDoctorIdentityResolver;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalOrderingService;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ClinicalServiceOrderCancellationController {
    private final ClinicalOrderingService ordering;
    private final ClinicalDoctorIdentityResolver doctors;

    public ClinicalServiceOrderCancellationController(ClinicalOrderingService ordering,
            ClinicalDoctorIdentityResolver doctors) {
        this.ordering = ordering;
        this.doctors = doctors;
    }

    public record CancellationRequest(@NotBlank @Size(max = 500) String reason) { }

    @PostMapping("/api/v1/clinical-service-orders/{orderId}/cancellations")
    @PreAuthorize("hasAuthority('ROLE_DOCTOR')")
    public ResponseEntity<ClinicalOrderingService.OrderView> cancelPendingPayment(
            @PathVariable UUID orderId, @Valid @RequestBody CancellationRequest body,
            @AuthenticationPrincipal AuthenticatedAccount account) {
        var cancelled = ordering.cancelPendingPayment(orderId, doctors.requireDoctorProfile(account), body.reason());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(cancelled);
    }
}
