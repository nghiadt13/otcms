package fu.fa26.sep490.g58.otcms_be.reporting.model;

import java.util.List;

/** Dashboard blocks added for UC-77 (imaging, payments, orders, reviews, room queues). */
public final class DashboardSections {
    private DashboardSections() {
    }

    /** People waiting (WAITING or CALLED) in one room right now. */
    public record RoomWaiting(String roomId, String roomName, long value) {
    }

    /** Review count and mean rating of PUBLISHED reviews in a period; {@code average} is null without reviews. */
    public record ReviewStats(long count, Double average) {
    }

    /** Collected money (whole VND) in the period, in total and per method code (CASH, PAYOS). */
    public record PaymentsSummary(MetricValue collected, List<StatusMetric> byMethod) {
    }

    /** Orders created in the period waiting for payment or cancelled, plus all items waiting for a manual refund (D7). */
    public record OrdersSummary(MetricValue pendingPayment, MetricValue cancelled, long refundPending) {
    }

    /** Mean rating for the period and the previous one, and every review still waiting for moderation. */
    public record ReviewsSummary(Double average, Double previousAverage, long pendingApproval) {
    }
}
