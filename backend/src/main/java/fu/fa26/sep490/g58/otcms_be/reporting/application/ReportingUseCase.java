package fu.fa26.sep490.g58.otcms_be.reporting.application;

import fu.fa26.sep490.g58.otcms_be.reporting.domain.ReportingPeriod;
import java.util.List;
import java.util.Map;

/** Read-only input port shared by dashboard and report HTTP adapters. */
public interface ReportingUseCase {
    byte[] exportPdf(String subject, ReportingPeriod period, String breakdown);
    Map<String, Object> dashboard(ReportingPeriod period);
    List<Map<String, Object>> subjects();
    Map<String, Object> report(String subject, ReportingPeriod period, String breakdown);
}
