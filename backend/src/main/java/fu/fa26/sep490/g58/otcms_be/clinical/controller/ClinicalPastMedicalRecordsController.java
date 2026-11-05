package fu.fa26.sep490.g58.otcms_be.clinical.controller;

import fu.fa26.sep490.g58.otcms_be.clinical.model.dto.PastMedicalRecordsDtos.HistoryResponse;
import fu.fa26.sep490.g58.otcms_be.clinical.security.ClinicalDoctorIdentityResolver;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalPastMedicalRecordsService;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/visits/{currentVisitId}/past-medical-records")
public class ClinicalPastMedicalRecordsController {
    private final ClinicalPastMedicalRecordsService history;
    private final ClinicalDoctorIdentityResolver doctors;
    public ClinicalPastMedicalRecordsController(ClinicalPastMedicalRecordsService history, ClinicalDoctorIdentityResolver doctors) {
        this.history = history; this.doctors = doctors;
    }
    @GetMapping @PreAuthorize("hasAuthority('ROLE_DOCTOR')")
    public ResponseEntity<HistoryResponse> get(@PathVariable UUID currentVisitId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest request) {
        UUID doctorId = doctors.requireDoctorProfile(account);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(CorrelationIdFilter.HEADER, CorrelationIdFilter.current(request))
                .body(history.getPastMedicalRecords(currentVisitId, doctorId, account.accountId(), page, size));
    }
}
