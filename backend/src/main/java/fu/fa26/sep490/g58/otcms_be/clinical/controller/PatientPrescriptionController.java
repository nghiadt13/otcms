package fu.fa26.sep490.g58.otcms_be.clinical.controller;

import fu.fa26.sep490.g58.otcms_be.clinical.service.PatientPrescriptionQueryService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.PatientPrescriptionQueryService.*;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/prescriptions")
@PreAuthorize("hasAuthority('ROLE_PATIENT')")
public class PatientPrescriptionController {
    private final PatientPrescriptionQueryService prescriptions;
    public PatientPrescriptionController(PatientPrescriptionQueryService prescriptions) { this.prescriptions = prescriptions; }

    @GetMapping
    public ResponseEntity<PrescriptionPage> list(@AuthenticationPrincipal AuthenticatedAccount account,
            @RequestParam(required = false) UUID patientId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, HttpServletRequest request) {
        return response(prescriptions.list(account, patientId, page, size), request);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Detail> detail(@AuthenticationPrincipal AuthenticatedAccount account, @PathVariable UUID id,
            HttpServletRequest request) {
        return response(prescriptions.detail(account, id), request);
    }

    private static <T> ResponseEntity<T> response(T body, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(CorrelationIdFilter.HEADER, CorrelationIdFilter.current(request)).body(body);
    }
}
