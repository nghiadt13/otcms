package fu.fa26.sep490.g58.otcms_be.reporting.model;

public record ServiceComparison(String serviceId, String name, String category, long value, Long previousValue) {
}
