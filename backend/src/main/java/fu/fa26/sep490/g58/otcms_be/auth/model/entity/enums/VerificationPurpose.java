package fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums;

/** Purpose of a row in verification_codes (final-db V110). Values match ck_verification_codes_purpose. */
public enum VerificationPurpose {
    EMAIL_VERIFY,
    EMAIL_CHANGE,
    PASSWORD_RESET,
    CARE_LINK,
    DEPENDENT_ADD
}
