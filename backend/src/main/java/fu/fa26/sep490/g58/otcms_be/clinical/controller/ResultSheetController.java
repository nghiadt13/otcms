package fu.fa26.sep490.g58.otcms_be.clinical.controller;

import fu.fa26.sep490.g58.otcms_be.clinical.service.resultsheet.ResultSheetQueryService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.resultsheet.ResultSheetQueryService.ResultSheet;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class ResultSheetController {
    private final ResultSheetQueryService sheets;
    public ResultSheetController(ResultSheetQueryService sheets) { this.sheets = sheets; }

    @GetMapping("/api/v1/visits/{visitId}/result-sheets")
    @PreAuthorize("hasAnyAuthority('ROLE_DOCTOR','ROLE_RECEPTIONIST','ROLE_PATIENT')")
    public ResponseEntity<Map<String, List<ResultSheet>>> list(@PathVariable UUID visitId,
            @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest request) {
        return response(sheets.list(account, visitId), request);
    }

    @PostMapping("/api/v1/visits/{visitId}/result-sheets/regeneration")
    @PreAuthorize("hasAuthority('ROLE_DOCTOR')")
    public ResponseEntity<Map<String, List<ResultSheet>>> regenerate(@PathVariable UUID visitId,
            @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest request) {
        return response(sheets.regenerate(account, visitId), request);
    }

    private static ResponseEntity<Map<String, List<ResultSheet>>> response(List<ResultSheet> result, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(CorrelationIdFilter.HEADER, CorrelationIdFilter.current(request)).body(Map.of("items", result));
    }
}
