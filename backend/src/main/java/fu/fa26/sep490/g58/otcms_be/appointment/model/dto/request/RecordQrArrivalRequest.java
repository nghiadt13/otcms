package fu.fa26.sep490.g58.otcms_be.appointment.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecordQrArrivalRequest(@NotBlank @Size(max = 2048) String proof) { }
