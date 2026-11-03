package fu.fa26.sep490.g58.otcms_be.reporting.exception;

import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import fu.fa26.sep490.g58.otcms_be.reporting.controller.ReportingController;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes=ReportingController.class)
public class ReportingApiExceptionHandler {
    @ExceptionHandler(ReportingOperationException.class)
    public ResponseEntity<ErrorResponse> business(ReportingOperationException exception, HttpServletRequest request) {
        if (exception.reason() == ReportingOperationException.Reason.EXPORT_UNAVAILABLE) {
            return unavailable(request);
        }
        return invalid(request);
    }
    @ExceptionHandler({
            IllegalArgumentException.class,java.time.DateTimeException.class,org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.bind.MissingServletRequestParameterException.class
    })
    public ResponseEntity<ErrorResponse> invalid(HttpServletRequest r){
        return ResponseEntity.badRequest().body(ErrorResponse.of("MSG-72","Invalid reporting period or subject",CorrelationIdFilter.current(r)));
    }
    @ExceptionHandler({
            org.springframework.dao.DataAccessException.class
    })
    public ResponseEntity<ErrorResponse> unavailable(HttpServletRequest r){
        return ResponseEntity.status(503).body(ErrorResponse.of("MSG-04","Report source or export capability unavailable",CorrelationIdFilter.current(r)));
    }
}
