package fu.fa26.sep490.g58.otcms_be.visit.controller;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.ReceptionVisitPage;
import fu.fa26.sep490.g58.otcms_be.visit.service.ReceptionVisitQueryService;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
@RestController
public class ReceptionVisitQueryController {
    private final ReceptionVisitQueryService visits;
    public ReceptionVisitQueryController(ReceptionVisitQueryService visits) { this.visits = visits; }
    @GetMapping("/api/v1/visits")
    @PreAuthorize("hasAnyAuthority('ROLE_RECEPTIONIST','ROLE_ADMIN')")
    public ResponseEntity<ReceptionVisitPage> list(@AuthenticationPrincipal AuthenticatedAccount actor,
            @RequestParam(required = false) UUID patientId, @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(visits.list(actor, patientId, status, page, size));
    }
}
