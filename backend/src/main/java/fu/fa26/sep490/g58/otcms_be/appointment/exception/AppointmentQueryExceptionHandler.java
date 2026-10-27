package fu.fa26.sep490.g58.otcms_be.appointment.exception;

import fu.fa26.sep490.g58.otcms_be.appointment.controller.AppointmentQueryController;
import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentQueryException;
import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AppointmentQueryController.class)
public class AppointmentQueryExceptionHandler {
    @ExceptionHandler(AppointmentQueryException.class)
    public ResponseEntity<ErrorResponse> handle(AppointmentQueryException exception, HttpServletRequest request) {
        HttpStatus status = switch (exception.code()) {
            case "ACCESS_DENIED" -> HttpStatus.FORBIDDEN;
            case "APPOINTMENT_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.BAD_REQUEST;
        };
        String message = exception.code().equals("APPOINTMENT_NOT_FOUND") ? "Appointment not found" : "Invalid appointment query";
        return ResponseEntity.status(status).body(ErrorResponse.of(exception.code(), message, CorrelationIdFilter.current(request)));
    }
}
