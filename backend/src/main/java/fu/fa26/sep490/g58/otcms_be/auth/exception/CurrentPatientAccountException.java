package fu.fa26.sep490.g58.otcms_be.auth.exception;

public class CurrentPatientAccountException extends RuntimeException {
    public CurrentPatientAccountException(String code) {
        super(code);
    }
}
