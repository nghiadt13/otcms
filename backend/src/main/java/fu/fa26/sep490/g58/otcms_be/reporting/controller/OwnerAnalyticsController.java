package fu.fa26.sep490.g58.otcms_be.reporting.controller;

import fu.fa26.sep490.g58.otcms_be.reporting.model.ReportingPeriod;
import fu.fa26.sep490.g58.otcms_be.reporting.model.dto.OwnerAnalyticsDtos.OwnerAnalyticsResponse;
import fu.fa26.sep490.g58.otcms_be.reporting.service.OwnerAnalyticsService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The Owner's overview only: the Admin operates and monitors, so this is not under {@code /admin}. */
@RestController
@RequestMapping("/api/v1/owner")
@PreAuthorize("hasAuthority('ROLE_OWNER')")
public class OwnerAnalyticsController {
    private final OwnerAnalyticsService analytics;

    public OwnerAnalyticsController(OwnerAnalyticsService analytics) {
        this.analytics = analytics;
    }

    /** Both dates are required (MSG-72 otherwise); at most 90 days. */
    @GetMapping("/analytics")
    public ResponseEntity<OwnerAnalyticsResponse> analytics(@RequestParam String from, @RequestParam String to) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(analytics.analytics(ReportingPeriod.parse(from, to)));
    }
}
