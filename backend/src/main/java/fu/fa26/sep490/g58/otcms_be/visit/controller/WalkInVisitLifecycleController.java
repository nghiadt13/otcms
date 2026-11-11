package fu.fa26.sep490.g58.otcms_be.visit.controller;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.service.WalkInVisitLifecycleService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WalkInVisitLifecycleController {
    private final WalkInVisitLifecycleService lifecycle;

    public WalkInVisitLifecycleController(WalkInVisitLifecycleService lifecycle) {
        this.lifecycle = lifecycle;
    }

    @PostMapping("/api/v1/visits/{visitId}/cancellation")
    @PreAuthorize("hasAuthority('ROLE_RECEPTIONIST')")
    public ResponseEntity<Void> cancel(@PathVariable UUID visitId,
            @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest request) {
        if (account == null) throw new VisitOperationException("ACCESS_DENIED");
        lifecycle.cancel(visitId, account.accountId(), account.accountRole(), CorrelationIdFilter.current(request));
        return ResponseEntity.noContent().header(CorrelationIdFilter.HEADER,
                CorrelationIdFilter.current(request)).build();
    }

    @PostMapping("/api/v1/visits/{visitId}/closure")
    @PreAuthorize("hasAuthority('ROLE_DOCTOR')")
    public ResponseEntity<Void> close(@PathVariable UUID visitId,
            @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest request) {
        if (account == null) throw new VisitOperationException("ACCESS_DENIED");
        lifecycle.close(visitId, account.accountId(), account.accountRole(), CorrelationIdFilter.current(request));
        return ResponseEntity.noContent().header(CorrelationIdFilter.HEADER,
                CorrelationIdFilter.current(request)).build();
    }
}
