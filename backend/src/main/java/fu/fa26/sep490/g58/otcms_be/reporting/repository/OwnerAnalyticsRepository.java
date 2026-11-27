package fu.fa26.sep490.g58.otcms_be.reporting.repository;

import fu.fa26.sep490.g58.otcms_be.reporting.model.ReportingPeriod;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read-only sources of the Owner dashboard. Money is whole VND, net of items already refunded. */
public interface OwnerAnalyticsRepository {
    /** Confirmed money of one clinic day, by method ({@code CASH} = counter, {@code PAYOS} = everything else). */
    record DailyAmount(String date, String method, long amount) { }

    /** {@code doctorId == null}: the money could not be traced to a doctor. */
    record DoctorAmount(UUID doctorId, String name, long amount) { }

    /** Completed visits of a doctor and the number of clinic days with at least one. */
    record DoctorActivity(UUID doctorId, String name, long visits, long activeDays) { }

    record ServiceAmount(UUID serviceId, String name, String category, long count, long amount) { }

    /** Appointment counts of a period: all, arrived (checked in or further), completed, cancelled, no-show. */
    record AppointmentFunnel(long booked, long arrived, long completed, long cancelled, long noShow) { }

    /** Confirmed payments per clinic day and method over {@code [from, until)}; the span may cover two periods. */
    List<DailyAmount> dailyRevenue(Instant from, Instant until);

    List<DoctorAmount> doctorRevenue(ReportingPeriod period);

    List<DoctorActivity> doctorActivity(ReportingPeriod period);

    List<ServiceAmount> serviceRevenue(ReportingPeriod period);

    AppointmentFunnel appointmentFunnel(ReportingPeriod period);

    /** Visits checked in during the period, walk-ins included. */
    long arrivals(ReportingPeriod period);
}
