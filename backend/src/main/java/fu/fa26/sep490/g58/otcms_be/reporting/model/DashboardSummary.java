package fu.fa26.sep490.g58.otcms_be.reporting.model;

import java.util.List;

public record DashboardSummary(
    ReportingPeriod period,
    ReportingPeriod previousPeriod,
    ActivityCounts appointments,
    ActivityCounts visits,
    List<ServiceComparison> services
) {
}
