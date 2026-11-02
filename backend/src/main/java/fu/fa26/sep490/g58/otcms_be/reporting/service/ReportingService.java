package fu.fa26.sep490.g58.otcms_be.reporting.service;

import fu.fa26.sep490.g58.otcms_be.reporting.model.DashboardSummary;
import fu.fa26.sep490.g58.otcms_be.reporting.model.ReportResult;
import fu.fa26.sep490.g58.otcms_be.reporting.model.ReportingPeriod;
import java.util.List;

public interface ReportingService {
    DashboardSummary dashboard(ReportingPeriod period);
    List<String> subjects();
    ReportResult report(String subject, ReportingPeriod period, String breakdown);
}
