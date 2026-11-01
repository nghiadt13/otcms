package fu.fa26.sep490.g58.otcms_be.reporting.adapter.in.web;
import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice(assignableTypes=ReportingController.class)
public class ReportingErrors {
    @ExceptionHandler({IllegalArgumentException.class,java.time.DateTimeException.class,org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.bind.MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> invalid(HttpServletRequest r){return ResponseEntity.badRequest().body(ErrorResponse.of("MSG-72","Invalid reporting period or subject",CorrelationIdFilter.current(r)));}
    @ExceptionHandler({ReportingController.ExportUnavailable.class,org.springframework.dao.DataAccessException.class})
    public ResponseEntity<ErrorResponse> unavailable(HttpServletRequest r){return ResponseEntity.status(503).body(ErrorResponse.of("MSG-04","Report source or export capability unavailable",CorrelationIdFilter.current(r)));}
}
