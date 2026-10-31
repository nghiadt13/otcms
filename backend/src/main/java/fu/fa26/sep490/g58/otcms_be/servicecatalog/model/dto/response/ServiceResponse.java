package fu.fa26.sep490.g58.otcms_be.servicecatalog.model.dto.response;

import java.math.BigDecimal;
import java.util.UUID;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicServiceType;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.LabIndicator;
import java.util.List;

public record ServiceResponse(UUID id, String name, String description, ClinicServiceType type, BigDecimal price, int durationMinutes,
        int bufferMinutes, boolean onlineBookable, boolean active, String modality, String patientNote,
        List<LabIndicator> indicators) {
    public ServiceResponse(UUID id, String name, String description, BigDecimal price, int durationMinutes,
            int bufferMinutes, boolean onlineBookable, boolean active) {
        this(id, name, description, null, price, durationMinutes, bufferMinutes, onlineBookable, active, null, null, List.of());
    }
}
