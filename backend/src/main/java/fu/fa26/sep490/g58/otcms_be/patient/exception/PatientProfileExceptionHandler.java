package fu.fa26.sep490.g58.otcms_be.patient.exception;

import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class PatientProfileExceptionHandler {
    @ExceptionHandler(PatientProfileException.class)
    public ResponseEntity<ErrorResponse> handle(PatientProfileException exception) {
        boolean forbidden = "FORBIDDEN".equals(exception.getMessage());
        HttpStatus status = forbidden ? HttpStatus.FORBIDDEN : HttpStatus.BAD_REQUEST;
        String code = forbidden ? "FORBIDDEN" : "VALIDATION_FAILED";
        return ResponseEntity.status(status).body(ErrorResponse.of(code,
                forbidden ? "Patient profile access denied" : "Patient profile request is invalid",
                UUID.randomUUID().toString()));
    }
}
