package fu.fa26.sep490.g58.otcms_be.reporting.model;

import java.util.List;

public record ActivityCounts(MetricValue total, List<StatusMetric> byStatus) {
}
