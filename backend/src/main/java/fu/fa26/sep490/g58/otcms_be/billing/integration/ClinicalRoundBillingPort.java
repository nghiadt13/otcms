package fu.fa26.sep490.g58.otcms_be.billing.integration;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Clinical owns patient identity, effective orders and immutable prices. Joins Billing transactions. */
public interface ClinicalRoundBillingPort {
    ClinicalRoundBillingSource requirePayableRound(UUID roundId);
    void markRoundPaid(UUID roundId);
    record ClinicalRoundBillingSource(UUID roundId, UUID visitId, UUID patientId, List<PayableOrder> orders) { }
    record PayableOrder(UUID orderId, UUID serviceId, String serviceNameSnapshot, BigDecimal priceSnapshot) { }
}
