package fu.fa26.sep490.g58.otcms_be.reporting.controller;

import fu.fa26.sep490.g58.otcms_be.reporting.exception.ReportingOperationException;
import fu.fa26.sep490.g58.otcms_be.reporting.mapper.ReportCsvWriter;
import fu.fa26.sep490.g58.otcms_be.reporting.mapper.ReportingDtoMapper;
import fu.fa26.sep490.g58.otcms_be.reporting.model.ReportingPeriod;
import fu.fa26.sep490.g58.otcms_be.reporting.model.dto.ReportingDtos.CreateReportRequest;
import fu.fa26.sep490.g58.otcms_be.reporting.model.dto.ReportingDtos.DashboardResponse;
import fu.fa26.sep490.g58.otcms_be.reporting.model.dto.ReportingDtos.ReportResponse;
import fu.fa26.sep490.g58.otcms_be.reporting.model.dto.ReportingDtos.SubjectsResponse;
import fu.fa26.sep490.g58.otcms_be.reporting.service.ReportingService;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_OWNER')")
public class ReportingController {
    private static final MediaType CSV = new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8);
    private final ReportingService reporting;
    private final jakarta.validation.Validator validator;
    public ReportingController(ReportingService reporting, jakarta.validation.Validator validator) {
        this.reporting = reporting;
        this.validator = validator;
    }
    /** No from/to means today (clinic time); only one of them is an invalid period. */
    @GetMapping("/dashboard")
    public DashboardResponse dashboard(@RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return ReportingDtoMapper.dashboard(reporting.dashboard(period(from, to)));
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
    /** UC-78 step 7: the same report as {@code POST /reports}, as a CSV file (UTF-8 with BOM, opens in Excel). */
    @GetMapping("/reports/export")
    public ResponseEntity<byte[]> export(@RequestParam String subject, @RequestParam(required = false) String from,
            @RequestParam(required = false) String to, @RequestParam(required = false) String breakdown,
            @RequestParam(defaultValue = "csv") String format) {
        if (!"csv".equals(format)) {
            throw new ReportingOperationException(ReportingOperationException.Reason.INVALID_REPORT);
        }
        var period = period(from, to);
        var report = ReportingDtoMapper.report(reporting.report(subject, period, breakdown));
        String filename = "report-" + subject.toLowerCase(java.util.Locale.ROOT) + "-" + period.from() + "-" + period.to() + ".csv";
        return ResponseEntity.ok()
            .contentType(CSV)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
            .cacheControl(CacheControl.noStore())
            .body(ReportCsvWriter.write(report));
    }
    private ReportingPeriod period(String from, String to) {
        if (from == null && to == null) {
            return reporting.today();
        }
        return ReportingPeriod.parse(from, to);
    }
}
