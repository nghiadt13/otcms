package fu.fa26.sep490.g58.otcms_be.billing.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;

public record CounterPaymentRequest(List<UUID> itemIds, @NotBlank String method) { }
