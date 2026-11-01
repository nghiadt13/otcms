package fu.fa26.sep490.g58.otcms_be.activitylog.adapter.in.web;
import fu.fa26.sep490.g58.otcms_be.activitylog.application.ActivityLogFailure;
import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
@RestControllerAdvice(assignableTypes = ActivityLogController.class)
public class ActivityLogErrors {
    @ExceptionHandler(ActivityLogFailure.class)
    public ResponseEntity<ErrorResponse> business(ActivityLogFailure ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.getMessage().equals("MSG-50") ? 404 : 400)
                .body(ErrorResponse.of(ex.getMessage(), "Activity log request could not be completed", CorrelationIdFilter.current(request)));
    }
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> invalid(HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("MSG-54", "Invalid criteria", CorrelationIdFilter.current(request)));
    }
    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public ResponseEntity<ErrorResponse> unavailable(HttpServletRequest request) {
        return ResponseEntity.internalServerError().body(ErrorResponse.of("MSG-04", "Activity logs unavailable", CorrelationIdFilter.current(request)));
    }
}
