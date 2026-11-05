package fu.fa26.sep490.g58.otcms_be.clinical.controller;

import fu.fa26.sep490.g58.otcms_be.clinical.mapper.ClinicalApiMapper;
import fu.fa26.sep490.g58.otcms_be.clinical.model.dto.ClinicalDtos.AdviceRequest;
import fu.fa26.sep490.g58.otcms_be.clinical.model.dto.ClinicalDtos.AdviceResponse;
import fu.fa26.sep490.g58.otcms_be.clinical.security.ClinicalDoctorIdentityResolver;
import fu.fa26.sep490.g58.otcms_be.clinical.service.AfterVisitAdviceService;
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
@RequestMapping("/api/v1/visits/{visitId}/after-visit-advice")
public class AfterVisitAdviceController {
    private final AfterVisitAdviceService advice;
    private final ClinicalDoctorIdentityResolver doctors;

    public AfterVisitAdviceController(AfterVisitAdviceService advice, ClinicalDoctorIdentityResolver doctors) {
        this.advice = advice;
        this.doctors = doctors;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_DOCTOR')")
    public ResponseEntity<AdviceResponse> get(@PathVariable UUID visitId,
            @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest request) {
        return ok(advice.getAdvice(visitId, doctors.requireDoctorProfile(account)), request);
    }

    @PutMapping
    @PreAuthorize("hasAuthority('ROLE_DOCTOR')")
    public ResponseEntity<AdviceResponse> save(@PathVariable UUID visitId, @Valid @RequestBody AdviceRequest body,
            @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest request) {
        return ok(advice.recordAdvice(ClinicalApiMapper.command(visitId, doctors.requireDoctorProfile(account), body)), request);
    }

    private static ResponseEntity<AdviceResponse> ok(AfterVisitAdviceService.AdviceView view, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(CorrelationIdFilter.HEADER, CorrelationIdFilter.current(request))
                .body(ClinicalApiMapper.response(view));
    }
}
