package fu.fa26.sep490.g58.otcms_be.clinical.controller;

import fu.fa26.sep490.g58.otcms_be.clinical.security.ClinicalDoctorIdentityResolver;
import fu.fa26.sep490.g58.otcms_be.clinical.service.DoctorLaboratoryResultService;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class DoctorLaboratoryResultController {
    private final DoctorLaboratoryResultService results;
    private final ClinicalDoctorIdentityResolver doctors;
    public DoctorLaboratoryResultController(DoctorLaboratoryResultService results, ClinicalDoctorIdentityResolver doctors) { this.results = results; this.doctors = doctors; }
    @GetMapping("/api/v1/visits/{visitId}/laboratory-results")
    @PreAuthorize("hasAuthority('ROLE_DOCTOR')")
    public ResponseEntity<?> list(@PathVariable UUID visitId, @AuthenticationPrincipal AuthenticatedAccount actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("items", results.list(visitId, doctors.requireDoctorProfile(actor))));
    }
    @GetMapping("/api/v1/visits/{visitId}/laboratory-results/{orderId}/report")
    @PreAuthorize("hasAuthority('ROLE_DOCTOR')")
    public ResponseEntity<org.springframework.core.io.InputStreamResource> report(@PathVariable UUID visitId, @PathVariable UUID orderId,
            @AuthenticationPrincipal AuthenticatedAccount actor) {
        var report = results.report(visitId, orderId, doctors.requireDoctorProfile(actor));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(org.springframework.http.MediaType.parseMediaType(report.contentType()))
                .header("X-Content-Type-Options", "nosniff").header("Content-Disposition", "attachment; filename=lab-report")
                .body(new org.springframework.core.io.InputStreamResource(report.content()));
    }
    @PostMapping("/api/v1/visits/{visitId}/laboratory-results/{orderId}/assessment")
    @PreAuthorize("hasAuthority('ROLE_DOCTOR')")
    public ResponseEntity<DoctorLaboratoryResultService.Assessment> assess(@PathVariable UUID visitId, @PathVariable UUID orderId,
            @Valid @RequestBody AssessmentRequest body, @AuthenticationPrincipal AuthenticatedAccount actor) {
        var result = results.assess(visitId, orderId, doctors.requireDoctorProfile(actor), actor.accountId(), body.assessment());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }
    public record AssessmentRequest(@NotBlank @Size(max = 4000) String assessment) { }
}
