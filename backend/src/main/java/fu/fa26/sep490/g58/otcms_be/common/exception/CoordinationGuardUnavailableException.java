package fu.fa26.sep490.g58.otcms_be.common.exception;

/** Raised when the shared database coordination guard cannot be acquired safely. */
public class CoordinationGuardUnavailableException extends RuntimeException {
    public CoordinationGuardUnavailableException() {
        super("DEPENDENCY_UNAVAILABLE");
    }
}
