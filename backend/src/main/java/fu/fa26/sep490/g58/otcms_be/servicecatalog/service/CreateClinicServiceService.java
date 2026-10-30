package fu.fa26.sep490.g58.otcms_be.servicecatalog.service;

import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicService;
import java.math.BigDecimal;

public interface CreateClinicServiceService {
    ClinicService create(String name, String description, BigDecimal price,
                         int durationMinutes, int bufferMinutes, boolean onlineBookable);

    default ClinicService create(String name, String description, int durationMinutes,
                                 int bufferMinutes, boolean onlineBookable) {
        return create(name, description, BigDecimal.ZERO, durationMinutes, bufferMinutes, onlineBookable);
    }
}
