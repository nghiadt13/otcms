package fu.fa26.sep490.g58.otcms_be.clinical.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import fu.fa26.sep490.g58.otcms_be.clinical.model.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ImagingRequestDtos {
    private ImagingRequestDtos() { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ImagingRequest(@NotEmpty List<@NotNull @Valid ImagingOrderRequest> orders) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ImagingOrderRequest(@NotNull UUID serviceId, @NotBlank String clinicalReason,
            @NotBlank @Size(max = 255) String bodyArea, String instructions) { }

    public record ImagingOrderResponse(UUID orderId, String orderCode, UUID serviceId, String serviceName,
            ClinicalServiceType serviceType, String clinicalReason, String bodyArea, String instructions,
            BigDecimal priceSnapshot, ServiceOrderStatus orderStatus, ServiceOrderPaymentStatus paymentStatus) { }

    public record ImagingRoundResponse(UUID id, UUID visitId, int roundNumber, Instant orderedAt,
            List<ImagingOrderResponse> orders) { }
}
