package fu.fa26.sep490.g58.otcms_be.appointment.exception;

import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentAvailabilityException;
import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

@RestControllerAdvice
public class AppointmentAvailabilityExceptionHandler {
    @ExceptionHandler(AppointmentAvailabilityException.class)
    public ResponseEntity<ErrorResponse> handle(AppointmentAvailabilityException exception) {
        HttpStatus status = switch (exception.code()) {
            case "BOOKING_WINDOW_VIOLATION" -> HttpStatus.UNPROCESSABLE_ENTITY;
            case "DOCTOR_NOT_ELIGIBLE" -> HttpStatus.UNPROCESSABLE_ENTITY;
            case "RESOURCE_NOT_FOUND", "APPOINTMENT_NOT_FOUND", "ACCESS_DENIED" -> HttpStatus.NOT_FOUND;
            case "APPOINTMENT_NOT_CHANGEABLE" -> HttpStatus.CONFLICT;
            case "CHANGE_DEADLINE_PASSED", "APPOINTMENT_CHANGE_TOO_LATE", "BOOKING_TIME_IN_PAST" -> HttpStatus.UNPROCESSABLE_ENTITY;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(ErrorResponse.of(exception.code(),
                "Availability request cannot be completed", UUID.randomUUID().toString()));
    }
}
