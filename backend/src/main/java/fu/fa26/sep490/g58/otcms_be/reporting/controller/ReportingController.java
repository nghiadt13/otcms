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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_OWNER')")
public class ReportingController {
    private final ReportingService reporting;
    private final jakarta.validation.Validator validator;
    public ReportingController(ReportingService reporting, jakarta.validation.Validator validator) {
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
    public ResponseEntity<Void> export() {
        throw new ReportingOperationException(ReportingOperationException.Reason.EXPORT_UNAVAILABLE);
    }
}
