package fu.fa26.sep490.g58.otcms_be.reporting.model.dto;

import java.util.List;

/**
 * Response of {@code GET /api/v1/owner/analytics}. The shape is the frontend contract of the Owner dashboard:
 * money is whole VND, dates are {@code YYYY-MM-DD} in clinic time, and {@code previousValue} is the same
 * measure over the period of equal length right before.
 */
public final class OwnerAnalyticsDtos {
    private OwnerAnalyticsDtos() {
    }

    public record Period(String from, String to) { }

    public record Metric(long value, Long previousValue) { }

    public record DailyRevenue(String date, long cash, long payos, Long previousTotal) { }

    public record Revenue(Metric total, Metric cash, Metric payos, List<DailyRevenue> daily) { }

    public record Funnel(long booked, long arrived, long completed, long cancelled, long noShow) { }

    /** {@code doctorId == null} is the revenue that cannot be traced to a doctor. */
    public record DoctorRow(String doctorId, String name, long visits, long activeDays, long revenue, Long previousRevenue) { }

    public record ServiceRow(String serviceId, String name, String category, long count, long revenue, Long previousRevenue) { }

    public record OwnerAnalyticsResponse(Period period, Period previousPeriod, Revenue revenue, Metric appointments,
            Metric arrivals, Funnel funnel, List<DoctorRow> doctors, List<ServiceRow> services) { }
}
