package fu.fa26.sep490.g58.otcms_be.visit.exception;

public class VisitWorkflowException extends RuntimeException {
    private final String code;
    public VisitWorkflowException(String code) { super(code); this.code = code; }
    public VisitWorkflowException(String code, Throwable cause) { super(code, cause); this.code = code; }
    public String code() { return code; }
}
