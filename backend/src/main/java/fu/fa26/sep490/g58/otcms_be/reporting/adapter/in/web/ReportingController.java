package fu.fa26.sep490.g58.otcms_be.reporting.adapter.in.web;

import fu.fa26.sep490.g58.otcms_be.reporting.application.ReportingUseCase;
import fu.fa26.sep490.g58.otcms_be.reporting.domain.ReportingPeriod;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_OWNER')")
public class ReportingController {
    private final ReportingUseCase query;

    public ReportingController(ReportingUseCase query) {
        this.query = query;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(@RequestParam String from, @RequestParam String to) {
        return query.dashboard(ReportingPeriod.parse(from, to));
    }

    @GetMapping("/reports/subjects")
    public Map<String, Object> subjects() {
        return Map.of("items", query.subjects());
    }

    public record Input(String subject, String from, String to, String breakdown) { }

    @PostMapping("/reports")
    public Map<String, Object> report(@RequestBody Input input) {
        if (input == null) throw new IllegalArgumentException("MSG-72");
        return query.report(input.subject(), ReportingPeriod.parse(input.from(), input.to()), input.breakdown());
    }

    @GetMapping("/reports/export")
    public org.springframework.http.ResponseEntity<byte[]> export(@RequestParam String subject,
            @RequestParam String from, @RequestParam String to,
            @RequestParam(required = false) String breakdown, @RequestParam String format) {
        if (!format.equals("pdf")) throw new IllegalArgumentException("MSG-72");
        return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "attachment; filename=\"otcms-" + subject.toLowerCase(java.util.Locale.ROOT)
                        + "-" + from + "-" + to + ".pdf\"")
                .header("Cache-Control", "no-store")
                .header("Access-Control-Expose-Headers", "Content-Disposition, X-Correlation-ID")
                .body(query.exportPdf(subject, ReportingPeriod.parse(from, to), breakdown));
    }

    public static final class ExportUnavailable extends RuntimeException { }
}
