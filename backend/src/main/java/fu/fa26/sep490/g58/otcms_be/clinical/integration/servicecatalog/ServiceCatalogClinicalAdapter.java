package fu.fa26.sep490.g58.otcms_be.clinical.integration.servicecatalog;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalServiceType;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicServiceCatalogPort;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.exception.ClinicServiceException;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.GetClinicServiceCatalogService;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Reads orderable catalog services through the owning feature's public service. */
@Component
public class ServiceCatalogClinicalAdapter implements ClinicServiceCatalogPort {
    private final GetClinicServiceCatalogService catalog;

    public ServiceCatalogClinicalAdapter(GetClinicServiceCatalogService catalog) {
        this.catalog = catalog;
    }

    @Override
    public ClinicalServiceSnapshot getService(UUID serviceId) {
        if (serviceId == null) throw new ClinicalOperationException("MSG-39");
        final ClinicService service;
        try {
            service = catalog.getService(serviceId);
        } catch (ClinicServiceException exception) {
            if (!"SERVICE_NOT_FOUND".equals(exception.getMessage())) throw exception;
            throw new ClinicalOperationException("MSG-41");
        }
        if (service == null) throw new ClinicalOperationException("MSG-41");
        if (!serviceId.equals(service.id()) || service.type() == null) {
            throw new ClinicalOperationException("MSG-39");
        }
        // Examination is recorded by UC29, not ordered as an ancillary service.
        // OTHER remains a legacy Clinical value; this adapter never produces it.
        var type = switch (service.type()) {
            case IMAGING -> ClinicalServiceType.IMAGING;
            case LABORATORY -> ClinicalServiceType.LABORATORY;
            case EXAMINATION -> throw new ClinicalOperationException("MSG-39");
        };
        return new ClinicalServiceSnapshot(service.id(), service.name(), type, service.price(), service.active());
    }
}
