package fu.fa26.sep490.g58.otcms_be.reporting.controller;

import fu.fa26.sep490.g58.otcms_be.reporting.model.ReportingPeriod;
import fu.fa26.sep490.g58.otcms_be.reporting.model.dto.OwnerFinanceDtos.OwnerFinanceResponse;
import fu.fa26.sep490.g58.otcms_be.reporting.service.OwnerFinanceService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The Owner's view of money and refunds: who confirmed which refund. Read-only; refunds are confirmed in billing. */
@RestController
@RequestMapping("/api/v1/owner")
@PreAuthorize("hasAuthority('ROLE_OWNER')")
public class OwnerFinanceController {
    private final OwnerFinanceService finance;

    public OwnerFinanceController(OwnerFinanceService finance) {
        this.finance = finance;
    }

    /** Both dates are required (MSG-72 otherwise); at most 90 days. */
    @GetMapping("/finance")
    public ResponseEntity<OwnerFinanceResponse> finance(@RequestParam String from, @RequestParam String to) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(finance.finance(ReportingPeriod.parse(from, to)));
    }
}
