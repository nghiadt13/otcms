package fu.fa26.sep490.g58.otcms_be.clinical.exception;

public class ClinicalOperationException extends RuntimeException {
    private final String code;
    public ClinicalOperationException(String code) { super(code); this.code = code; }
    public String code() { return code; }
}
