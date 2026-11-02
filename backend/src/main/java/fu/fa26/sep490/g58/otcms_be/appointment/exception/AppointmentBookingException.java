package fu.fa26.sep490.g58.otcms_be.appointment.exception;

public class AppointmentBookingException extends RuntimeException {
    private final String code;

    public AppointmentBookingException(String code) {
        super(code);
        this.code = code;
    }

    public AppointmentBookingException(String code, Throwable cause) {
        super(code, cause);
        this.code = code;
    }

    public String code() { return code; }
}
