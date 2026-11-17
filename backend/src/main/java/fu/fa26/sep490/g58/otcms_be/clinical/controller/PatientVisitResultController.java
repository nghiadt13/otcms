package fu.fa26.sep490.g58.otcms_be.clinical.controller;

import fu.fa26.sep490.g58.otcms_be.clinical.model.dto.PatientVisitResultDtos.VisitResult;
import fu.fa26.sep490.g58.otcms_be.clinical.model.dto.PatientVisitResultDtos.VisitResultPage;
import fu.fa26.sep490.g58.otcms_be.clinical.service.PatientVisitResultQueryService;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@PreAuthorize("hasAuthority('ROLE_PATIENT')")
public class PatientVisitResultController {
    private final PatientVisitResultQueryService results;

    public PatientVisitResultController(PatientVisitResultQueryService results) { this.results = results; }

    @GetMapping("/api/v1/visits/results")
    public ResponseEntity<VisitResultPage> list(@RequestParam(required = false) UUID patientId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest request) {
        return response(results.list(account.accountId(), patientId, page, size), request);
    }

    @GetMapping("/api/v1/visits/{visitId}/results")
    public ResponseEntity<VisitResult> get(@PathVariable UUID visitId,
            @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest request) {
        return response(results.get(account.accountId(), visitId), request);
    }

    @GetMapping("/api/v1/visits/{visitId}/results/laboratory/{orderId}/report")
    public ResponseEntity<org.springframework.core.io.InputStreamResource> laboratoryReport(
            @PathVariable UUID visitId, @PathVariable UUID orderId,
            @AuthenticationPrincipal AuthenticatedAccount account) {
        var report = results.getLaboratoryReport(account.accountId(), visitId, orderId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(report.contentType()))
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Disposition", "attachment; filename=laboratory-report")
                .body(new org.springframework.core.io.InputStreamResource(
                        new java.io.ByteArrayInputStream(report.content())));
    }

    private static <T> ResponseEntity<T> response(T body, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(CorrelationIdFilter.HEADER, CorrelationIdFilter.current(request)).body(body);
    }
}
