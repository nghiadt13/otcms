package fu.fa26.sep490.g58.otcms_be.staff.exception;

import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class StaffProfileExceptionHandler {
    @ExceptionHandler(StaffProfileException.class)
    public ResponseEntity<ErrorResponse> handle(StaffProfileException ex) {
        HttpStatus status = switch (ex.getMessage()) {
            case "STAFF_PROFILE_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "STAFF_ACCOUNT_UNAVAILABLE", "STAFF_PROFILE_CONFLICT", "DOCTOR_HAS_FUTURE_APPOINTMENTS" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(ErrorResponse.of(ex.getMessage(),
                "Staff profile request could not be completed", UUID.randomUUID().toString()));
    }
}
