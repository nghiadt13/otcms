package fu.fa26.sep490.g58.otcms_be.content.exception;

import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import fu.fa26.sep490.g58.otcms_be.content.controller.PatientAnnouncementController;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Wrong role on the patient announcements list: 403 with the content module's FORBIDDEN code. */
@RestControllerAdvice(assignableTypes = PatientAnnouncementController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PatientAnnouncementExceptionHandler {
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> denied(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of("FORBIDDEN", "Access denied",
                CorrelationIdFilter.current(request)));
    }
}
