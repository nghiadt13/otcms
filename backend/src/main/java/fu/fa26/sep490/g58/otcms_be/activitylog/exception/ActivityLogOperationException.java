package fu.fa26.sep490.g58.otcms_be.activitylog.exception;

public final class ActivityLogOperationException extends RuntimeException {
    public ActivityLogOperationException(String code) {
        super(code);
    }
}
