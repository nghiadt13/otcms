package fu.fa26.sep490.g58.otcms_be.appointment.exception;

public class AppointmentCapacityConflictException extends AppointmentBookingException {
    public AppointmentCapacityConflictException(Throwable cause) {
        super("VISIT_CAPACITY_NOT_AVAILABLE", cause);
    }
}
