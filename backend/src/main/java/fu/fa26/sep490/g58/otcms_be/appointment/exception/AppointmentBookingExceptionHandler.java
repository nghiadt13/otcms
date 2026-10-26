package fu.fa26.sep490.g58.otcms_be.appointment.exception;

import fu.fa26.sep490.g58.otcms_be.appointment.controller.AppointmentBookingController;
import fu.fa26.sep490.g58.otcms_be.appointment.controller.AppointmentFollowUpController;
import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentBookingException;
import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;

@RestControllerAdvice(assignableTypes = {AppointmentBookingController.class, AppointmentFollowUpController.class})
public class AppointmentBookingExceptionHandler {
    @ExceptionHandler(AppointmentBookingException.class)
    public ResponseEntity<ErrorResponse> handle(AppointmentBookingException exception, HttpServletRequest request) {
        HttpStatus status = switch (exception.code()) {
            case "ACCESS_DENIED" -> HttpStatus.FORBIDDEN;
            case "PATIENT_PROFILE_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "SOURCE_VISIT_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "APPOINTMENT_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "APPOINTMENT_NOT_CHANGEABLE" -> HttpStatus.CONFLICT;
            case "VISIT_CAPACITY_NOT_AVAILABLE" -> HttpStatus.CONFLICT;
            case "VISIT_LINK_CONFLICT", "VISIT_CANCELLATION_NOT_ALLOWED", "IDEMPOTENCY_CONFLICT" -> HttpStatus.CONFLICT;
            case "APPOINTMENT_CHANGE_TOO_LATE" -> HttpStatus.UNPROCESSABLE_ENTITY;
            case "SERVICE_NOT_BOOKABLE", "DOCTOR_NOT_ELIGIBLE", "BOOKING_TIME_IN_PAST",
                    "BOOKING_WINDOW_VIOLATION", "BOOKING_NOTICE_TOO_SHORT", "CHECK_IN_TIME_NOT_ALLOWED",
                    "NO_SHOW_NOT_ALLOWED" -> HttpStatus.UNPROCESSABLE_ENTITY;
            case "FOLLOW_UP_NOT_ALLOWED" -> HttpStatus.UNPROCESSABLE_ENTITY;
            case "SLOT_UNAVAILABLE" -> HttpStatus.CONFLICT;
            case "DEPENDENCY_UNAVAILABLE" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(ErrorResponse.of(exception.code(), "Appointment command was rejected",
                CorrelationIdFilter.current(request)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> malformed(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("VALIDATION_FAILED", "Invalid request body",
                CorrelationIdFilter.current(request)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> invalidRequest(MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("VALIDATION_FAILED", "Invalid request body",
                CorrelationIdFilter.current(request)));
    }
}
