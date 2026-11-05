package fu.fa26.sep490.g58.otcms_be.reporting.controller;

import fu.fa26.sep490.g58.otcms_be.reporting.exception.ReportingOperationException;
import fu.fa26.sep490.g58.otcms_be.reporting.mapper.ReportingDtoMapper;
import fu.fa26.sep490.g58.otcms_be.reporting.model.ReportingPeriod;
import fu.fa26.sep490.g58.otcms_be.reporting.model.dto.ReportingDtos.CreateReportRequest;
import fu.fa26.sep490.g58.otcms_be.reporting.model.dto.ReportingDtos.DashboardResponse;
import fu.fa26.sep490.g58.otcms_be.reporting.model.dto.ReportingDtos.ReportResponse;
import fu.fa26.sep490.g58.otcms_be.reporting.model.dto.ReportingDtos.SubjectsResponse;
import fu.fa26.sep490.g58.otcms_be.reporting.service.ReportingService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ContentDisposition;
import jakarta.validation.Validator;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_OWNER')")
public class ReportingController {
    private final ReportingService reporting;
    private final Validator validator;
    public ReportingController(ReportingService reporting, Validator validator) {
        this.reporting = reporting;
        this.validator = validator;
    }
    @GetMapping("/dashboard")
    public DashboardResponse dashboard(@RequestParam String from, @RequestParam String to) {
        return ReportingDtoMapper.dashboard(reporting.dashboard(ReportingPeriod.parse(from, to)));
    }
    @GetMapping("/reports/subjects")
    public SubjectsResponse subjects() {
        return ReportingDtoMapper.subjects(reporting.subjects());
    }
    @PostMapping("/reports")
    public ReportResponse report(@RequestBody CreateReportRequest input) {
        if (input == null || !validator.validate(input).isEmpty()) {
            throw new ReportingOperationException(ReportingOperationException.Reason.INVALID_REPORT);
        }
        return ReportingDtoMapper.report(reporting.report(input.subject(), ReportingPeriod.parse(input.from(), input.to()), input.breakdown()));
    }
    @GetMapping("/reports/export")
    public ResponseEntity<byte[]> export(@RequestParam String subject, @RequestParam String from,
        @RequestParam String to, @RequestParam(required = false) String breakdown,
        @RequestParam String format) {
        if (!"pdf".equals(format)) {
            throw new ReportingOperationException(ReportingOperationException.Reason.INVALID_REPORT);
        }
        var file = reporting.exportPdf(subject, ReportingPeriod.parse(from, to), breakdown);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(file.fileName()).build().toString())
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .header("X-Content-Type-Options", "nosniff")
            .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Content-Disposition, X-Correlation-ID")
            .body(file.content());
    }
}
