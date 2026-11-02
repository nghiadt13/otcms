package fu.fa26.sep490.g58.otcms_be.visit.exception;

public class VisitCapacityConflictException extends RuntimeException {
    private final String code;
    public VisitCapacityConflictException(String code) { super(code); this.code = code; }
    public String code() { return code; }
}
