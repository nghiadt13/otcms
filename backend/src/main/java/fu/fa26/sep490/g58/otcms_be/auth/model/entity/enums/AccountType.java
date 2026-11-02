package fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums;

/** Application account categories persisted in the OTCMS accounts table. */
public enum AccountType {
    PATIENT,
    RECEPTIONIST,
    DOCTOR,
    LAB_TECHNICIAN,
    IMAGING_TECHNICIAN,
    OWNER,
    ADMIN;

    /** Staff portal accepts any clinic staff account, while patient login is separate. */
    public boolean isStaff() {
        return this != PATIENT;
    }
}
