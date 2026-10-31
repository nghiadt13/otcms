package fu.fa26.sep490.g58.otcms_be.billing.exception;

public class BillingException extends RuntimeException {
    private final String code;
    public BillingException(String code) { super(code); this.code = code; }
    public BillingException(String code, Throwable cause) { super(code, cause); this.code = code; }
    public String code() { return code; }
}
