package fu.fa26.sep490.g58.otcms_be.clinical.controller;

import fu.fa26.sep490.g58.otcms_be.clinical.mapper.ClinicalApiMapper;
import fu.fa26.sep490.g58.otcms_be.clinical.model.dto.ClinicalDtos.ExaminationRequest;
import fu.fa26.sep490.g58.otcms_be.clinical.model.dto.ClinicalDtos.ExaminationResponse;
import fu.fa26.sep490.g58.otcms_be.clinical.security.ClinicalDoctorIdentityResolver;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalExaminationService;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/visits/{visitId}/examination")
public class ClinicalExaminationController {
    private final ClinicalExaminationService examinations;
    private final ClinicalDoctorIdentityResolver doctors;

    public ClinicalExaminationController(ClinicalExaminationService examinations, ClinicalDoctorIdentityResolver doctors) {
        this.examinations = examinations;
        this.doctors = doctors;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_DOCTOR')")
    public ResponseEntity<ExaminationResponse> get(@PathVariable UUID visitId,
            @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest request) {
        return ok(examinations.getExamination(visitId, doctors.requireDoctorProfile(account)), request);
    }

    @PutMapping
    @PreAuthorize("hasAuthority('ROLE_DOCTOR')")
    public ResponseEntity<ExaminationResponse> save(@PathVariable UUID visitId,
            @Valid @RequestBody ExaminationRequest body,
            @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest request) {
        return ok(examinations.saveDraft(ClinicalApiMapper.command(visitId, doctors.requireDoctorProfile(account), body)), request);
    }

    @PostMapping("/completion")
    @PreAuthorize("hasAuthority('ROLE_DOCTOR')")
    public ResponseEntity<ExaminationResponse> complete(@PathVariable UUID visitId,
            @Valid @RequestBody ExaminationRequest body,
            @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest request) {
        return ok(examinations.completeExamination(
                ClinicalApiMapper.command(visitId, doctors.requireDoctorProfile(account), body)), request);
    }

    private static ResponseEntity<ExaminationResponse> ok(ClinicalExaminationService.ExaminationView view,
            HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(CorrelationIdFilter.HEADER, CorrelationIdFilter.current(request))
                .body(ClinicalApiMapper.response(view));
    }
}
