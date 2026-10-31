package fu.fa26.sep490.g58.otcms_be.auth.exception;

import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class PatientAccountAdminExceptionHandler {
    @ExceptionHandler(StaffAccountAdminException.class)
    public ResponseEntity<ErrorResponse> staff(StaffAccountAdminException exception) {
        HttpStatus status = "EMAIL_ALREADY_EXISTS".equals(exception.getMessage())
                ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(ErrorResponse.of(exception.getMessage(),
                "Staff account request could not be completed", UUID.randomUUID().toString()));
    }
    @ExceptionHandler(PatientAccountAdminException.class)
    public ResponseEntity<ErrorResponse> handle(PatientAccountAdminException exception) {
        HttpStatus status = switch (exception.getMessage()) {
            case "PATIENT_ACCOUNT_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "ACCOUNT_STATUS_UNCHANGED" -> HttpStatus.CONFLICT;
            case "EMAIL_ALREADY_EXISTS" -> HttpStatus.CONFLICT;
            case "ACCESS_DENIED" -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(ErrorResponse.of(exception.getMessage(),
                "Patient account request could not be completed", UUID.randomUUID().toString()));
    }
}
