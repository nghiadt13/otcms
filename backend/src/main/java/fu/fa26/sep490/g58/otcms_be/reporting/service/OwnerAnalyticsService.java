package fu.fa26.sep490.g58.otcms_be.reporting.service;

import fu.fa26.sep490.g58.otcms_be.reporting.model.ReportingPeriod;
import fu.fa26.sep490.g58.otcms_be.reporting.model.dto.OwnerAnalyticsDtos.OwnerAnalyticsResponse;

/** Economic overview for the clinic Owner: collected revenue, bookings, arrivals, doctors and services. */
public interface OwnerAnalyticsService {
    OwnerAnalyticsResponse analytics(ReportingPeriod period);
}
