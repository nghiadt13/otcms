package fu.fa26.sep490.g58.otcms_be.servicecatalog.service.impl;

import fu.fa26.sep490.g58.otcms_be.servicecatalog.exception.ClinicServiceException;
import java.math.BigDecimal;
import java.util.UUID;

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
