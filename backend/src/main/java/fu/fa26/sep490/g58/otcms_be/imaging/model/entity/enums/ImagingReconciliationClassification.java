package fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums;

/** Safe operator-facing classification; never authorizes a relink or archive deletion. */
public enum ImagingReconciliationClassification {
    LINKED,
    RETRYABLE,
    ORPHANED,
    CONFLICT
}
