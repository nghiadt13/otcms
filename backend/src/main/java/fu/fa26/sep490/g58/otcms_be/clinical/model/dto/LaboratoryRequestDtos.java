package fu.fa26.sep490.g58.otcms_be.clinical.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public final class LaboratoryRequestDtos {
    private LaboratoryRequestDtos() { }

    public record Request(@NotNull @AssertTrue Boolean patientConsentConfirmed,
            @NotEmpty @Size(max = 200) List<@NotNull @Valid Order> orders) { }
    public record Order(@NotNull UUID serviceId, @NotBlank String clinicalReason, String instructions) { }
}
