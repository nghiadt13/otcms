package fu.fa26.sep490.g58.otcms_be.reporting.exception;

public final class ReportingOperationException extends RuntimeException {
    public enum Reason {
        INVALID_PERIOD, INVALID_REPORT, EXPORT_UNAVAILABLE
    }
    private final Reason reason;
    public ReportingOperationException(Reason reason) {
        super(reason == Reason.EXPORT_UNAVAILABLE ? "MSG-04" : "MSG-72");
        this.reason = reason;
    }
    public Reason reason() {
        return reason;
    }
}
