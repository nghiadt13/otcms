package fu.fa26.sep490.g58.otcms_be.servicecatalog.service;

import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicService;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.LabIndicator;

public interface UpdateClinicServiceService {
    ClinicService update(UUID serviceId, String name, String description,
                         BigDecimal price, int durationMinutes, int bufferMinutes, boolean onlineBookable);

    ClinicService update(UUID serviceId, String name, String description, BigDecimal price,
                         int durationMinutes, int bufferMinutes, String modality, String patientNote,
                         List<LabIndicator> indicators);

    default ClinicService update(UUID serviceId, String name, String description,
                                 int durationMinutes, int bufferMinutes, boolean onlineBookable) {
        return update(serviceId, name, description, BigDecimal.ZERO, durationMinutes, bufferMinutes, onlineBookable);
    }
}
