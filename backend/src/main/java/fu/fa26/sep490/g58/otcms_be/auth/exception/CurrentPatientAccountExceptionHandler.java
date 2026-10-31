package fu.fa26.sep490.g58.otcms_be.auth.exception;

import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

@RestControllerAdvice
public class CurrentPatientAccountExceptionHandler {
    @ExceptionHandler(CurrentPatientAccountException.class)
    public ResponseEntity<ErrorResponse> handle(CurrentPatientAccountException exception) {
        String code = exception.getMessage();
        HttpStatus status = "FORBIDDEN".equals(code) ? HttpStatus.FORBIDDEN
                : "ACCOUNT_NOT_FOUND".equals(code) ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(ErrorResponse.of(code,
                "Current patient account request is invalid", UUID.randomUUID().toString()));
    }
}
