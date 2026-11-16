package fu.fa26.sep490.g58.otcms_be.diagnostic.controller;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.diagnostic.mapper.DiagnosticDtoMapper;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.dto.DiagnosticDtos.ImagingHistoryPage;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticHistoryService;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticReleasedReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/imaging-history")
@Validated
@PreAuthorize("isAuthenticated()")
public class DiagnosticHistoryController {
    private final DiagnosticHistoryService history;
    private final DiagnosticDtoMapper mapper;
    private final DiagnosticReleasedReportService releasedReports;

    @Autowired
    public DiagnosticHistoryController(DiagnosticHistoryService history, DiagnosticDtoMapper mapper,
            DiagnosticReleasedReportService releasedReports) {
        this.history = history;
        this.mapper = mapper;
        this.releasedReports = releasedReports;
    }

    /** Compatibility constructor for direct controller callers that do not expose the report route. */
    public DiagnosticHistoryController(DiagnosticHistoryService history, DiagnosticDtoMapper mapper) {
        this.history = history;
        this.mapper = mapper;
        this.releasedReports = null;
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

    @GetMapping("/visits/{visitId}/reports/{reportId}/content")
    @PreAuthorize("hasAuthority('ROLE_PATIENT')")
    public ResponseEntity<byte[]> viewReleasedReport(@PathVariable UUID visitId, @PathVariable UUID reportId,
                                                     @AuthenticationPrincipal AuthenticatedAccount principal, HttpServletRequest request) {
        var content = releasedReports.readForPatientVisit(
                DiagnosticActor.user(principal.accountId(), principal.accountRole()), visitId, reportId);
        byte[] bytes = content.bytes();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(CorrelationIdFilter.HEADER, CorrelationIdFilter.current(request))
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename("ket-qua-chan-doan.pdf", StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.APPLICATION_PDF).contentLength(bytes.length).body(bytes);
    }
}
