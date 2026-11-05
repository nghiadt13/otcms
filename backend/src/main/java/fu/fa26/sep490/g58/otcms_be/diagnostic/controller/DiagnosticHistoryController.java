package fu.fa26.sep490.g58.otcms_be.diagnostic.controller;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.diagnostic.mapper.DiagnosticDtoMapper;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.dto.DiagnosticDtos.ImagingHistoryPage;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/imaging-history")
@Validated
@PreAuthorize("isAuthenticated()")
public class DiagnosticHistoryController {
    private final DiagnosticHistoryService history;
    private final DiagnosticDtoMapper mapper;

    public DiagnosticHistoryController(DiagnosticHistoryService history, DiagnosticDtoMapper mapper) {
        this.history = history;
        this.mapper = mapper;
    }

    @GetMapping
    @Operation(operationId = "getImagingHistory", summary = "Get actor-authorized imaging result history")
    public org.springframework.http.ResponseEntity<ImagingHistoryPage> getHistory(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedAccount principal,
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) UUID visitId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(mapper.toImagingHistoryPage(history.getImagingHistory(
                DiagnosticActor.user(principal.accountId(), principal.accountRole()), patientId, visitId, page, size)));
    }
}
