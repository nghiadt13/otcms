package fu.fa26.sep490.g58.otcms_be.reporting.model;

import fu.fa26.sep490.g58.otcms_be.reporting.model.DashboardSections.OrdersSummary;
import fu.fa26.sep490.g58.otcms_be.reporting.model.DashboardSections.PaymentsSummary;
import fu.fa26.sep490.g58.otcms_be.reporting.model.DashboardSections.ReviewsSummary;
import fu.fa26.sep490.g58.otcms_be.reporting.model.DashboardSections.RoomWaiting;
import java.util.List;

public record DashboardSummary(
    ReportingPeriod period,
    ReportingPeriod previousPeriod,
    ActivityCounts appointments,
    ActivityCounts visits,
    List<ServiceComparison> services,
    MetricValue imagingPerformed,
    PaymentsSummary payments,
    OrdersSummary orders,
    ReviewsSummary reviews,
    List<RoomWaiting> waitingByRoom
) {
}
