package fu.fa26.sep490.g58.otcms_be.reporting.model.dto;

import java.time.Instant;
import java.util.List;

/**
 * Response of {@code GET /api/v1/owner/finance}: money collected and refunded, and who refunded what.
 * Money is whole VND. {@code pending} is a snapshot of right now (refunds still owed), not tied to the period.
 */
public final class OwnerFinanceDtos {
    private OwnerFinanceDtos() {
    }

    public record Summary(long collected, long refunded, long refundedCount, long pending, long pendingCount) { }

    /** Who confirmed refunds in the period; {@code name == null} is a refund whose confirmer is unknown. */
    public record RefundByPerson(String name, String role, long count, long amount) { }

    /**
     * One refunded or refund-pending invoice item. {@code paidAt} is when the money was taken;
     * {@code refundedAt} and {@code refundedBy*} are empty while the refund is still pending.
     */
    public record RefundRow(String itemId, String invoiceNumber, String patientName, String description, long amount,
            String status, String reason, Instant paidAt, Instant refundedAt, String refundedByName, String refundedByRole) { }

    public record OwnerFinanceResponse(OwnerAnalyticsDtos.Period period, Summary summary, List<RefundByPerson> byPerson,
            List<RefundRow> refunds) { }
}
