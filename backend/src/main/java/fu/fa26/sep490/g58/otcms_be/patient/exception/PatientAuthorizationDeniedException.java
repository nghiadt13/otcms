package fu.fa26.sep490.g58.otcms_be.patient.exception;

/** Scoped denial; callers must not distinguish missing, unlinked, unrelated or revoked profiles. */
public class PatientAuthorizationDeniedException extends RuntimeException {
    public PatientAuthorizationDeniedException() { super("NOT_FOUND"); }
}
