package fu.fa26.sep490.g58.otcms_be.billing.model.dto.request;

import jakarta.validation.constraints.Size;

/** Body of {@code POST /invoice-items/{id}/refund-confirmation}: optional note of how the patient was paid back. */
public record RefundConfirmationRequest(@Size(max = 500) String note) { }
