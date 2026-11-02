package fu.fa26.sep490.g58.otcms_be.servicecatalog.service;

import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicService;
import java.util.List;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicServiceType;

public interface GetClinicServiceCatalogService {
    List<ClinicService> publicCatalog();
    List<ClinicService> staffCatalog();
    List<ClinicService> roomOptions(ClinicServiceType type, boolean includeInactive);
    List<ClinicService> adminCatalog();
    ClinicService getService(java.util.UUID serviceId);
}
