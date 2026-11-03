package fu.fa26.sep490.g58.otcms_be.content.exception;

/** Business failure category; HTTP mapping belongs only to the API exception handler. */
public final class ContentOperationException extends RuntimeException {
    public enum Reason {
        INVALID_CONTENT("MSG-69"), INVALID_ANNOUNCEMENT("MSG-71"), INVALID_CRITERIA("MSG-54"),
        NOT_FOUND("MSG-50"), FORBIDDEN("MSG-50"), CONFLICT("MSG-69"), DELIVERY_UNAVAILABLE("MSG-04");
        private final String code;
        Reason(String code) {
            this.code = code;
        }
        public String code() {
            return code;
        }
    }
    private final Reason reason;
    public ContentOperationException(Reason reason) {
        super(reason.code());
        this.reason = reason;
    }
    public Reason reason() {
        return reason;
    }
}
