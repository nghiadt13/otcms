package fu.fa26.sep490.g58.otcms_be.reporting.model;

/**
 * One generic report row for the topics that are not a plain daily count: payments (label = date, receptionist or
 * method), pending/cancelled orders (label = group code) and reviews (label = date). {@code amount} is whole VND and
 * {@code average} a 1-5 rating; both are null when the topic has no such column.
 */
public record ReportRow(String label, long count, Long amount, Double average) {
}
