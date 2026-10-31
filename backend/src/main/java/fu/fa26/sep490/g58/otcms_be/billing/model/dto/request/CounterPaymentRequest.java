package fu.fa26.sep490.g58.otcms_be.billing.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CounterPaymentRequest(@NotNull @Positive Long amount, @NotBlank String method) { }
