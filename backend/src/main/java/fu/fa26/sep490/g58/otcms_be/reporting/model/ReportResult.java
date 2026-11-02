package fu.fa26.sep490.g58.otcms_be.reporting.model;

import java.time.Instant;
import java.util.List;

public record ReportResult(
    String subject,
    ReportingPeriod period,
    String breakdown,
    Instant generatedAt,
    List<DailyCount> daily,
    List<ServiceUsage> services,
    long total
) {
}
