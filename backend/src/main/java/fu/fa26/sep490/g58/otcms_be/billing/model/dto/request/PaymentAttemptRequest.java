package fu.fa26.sep490.g58.otcms_be.billing.model.dto.request;

import fu.fa26.sep490.g58.otcms_be.billing.model.PaymentChannel;
import java.util.List;
import java.util.UUID;

/**
 * Body of {@code POST /invoices/{id}/payment-attempts}. {@code itemIds} are the items to pay online; null or empty
 * means every item that is still payable. {@code channel} is ONLINE (patient, the default) or COUNTER_QR
 * (receptionist showing the PayOS QR at the counter).
 */
public record PaymentAttemptRequest(List<UUID> itemIds, PaymentChannel channel) { }
