package fu.fa26.sep490.g58.otcms_be.servicecatalog.exception;

public class ClinicServiceException extends RuntimeException {
    public ClinicServiceException(String code) { super(code); }
    public ClinicServiceException(String code, Throwable cause) { super(code, cause); }
}
