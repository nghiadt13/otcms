package fu.fa26.sep490.g58.otcms_be.servicecatalog.model.dto.request;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.exception.*;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ServiceRequest(@NotBlank String name, String description, @NotNull @Min(1) Integer durationMinutes,
        @NotNull @Min(0) Integer bufferMinutes, boolean onlineBookable) { }
