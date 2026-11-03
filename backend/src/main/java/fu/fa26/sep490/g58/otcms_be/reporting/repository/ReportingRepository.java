package fu.fa26.sep490.g58.otcms_be.reporting.repository;

import fu.fa26.sep490.g58.otcms_be.reporting.model.DailyCount;
import fu.fa26.sep490.g58.otcms_be.reporting.model.ReportingPeriod;
import fu.fa26.sep490.g58.otcms_be.reporting.model.ServiceCount;
import java.util.List;
import java.util.Map;

public interface ReportingRepository {
    Map<String, Long> appointmentCounts(ReportingPeriod period);
    Map<String, Long> visitCounts(ReportingPeriod period);
    List<DailyCount> daily(String subject, ReportingPeriod period);
    List<ServiceCount> services(ReportingPeriod period);
}
