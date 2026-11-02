package fu.fa26.sep490.g58.otcms_be.staff.exception;

public class DoctorProfileException extends RuntimeException {
    public DoctorProfileException(String code) { super(code); }
    public DoctorProfileException(String code, Throwable cause) { super(code, cause); }
}
