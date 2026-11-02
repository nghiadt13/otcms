package fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums;

/** Lifecycle states persisted in the OTCMS accounts table. */
public enum AccountStatus {
    PENDING_VERIFICATION,
    ACTIVE,
    TEMPORARILY_LOCKED,
    DISABLED,
    ARCHIVED
}
