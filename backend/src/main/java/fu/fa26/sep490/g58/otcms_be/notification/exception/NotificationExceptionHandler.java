package fu.fa26.sep490.g58.otcms_be.notification.exception;

import fu.fa26.sep490.g58.otcms_be.notification.controller.MyNotificationController;
import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = MyNotificationController.class) @Order(Ordered.HIGHEST_PRECEDENCE)
public class NotificationExceptionHandler {
    @ExceptionHandler(NotificationNotFoundException.class)
    public ResponseEntity<ErrorResponse> missing(Exception error, HttpServletRequest request) { return response(404, "MSG-50", request); }
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> invalid(Exception error, HttpServletRequest request) { return response(400, "MSG-54", request); }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception error, HttpServletRequest request) { return response(500, "MSG-04", request); }
    private ResponseEntity<ErrorResponse> response(int status, String code, HttpServletRequest request) {
        return ResponseEntity.status(status).body(ErrorResponse.of(code, "Notification request could not be completed", CorrelationIdFilter.current(request)));
    }
}
