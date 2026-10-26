package fu.fa26.sep490.g58.otcms_be.appointment.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecordManualArrivalRequest(@NotBlank @Size(max = 500) String reason) { }
