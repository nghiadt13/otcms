package fu.fa26.sep490.g58.otcms_be.servicecatalog.exception;

import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.controller.ClinicServiceController;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ClinicServiceController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ClinicServiceValidationExceptionHandler {
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> denied(AccessDeniedException exception, HttpServletRequest request) {
        if (!("PUT".equals(request.getMethod()) || "DELETE".equals(request.getMethod())))
            throw exception;
        if (!request.getRequestURI().matches("/api/v1/admin/clinic-services/[^/]+")) throw exception;
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of("MSG-50", "Access denied",
                CorrelationIdFilter.current(request)));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> invalid(Exception exception, HttpServletRequest request) throws Exception {
        if (!("POST".equals(request.getMethod()) && request.getRequestURI().equals("/api/v1/admin/clinic-services"))
                && !("PUT".equals(request.getMethod())
                && request.getRequestURI().matches("/api/v1/admin/clinic-services/[^/]+"))) throw exception;
        return ResponseEntity.badRequest().body(ErrorResponse.of("MSG-66", "Invalid clinic service request",
                CorrelationIdFilter.current(request)));
    }
}
