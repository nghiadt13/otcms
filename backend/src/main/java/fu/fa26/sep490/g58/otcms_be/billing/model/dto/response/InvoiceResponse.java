package fu.fa26.sep490.g58.otcms_be.billing.model.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code totalAmount} leaves out VOID items; {@code paidAmount} is the money of CONFIRMED attempts. Each item's
 * {@code status} is PENDING, PAYMENT_PENDING, PAID, VOID, REFUND_PENDING or REFUNDED.
 */
public record InvoiceResponse(UUID id, String invoiceNumber, UUID patientProfileId, UUID visitId,
        UUID appointmentId, String status, BigDecimal totalAmount, BigDecimal paidAmount,
        BigDecimal outstandingBalance, Instant issuedAt, List<Item> items, String sourceType, UUID clinicalOrderingRoundId) {
    public record Item(UUID id, int lineNumber, UUID serviceId, String description, int quantity,
            BigDecimal unitPrice, BigDecimal lineTotal, String status) { }
}
