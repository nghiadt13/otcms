package fu.fa26.sep490.g58.otcms_be.servicecatalog.service.impl;

import fu.fa26.sep490.g58.otcms_be.servicecatalog.exception.ClinicServiceException;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicServiceType;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.LabIndicator;

final class ClinicServiceValidation {
    private ClinicServiceValidation() {
    }

    static void ensureId(UUID id) {
        if (id == null) {
            throw new ClinicServiceException("VALIDATION_FAILED");
        }
    }

    static String normalizeName(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ");
    }

    static String normalizeDescription(String description) {
        return description == null || description.isBlank() ? null : description.trim();
    }

    static void validateCatalogDetails(ClinicServiceType type, String modality, List<LabIndicator> indicators) {
        ClinicServiceType resolved = type == null ? ClinicServiceType.EXAMINATION : type;
        boolean validModality = modality != null && List.of("XRAY", "ULTRASOUND", "CT", "MRI").contains(modality);
        if ((resolved == ClinicServiceType.IMAGING) != validModality
                || (resolved != ClinicServiceType.LABORATORY && indicators != null && !indicators.isEmpty())) {
            throw new ClinicServiceException("VALIDATION_FAILED");
        }
        if (resolved == ClinicServiceType.LABORATORY) {
            if (indicators == null || indicators.isEmpty()) throw new ClinicServiceException("VALIDATION_FAILED");
            for (LabIndicator indicator : indicators) {
                if (indicator == null || indicator.name() == null || indicator.name().isBlank()
                        || indicator.unit() == null || indicator.unit().isBlank()
                        || indicator.refMale() == null || indicator.refFemale() == null
                        || indicator.refMale().min() > indicator.refMale().max()
                        || indicator.refFemale().min() > indicator.refFemale().max()) {
                    throw new ClinicServiceException("VALIDATION_FAILED");
                }
            }
        }
    }

    static void validate(String name, BigDecimal price, int durationMinutes, int bufferMinutes) {
        if (name.length() < 2 || name.length() > 160 || price == null || price.signum() < 0
                || price.scale() > 2 || price.precision() - price.scale() > 10
                || durationMinutes <= 0 || bufferMinutes < 0) {
            throw new ClinicServiceException("VALIDATION_FAILED");
        }
    }

    static void validate(String name, int durationMinutes, int bufferMinutes) {
        validate(name, BigDecimal.ZERO, durationMinutes, bufferMinutes);
    }
}
