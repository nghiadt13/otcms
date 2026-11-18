package fu.fa26.sep490.g58.otcms_be.reporting.model;

import java.time.Instant;
import java.util.List;

/**
 * A compiled report. APPOINTMENTS, VISITS and IMAGING fill {@code daily}; SERVICES fills {@code services}; PAYMENTS,
 * PENDING_CANCELLED_ORDERS and REVIEWS fill {@code rows}. {@code total} is the count total, {@code totalAmount}
 * the VND total when the topic has money (else null).
 */
public record ReportResult(
    String subject,
    ReportingPeriod period,
    String breakdown,
    Instant generatedAt,
    List<DailyCount> daily,
    List<ServiceUsage> services,
    List<ReportRow> rows,
    long total,
    Long totalAmount
) {
}
