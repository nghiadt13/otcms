package fu.fa26.sep490.g58.otcms_be.imaging.model;

/** Decisions an ADMIN can record; none performs a relink or archive deletion. */
public enum ImagingReconciliationDecision {
    KEEP_QUARANTINED,
    EXTERNAL_RESOLUTION_CONFIRMED,
    START_REVIEW
}
