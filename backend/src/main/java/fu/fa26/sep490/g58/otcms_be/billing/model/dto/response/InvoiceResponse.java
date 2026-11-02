package fu.fa26.sep490.g58.otcms_be.billing.model.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InvoiceResponse(UUID id, String invoiceNumber, UUID patientProfileId, UUID visitId,
        UUID appointmentId, String status, BigDecimal totalAmount, BigDecimal paidAmount,
        BigDecimal outstandingBalance, Instant issuedAt, List<Item> items) {
    public record Item(int lineNumber, UUID serviceId, String description, int quantity,
            BigDecimal unitPrice, BigDecimal lineTotal) { }
}
