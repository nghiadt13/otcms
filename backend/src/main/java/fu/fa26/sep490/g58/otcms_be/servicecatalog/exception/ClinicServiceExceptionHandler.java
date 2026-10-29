package fu.fa26.sep490.g58.otcms_be.servicecatalog.exception;

import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ClinicServiceExceptionHandler {
    @ExceptionHandler(ClinicServiceException.class)
    public ResponseEntity<ErrorResponse> handle(ClinicServiceException exception) {
        String code = exception.getMessage();
        HttpStatus status = "SERVICE_NOT_FOUND".equals(code) || "DOCTOR_PROFILE_NOT_FOUND".equals(code)
                || "DOCTOR_ASSIGNMENT_NOT_FOUND".equals(code) ? HttpStatus.NOT_FOUND
                : "ACCESS_DENIED".equals(code) ? HttpStatus.FORBIDDEN
                : "DEPENDENCY_UNAVAILABLE".equals(code) ? HttpStatus.SERVICE_UNAVAILABLE
                : "SERVICE_NOT_BOOKABLE".equals(code) ? HttpStatus.UNPROCESSABLE_ENTITY
                : "SERVICE_DEACTIVATION_REQUIRES_IMPACT_CHECK".equals(code)
                || "DOCTOR_ASSIGNMENT_MUTATION_BLOCKED".equals(code)
                || "DOCTOR_ASSIGNMENT_ALREADY_EXISTS".equals(code)
                || "DUPLICATE_SERVICE_NAME".equals(code)
                || "SERVICE_ACTIVATION_REQUIRES_ADMIN".equals(code) ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(ErrorResponse.of(code,
                status == HttpStatus.NOT_FOUND ? "Clinic service not found"
                        : "SERVICE_DEACTIVATION_REQUIRES_IMPACT_CHECK".equals(code)
                        ? "Service deactivation requires an appointment impact check" : "Clinic service request is invalid",
                UUID.randomUUID().toString()));
    }

}
