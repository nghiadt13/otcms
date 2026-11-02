package fu.fa26.sep490.g58.otcms_be.patient.exception;

import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import fu.fa26.sep490.g58.otcms_be.patient.exception.PatientAuthorizationDeniedException;
import fu.fa26.sep490.g58.otcms_be.patient.controller.CareRelationshipController;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = CareRelationshipController.class)
public class CareRelationshipExceptionHandler {
    @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> invalidTransport(Exception exception, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("VALIDATION_FAILED", "Care relationship request is invalid",
                fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter.current(request)));
    }
    @ExceptionHandler(fu.fa26.sep490.g58.otcms_be.patient.exception.PatientAuthorizationDeniedException.class)
    public ResponseEntity<ErrorResponse> authorizationDenied(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of("NOT_FOUND", "Patient resource not found",
                fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter.current(request)));
    }
    @ExceptionHandler(CareRelationshipException.class)
    public ResponseEntity<ErrorResponse> handle(CareRelationshipException exception, HttpServletRequest request) {
        String code = exception.getMessage();
        HttpStatus status = switch (code) {
            case "FORBIDDEN" -> HttpStatus.FORBIDDEN;
            case "NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "RELATIONSHIP_STATE_CONFLICT" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        String message = switch (status) {
            case FORBIDDEN -> "Care relationship access denied";
            case NOT_FOUND -> "Patient resource not found";
            case CONFLICT -> "Care relationship state does not allow this operation";
            default -> "Care relationship request is invalid";
        };
        Object correlation = request.getAttribute(fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter.ATTRIBUTE);
        return ResponseEntity.status(status).body(ErrorResponse.of("FORBIDDEN".equals(code) ? "ACCESS_DENIED" : code, message,
                correlation instanceof String id ? id : UUID.randomUUID().toString()));
    }
}
