package fu.fa26.sep490.g58.otcms_be.diagnostic.integration;

import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ImagingService;
import java.util.UUID;

/** Reads the owning service catalog; FEAT-003 never accepts a client-supplied price. */
public interface ImagingServiceCatalogPort {
    ImagingService requireImagingService(UUID serviceId, String imagingType);
}
