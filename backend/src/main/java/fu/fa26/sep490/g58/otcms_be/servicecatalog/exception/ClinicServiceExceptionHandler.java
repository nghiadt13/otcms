package fu.fa26.sep490.g58.otcms_be.servicecatalog.exception;

import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ClinicServiceExceptionHandler {
    @ExceptionHandler(ClinicServiceException.class)
    public ResponseEntity<ErrorResponse> handle(ClinicServiceException exception, HttpServletRequest request) {
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
        String responseCode = code;
        if (("POST".equals(request.getMethod()) && request.getRequestURI().equals("/api/v1/admin/clinic-services"))
                || (("PUT".equals(request.getMethod()) || "DELETE".equals(request.getMethod()))
                && request.getRequestURI().matches("/api/v1/admin/clinic-services/[^/]+"))) {
            responseCode = switch (code) {
                case "VALIDATION_FAILED", "SERVICE_REQUEST_INVALID", "DUPLICATE_SERVICE_NAME" -> "MSG-66";
                case "SERVICE_NOT_FOUND", "ACCESS_DENIED" -> "MSG-50";
                case "DEPENDENCY_UNAVAILABLE" -> "MSG-04";
                default -> code;
            };
        }
        return ResponseEntity.status(status).body(ErrorResponse.of(responseCode,
                status == HttpStatus.NOT_FOUND ? "Clinic service not found"
                        : "SERVICE_DEACTIVATION_REQUIRES_IMPACT_CHECK".equals(code)
                        ? "Service deactivation requires an appointment impact check" : "Clinic service request is invalid",
                UUID.randomUUID().toString()));
    }

}
