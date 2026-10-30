package fu.fa26.sep490.g58.otcms_be.servicecatalog.service;

import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicService;
import java.util.List;

public interface GetClinicServiceCatalogService {
    List<ClinicService> adminCatalog();
    List<ClinicService> publicCatalog();
    List<ClinicService> staffCatalog();
}
