package fu.fa26.sep490.g58.otcms_be.imaging.service;

import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingReconciliationClassification;

/** Validated filters for the ADMIN-only reconciliation queue. */
public record ImagingReconciliationQuery(String state, String classification, String category, int page, int size) {
    public ImagingReconciliationQuery {
        if (state != null && !java.util.Set.of("OPEN", "IN_PROGRESS", "RESOLVED").contains(state)) {
            throw new IllegalArgumentException("Invalid reconciliation state");
        }
        if (classification != null) ImagingReconciliationClassification.valueOf(classification);
        if (category != null && !category.matches("[A-Z][A-Z0-9_]{0,31}")) {
            throw new IllegalArgumentException("Invalid reconciliation category");
        }
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid page bounds");
    }
}
