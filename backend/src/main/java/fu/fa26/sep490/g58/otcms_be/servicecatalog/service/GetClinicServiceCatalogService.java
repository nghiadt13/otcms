package fu.fa26.sep490.g58.otcms_be.servicecatalog.service;

import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicService;
import java.util.List;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicServiceType;

public interface GetClinicServiceCatalogService {
    record ImagingQueueService(java.util.UUID serviceId, ClinicServiceType type, String modality, boolean active) { }
    java.util.Map<java.util.UUID, ImagingQueueService> imagingQueueServices(java.util.Collection<java.util.UUID> serviceIds);
    List<ClinicService> adminCatalog();
    List<ClinicService> publicCatalog();
    List<ClinicService> staffCatalog();
    List<ClinicService> roomOptions(ClinicServiceType type, boolean includeInactive);
    ClinicService getService(java.util.UUID serviceId);
}
