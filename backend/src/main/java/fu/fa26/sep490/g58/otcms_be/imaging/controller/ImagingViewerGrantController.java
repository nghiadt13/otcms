package fu.fa26.sep490.g58.otcms_be.imaging.controller;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.imaging.service.impl.ImagingViewerGrantService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Creates study-scoped HttpOnly grant cookies; neither the token nor archive URL enters the JSON body. */
@RestController
@RequestMapping("/api/v1/imaging/studies")
public class ImagingViewerGrantController {
    private final ImagingViewerGrantService grants;

    public ImagingViewerGrantController(ImagingViewerGrantService grants) { this.grants = grants; }

    @PostMapping("/{studyRef}/viewer-grants")
    @PreAuthorize("hasAnyAuthority('ROLE_DOCTOR', 'ROLE_PATIENT')")
    public ResponseEntity<ViewerGrantResponse> issue(@PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{1,128}")
            String studyRef, @RequestBody ViewerGrantRequest request,
            @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest servletRequest) {
        if (account == null || request == null
                || (account.accountRole() == AccountRole.DOCTOR && !"DOCTOR_REVIEW".equals(request.purpose()))
                || (account.accountRole() == AccountRole.PATIENT && !"PATIENT_VIEW".equals(request.purpose()))
                || (account.accountRole() != AccountRole.DOCTOR && account.accountRole() != AccountRole.PATIENT)) {
            throw new ImagingOperationException(ImagingOperationOutcome.UNAUTHORIZED_RECORD);
        }
        String correlationId = CorrelationIdFilter.current(servletRequest);
        ImagingViewerGrantService.IssuedGrant grant = grants.issue(account.accountId(), studyRef,
                request.purpose(), correlationId);
        long maxAge = Math.max(1, Duration.between(Instant.now(), grant.expiresAt()).toSeconds());
        ResponseCookie cookie = ResponseCookie.from(grant.cookieName(), grant.token())
                .httpOnly(true).secure(true).sameSite("Strict").path("/dicom-web/")
                .maxAge(Duration.ofSeconds(maxAge)).build();
        return ResponseEntity.status(201).header(HttpHeaders.SET_COOKIE, cookie.toString())
                .cacheControl(CacheControl.noStore()).body(new ViewerGrantResponse(grant.expiresAt()));
    }

    public record ViewerGrantRequest(@Pattern(regexp = "DOCTOR_REVIEW|PATIENT_VIEW") String purpose) { }
    public record ViewerGrantResponse(Instant expiresAt) { }
}
