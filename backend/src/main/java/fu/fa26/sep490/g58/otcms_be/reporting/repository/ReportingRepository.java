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

/** Read-only aggregate queries; every method only counts, nothing is written (UC-77 postcondition). */
public interface ReportingRepository {
    Map<String, Long> appointmentCounts(ReportingPeriod period);
    Map<String, Long> visitCounts(ReportingPeriod period);
    /** Daily counts for APPOINTMENTS, VISITS and IMAGING. */
    List<DailyCount> daily(String subject, ReportingPeriod period);
    List<ServiceCount> services(ReportingPeriod period);

    /** Imaging orders whose capture was performed (result available or reviewed) in the period. */
    long imagingPerformed(ReportingPeriod period);
    /** Confirmed payments in the period: method code (CASH, PAYOS) to whole VND. */
    Map<String, Long> paymentsByMethod(ReportingPeriod period);
    /** Orders created in the period: PENDING_PAYMENT and CANCELLED counts. */
    Map<String, Long> orderCounts(ReportingPeriod period);
    /** Invoice items waiting for a manual refund (REFUND_PENDING), all time. */
    long refundPendingItems();
    ReviewStats reviewStats(ReportingPeriod period);
    long pendingReviews();
    /** Rooms with people waiting now, for the clinic date {@code today}. */
    List<RoomWaiting> waitingByRoom(LocalDate today);

    /** Payments report rows; {@code breakdown} is DATE, RECEPTIONIST or METHOD (validated by the caller). */
    List<ReportRow> paymentRows(ReportingPeriod period, String breakdown);
    /** PENDING_PAYMENT, CANCELLED and REFUND_PENDING groups with count and VND amount. */
    List<ReportRow> orderRows(ReportingPeriod period);
    /** Reviews per date with count and mean rating (PUBLISHED reviews only). */
    List<ReportRow> reviewRows(ReportingPeriod period);
}
