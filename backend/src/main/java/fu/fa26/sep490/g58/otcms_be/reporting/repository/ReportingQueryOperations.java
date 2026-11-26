package fu.fa26.sep490.g58.otcms_be.reporting.repository;

import fu.fa26.sep490.g58.otcms_be.reporting.model.DailyCount;
import fu.fa26.sep490.g58.otcms_be.reporting.model.DashboardSections.ReviewStats;
import fu.fa26.sep490.g58.otcms_be.reporting.model.DashboardSections.RoomWaiting;
import fu.fa26.sep490.g58.otcms_be.reporting.model.ReportRow;
import fu.fa26.sep490.g58.otcms_be.reporting.model.ReportingPeriod;
import fu.fa26.sep490.g58.otcms_be.reporting.model.ServiceCount;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface ReportingQueryOperations {
    Map<String, Long> appointmentCounts(ReportingPeriod period);
    Map<String, Long> visitCounts(ReportingPeriod period);
    List<DailyCount> daily(String subject, ReportingPeriod period);
    List<ServiceCount> services(ReportingPeriod period);
    long imagingPerformed(ReportingPeriod period);
    Map<String, Long> paymentsByMethod(ReportingPeriod period);
    Map<String, Long> orderCounts(ReportingPeriod period);
    long refundPendingItems();
    ReviewStats reviewStats(ReportingPeriod period);
    long pendingReviews();
    List<RoomWaiting> waitingByRoom(LocalDate today);
    List<ReportRow> paymentRows(ReportingPeriod period, String breakdown);
    List<ReportRow> orderRows(ReportingPeriod period);
    List<ReportRow> reviewRows(ReportingPeriod period);
}
