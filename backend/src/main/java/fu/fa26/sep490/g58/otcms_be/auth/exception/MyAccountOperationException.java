package fu.fa26.sep490.g58.otcms_be.auth.exception;

public class MyAccountOperationException extends RuntimeException {
    public enum Reason { INVALID_INPUT, ACCESS_DENIED, OTP_INVALID, EMAIL_UNAVAILABLE, AVATAR_NOT_FOUND }
    private final Reason reason;
    public MyAccountOperationException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }
    public Reason reason() { return reason; }
}
