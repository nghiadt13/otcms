package fu.fa26.sep490.g58.otcms_be.billing.model.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PayableDiagnosticOrderResponse(UUID orderId, String orderCode, UUID visitId,
        UUID patientProfileId, UUID serviceId, String serviceName, String clinicalReason, String bodyArea,
        BigDecimal unitPrice, Instant orderedAt, UUID invoiceId) { }
