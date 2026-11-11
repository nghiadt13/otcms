package fu.fa26.sep490.g58.otcms_be.diagnostic.controller;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import fu.fa26.sep490.g58.otcms_be.diagnostic.mapper.DiagnosticDtoMapper;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticReportUpload;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticFindingsReleaseView;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticFindingsView;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticReportView;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticRequestView;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.dto.request.CompleteFindingsRequest;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.dto.request.FindingsCorrectionRequest;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.dto.request.ReviewStudyRequest;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.dto.request.SaveFindingsDraftRequest;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.dto.response.DiagnosticReportResponse;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.dto.response.FindingsReleaseStatusResponse;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.dto.response.FindingsVersionResponse;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.dto.DiagnosticDtos.ImagingRequest;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticFindingsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/imaging-requests/{requestId}")
@Validated
@PreAuthorize("isAuthenticated()")
public class DiagnosticFindingsController {
    private final DiagnosticFindingsService findings;
    private final DiagnosticDtoMapper mapper;

    public DiagnosticFindingsController(DiagnosticFindingsService findings, DiagnosticDtoMapper mapper) {
        this.findings = findings;
        this.mapper = mapper;
    }

    @PostMapping("/reviews")
    public ImagingRequest review(@AuthenticationPrincipal AuthenticatedAccount principal,
            @PathVariable UUID requestId, @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Expected-Version") @Min(0) long expectedVersion,
            @Valid @RequestBody ReviewStudyRequest body, HttpServletRequest request) {
        DiagnosticRequestView view = findings.review(actor(principal), requestId, idempotencyKey,
                expectedVersion, body.studyRef(), CorrelationIdFilter.current(request));
        return mapper.toImagingRequest(view);
    }

    @GetMapping("/findings/current")
    public ResponseEntity<FindingsVersionResponse> getCurrent(
            @AuthenticationPrincipal AuthenticatedAccount principal,
            @PathVariable UUID requestId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(mapper.toFindingsVersion(findings.getCurrent(actor(principal), requestId)));
    }

    @PostMapping("/findings")
    public FindingsVersionResponse saveDraft(@AuthenticationPrincipal AuthenticatedAccount principal,
            @PathVariable UUID requestId, @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Expected-Version") @Min(0) long expectedVersion,
            @Valid @RequestBody SaveFindingsDraftRequest body, HttpServletRequest request) {
        DiagnosticFindingsView view = findings.saveDraft(actor(principal), requestId, idempotencyKey,
                expectedVersion, body.content(), CorrelationIdFilter.current(request));
        return mapper.toFindingsVersion(view);
    }

    @PostMapping("/findings/corrections")
    public FindingsVersionResponse createCorrection(@AuthenticationPrincipal AuthenticatedAccount principal,
            @PathVariable UUID requestId, @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Expected-Version") @Min(0) long expectedVersion,
            @Valid @RequestBody FindingsCorrectionRequest body, HttpServletRequest request) {
        DiagnosticFindingsView view = findings.createCorrection(actor(principal), requestId, idempotencyKey,
                expectedVersion, body.content(), body.correctionReason(), CorrelationIdFilter.current(request));
        return mapper.toFindingsVersion(view);
    }

    @PostMapping(value = "/reports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DiagnosticReportResponse uploadReport(@AuthenticationPrincipal AuthenticatedAccount principal,
            @PathVariable UUID requestId, @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestParam @Min(0) long expectedVersion, @RequestPart("file") MultipartFile file,
            HttpServletRequest request) {
        DiagnosticReportView view = findings.uploadReport(actor(principal), requestId, idempotencyKey,
                expectedVersion, new DiagnosticReportUpload(file::getInputStream, file.getSize(), file.getContentType()),
                CorrelationIdFilter.current(request));
        return mapper.toReport(view);
    }

    @PostMapping("/findings/completions")
    public FindingsReleaseStatusResponse complete(@AuthenticationPrincipal AuthenticatedAccount principal,
            @PathVariable UUID requestId, @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Expected-Version") @Min(0) long expectedVersion,
            @Valid @RequestBody CompleteFindingsRequest body, HttpServletRequest request) {
        DiagnosticFindingsReleaseView view = findings.complete(actor(principal), requestId, idempotencyKey,
                expectedVersion, body.findingsVersion(), body.reportId(), body.studyRef(),
                CorrelationIdFilter.current(request));
        return mapper.toReleaseStatus(view);
    }

    private DiagnosticActor actor(AuthenticatedAccount principal) {
        return DiagnosticActor.user(principal.accountId(), principal.accountRole());
    }
}
