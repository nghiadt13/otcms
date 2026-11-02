package fu.fa26.sep490.g58.otcms_be.appointment.exception;

/** Signals that Appointment could not provide trustworthy capacity evidence. */
public class AppointmentCapacityUnavailableException extends RuntimeException {
    public AppointmentCapacityUnavailableException(String message) {
        super(message);
    }

    public AppointmentCapacityUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
