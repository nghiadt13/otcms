package fu.fa26.sep490.g58.otcms_be.servicecatalog.integration.diagnostic;

import fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.ImagingServiceCatalogPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ImagingService;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicServiceType;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.GetClinicServiceCatalogService;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ImagingServiceCatalogAdapter implements ImagingServiceCatalogPort {
    private final GetClinicServiceCatalogService catalog;

    public ImagingServiceCatalogAdapter(GetClinicServiceCatalogService catalog) { this.catalog = catalog; }

    @Override
    public ImagingService requireImagingService(UUID serviceId, String imagingType) {
        final fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicService service;
        try {
            service = catalog.getService(serviceId);
        } catch (fu.fa26.sep490.g58.otcms_be.servicecatalog.exception.ClinicServiceException failure) {
            if (!"SERVICE_NOT_FOUND".equals(failure.getMessage())) throw failure;
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.SERVICE_NOT_ELIGIBLE);
        }
        if (service == null || !serviceId.equals(service.id()) || !service.active()
                || service.type() != ClinicServiceType.IMAGING || service.modality() == null || service.modality().isBlank()
                || (imagingType != null && !imagingType.equalsIgnoreCase(service.modality()))) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.SERVICE_NOT_ELIGIBLE);
        }
        return new ImagingService(service.id(), service.modality(), true);
    }
}
