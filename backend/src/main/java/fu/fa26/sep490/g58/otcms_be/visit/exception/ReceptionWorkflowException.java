package fu.fa26.sep490.g58.otcms_be.visit.exception;

/** Stable error raised by the Visit/reception-owned atomic workflow contract. */
public class ReceptionWorkflowException extends RuntimeException {
    private final String code;

    public ReceptionWorkflowException(String code) {
        super(code);
        this.code = code;
    }

    public String code() { return code; }
}
