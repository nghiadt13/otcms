package fu.fa26.sep490.g58.otcms_be.auth.exception;

public class StaffAccountAdminException extends RuntimeException {
    public enum Reason { CREATE_INPUT, SEARCH_INPUT, USERNAME_TAKEN, ACTOR_DENIED, TARGET_UNAVAILABLE,
        STATUS_UNCHANGED, OPERATION_FAILED }
    private final Reason reason;
    public StaffAccountAdminException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }
    public Reason reason() { return reason; }
}
