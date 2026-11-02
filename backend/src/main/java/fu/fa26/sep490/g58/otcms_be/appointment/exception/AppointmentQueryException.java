package fu.fa26.sep490.g58.otcms_be.appointment.exception;

public class AppointmentQueryException extends RuntimeException {
    private final String code;
    public AppointmentQueryException(String code) { super(code); this.code = code; }
    public String code() { return code; }
}
