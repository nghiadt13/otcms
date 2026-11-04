package fu.fa26.sep490.g58.otcms_be.clinical.service;

import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalServiceType;
import java.math.BigDecimal;
import java.util.UUID;

/** Clinical-owned catalog read boundary; no servicecatalog repository access. */
public interface ClinicServiceCatalogPort {
    ClinicalServiceSnapshot getService(UUID serviceId);
    record ClinicalServiceSnapshot(UUID serviceId, String name, ClinicalServiceType type,
            BigDecimal currentPrice, boolean active) { }
}
