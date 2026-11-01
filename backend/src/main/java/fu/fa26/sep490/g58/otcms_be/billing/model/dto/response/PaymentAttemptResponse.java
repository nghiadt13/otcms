package fu.fa26.sep490.g58.otcms_be.billing.model.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentAttemptResponse(UUID id, UUID invoiceId, String channel, String provider,
        BigDecimal amount, String currency, String status, String checkoutUrl, String qrCode,
        Instant expiresAt, Instant createdAt, Instant confirmedAt, String reconciliationReason) { }
