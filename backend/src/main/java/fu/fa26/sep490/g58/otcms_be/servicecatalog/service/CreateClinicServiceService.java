package fu.fa26.sep490.g58.otcms_be.servicecatalog.service;

import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicServiceType;
import java.math.BigDecimal;
import java.util.List;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.LabIndicator;

public interface CreateClinicServiceService {
    ClinicService create(String name, String description, BigDecimal price,
                         int durationMinutes, int bufferMinutes, boolean onlineBookable);

    ClinicService create(String name, String description, ClinicServiceType type, BigDecimal price,
                         int durationMinutes, int bufferMinutes, boolean onlineBookable);

    ClinicService create(String name, String description, ClinicServiceType type, BigDecimal price,
                         int durationMinutes, int bufferMinutes, boolean onlineBookable, String modality,
                         String patientNote, List<LabIndicator> indicators);

    default ClinicService create(String name, String description, int durationMinutes,
                                 int bufferMinutes, boolean onlineBookable) {
        return create(name, description, BigDecimal.ZERO, durationMinutes, bufferMinutes, onlineBookable);
    }
}
