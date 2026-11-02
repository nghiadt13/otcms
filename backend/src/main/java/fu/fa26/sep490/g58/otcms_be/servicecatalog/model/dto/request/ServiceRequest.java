package fu.fa26.sep490.g58.otcms_be.servicecatalog.model.dto.request;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicServiceType;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.LabIndicator;
import java.util.List;

public record ServiceRequest(@NotBlank String name, String description,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
        @NotNull @Min(1) Integer durationMinutes, @NotNull @Min(0) Integer bufferMinutes,
        ClinicServiceType type,
        boolean onlineBookable,
        String modality,
        String patientNote,
        List<LabIndicator> indicators) { }
