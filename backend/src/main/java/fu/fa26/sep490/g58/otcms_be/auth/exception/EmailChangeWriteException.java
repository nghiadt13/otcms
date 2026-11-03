package fu.fa26.sep490.g58.otcms_be.auth.exception;

/** Sanitized persistence failure. Never retain the server diagnostic or target identifier. */
public class EmailChangeWriteException extends RuntimeException {
    public enum Reason { COLLISION, OPERATION_FAILED }
    private final Reason reason;

    public EmailChangeWriteException(Reason reason) {
        // Resource-close failures must not attach a raw SQLException as a suppressed exception either.
        super(reason.name(), null, false, true);
        this.reason = reason;
    }

    public Reason reason() { return reason; }
}
