package fu.fa26.sep490.g58.otcms_be.clinical.controller;

import fu.fa26.sep490.g58.otcms_be.clinical.model.dto.ClinicalDtos.AmendableResultResponse;
import fu.fa26.sep490.g58.otcms_be.clinical.model.dto.ClinicalDtos.AmendmentRequest;
import fu.fa26.sep490.g58.otcms_be.clinical.model.dto.ClinicalDtos.AmendmentResponse;
import fu.fa26.sep490.g58.otcms_be.clinical.service.resultsheet.ResultAmendmentService;
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

/** UC-38: amend the result of a closed visit. */
@RestController
public class VisitAmendmentController {
    private final ResultAmendmentService amendments;
    public VisitAmendmentController(ResultAmendmentService amendments) { this.amendments = amendments; }

    @PostMapping("/api/v1/visits/{visitId}/amendments")
    @PreAuthorize("hasAuthority('ROLE_DOCTOR')")
    public ResponseEntity<AmendmentResponse> amend(@PathVariable UUID visitId, @Valid @RequestBody AmendmentRequest body,
            @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest request) {
        var result = amendments.amend(account, visitId, body);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(CorrelationIdFilter.HEADER, CorrelationIdFilter.current(request))
                .body(new AmendmentResponse(result.documentId(), result.version()));
    }

    @GetMapping("/api/v1/visits/{visitId}/amendments/current")
    @PreAuthorize("hasAuthority('ROLE_DOCTOR')")
    public ResponseEntity<AmendableResultResponse> current(@PathVariable UUID visitId,
            @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(CorrelationIdFilter.HEADER, CorrelationIdFilter.current(request))
                .body(amendments.current(account, visitId));
    }
}
