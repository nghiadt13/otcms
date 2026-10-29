package fu.fa26.sep490.g58.otcms_be.common.security;

/** Application role carried by the authenticated principal across feature boundaries. */
public enum AccountRole {
    PATIENT,
    RECEPTIONIST,
    DOCTOR,
    LAB_TECHNICIAN,
    IMAGING_TECHNICIAN,
    OWNER,
    ADMIN
}
