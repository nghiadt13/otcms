package fu.fa26.sep490.g58.otcms_be.clinical.controller;

import fu.fa26.sep490.g58.otcms_be.clinical.model.dto.DoctorActiveVisitDtos.ActiveVisits;
import fu.fa26.sep490.g58.otcms_be.clinical.service.DoctorActiveVisitReadService;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DoctorActiveVisitController {
    private final DoctorActiveVisitReadService visits;

    public DoctorActiveVisitController(DoctorActiveVisitReadService visits) { this.visits = visits; }

    @GetMapping("/api/v1/doctor/visits/active")
    @PreAuthorize("hasAuthority('ROLE_DOCTOR')")
    public ResponseEntity<ActiveVisits> active(@AuthenticationPrincipal AuthenticatedAccount account,
            HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(CorrelationIdFilter.HEADER, CorrelationIdFilter.current(request))
                .body(visits.findActiveVisits(account));
    }
}
