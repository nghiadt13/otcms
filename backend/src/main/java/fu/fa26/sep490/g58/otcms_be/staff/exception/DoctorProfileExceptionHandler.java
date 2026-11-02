package fu.fa26.sep490.g58.otcms_be.staff.exception;

import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class DoctorProfileExceptionHandler {
    @ExceptionHandler(DoctorProfileException.class)
    public ResponseEntity<ErrorResponse> handle(DoctorProfileException exception) {
        String code = exception.getMessage();
        HttpStatus status = "DOCTOR_ACCOUNT_UNAVAILABLE".equals(code) || "DOCTOR_PROFILE_NOT_FOUND".equals(code)
                || "DOCTOR_SHIFT_NOT_FOUND".equals(code) || "DOCTOR_TIME_OFF_NOT_FOUND".equals(code) ? HttpStatus.NOT_FOUND
                : "DOCTOR_ACCOUNT_ALREADY_PROFILED".equals(code) ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
        if ("ACCESS_DENIED".equals(code)) status = HttpStatus.FORBIDDEN;
        else if ("DEPENDENCY_UNAVAILABLE".equals(code)) status = HttpStatus.SERVICE_UNAVAILABLE;
        else if (code.endsWith("_BLOCKED") || "DOCTOR_SHIFT_OVERLAP".equals(code)) status = HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(ErrorResponse.of(code, "Doctor profile request cannot be completed",
                UUID.randomUUID().toString()));
    }
}
