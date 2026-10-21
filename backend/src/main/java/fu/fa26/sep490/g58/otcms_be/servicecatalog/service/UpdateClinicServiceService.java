package fu.fa26.sep490.g58.otcms_be.servicecatalog.service;

import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicService;
import java.util.UUID;

public interface UpdateClinicServiceService {
    ClinicService update(UUID serviceId, String name, String description,
                         int durationMinutes, int bufferMinutes, boolean onlineBookable);
}
