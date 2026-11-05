package fu.fa26.sep490.g58.otcms_be.clinical.controller;

import fu.fa26.sep490.g58.otcms_be.clinical.model.dto.PrescriptionDtos.*;
import fu.fa26.sep490.g58.otcms_be.clinical.security.ClinicalDoctorIdentityResolver;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalPrescriptionService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalPrescriptionService.*;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/visits/{visitId}/prescription")
public class ClinicalPrescriptionController {
    private final ClinicalPrescriptionService prescriptions;
    private final ClinicalDoctorIdentityResolver doctors;
    public ClinicalPrescriptionController(ClinicalPrescriptionService prescriptions, ClinicalDoctorIdentityResolver doctors) {
        this.prescriptions = prescriptions; this.doctors = doctors;
    }

    @GetMapping @PreAuthorize("hasAuthority('ROLE_DOCTOR')")
    public ResponseEntity<PrescriptionResponse> get(@PathVariable UUID visitId,
            @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest request) {
        return ok(prescriptions.getPrescription(visitId, doctors.requireDoctorProfile(account)), request);
    }
    @PutMapping @PreAuthorize("hasAuthority('ROLE_DOCTOR')")
    public ResponseEntity<PrescriptionResponse> save(@PathVariable UUID visitId, @Valid @RequestBody DraftRequest body,
            @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest request) {
        return ok(prescriptions.saveDraft(new DraftCommand(visitId, doctors.requireDoctorProfile(account), body.version(),
                body.items().stream().map(item -> new MedicineItem(item.medicine(), item.dose(), item.frequency(), item.usage())).toList())), request);
    }
    @PostMapping("/issuance") @PreAuthorize("hasAuthority('ROLE_DOCTOR')")
    public ResponseEntity<PrescriptionResponse> issue(@PathVariable UUID visitId, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody IssuanceRequest body, @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest request) {
        return ok(prescriptions.issue(new IssuanceCommand(visitId, doctors.requireDoctorProfile(account), body.version(), key)), request);
    }
    private static ResponseEntity<PrescriptionResponse> ok(PrescriptionView view, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(CorrelationIdFilter.HEADER, CorrelationIdFilter.current(request))
                .body(new PrescriptionResponse(view.id(), view.visitId(), view.patientProfileId(), view.status(), view.issuedAt(), view.version(),
                        view.items().stream().map(line -> new ItemResponse(line.lineNumber(), line.medicine(), line.dose(), line.frequency(), line.usage())).toList()));
    }
}
