package fu.fa26.sep490.g58.otcms_be.servicecatalog.service;

import java.util.List;
import java.util.UUID;

/** Service/Catalog-owned active Doctor-service assignment read projection. */
public interface ServiceDoctorAssignmentQueryService {
    List<UUID> findActiveDoctorProfileIds(UUID serviceId);
}
