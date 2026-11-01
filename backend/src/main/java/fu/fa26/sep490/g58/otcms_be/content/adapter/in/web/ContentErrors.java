package fu.fa26.sep490.g58.otcms_be.content.adapter.in.web;
import fu.fa26.sep490.g58.otcms_be.content.application.ContentFailure;
import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
@RestControllerAdvice(assignableTypes={AdminWebsiteContentController.class,AdminAnnouncementController.class,PublicClinicContentController.class})
public class ContentErrors {
    @ExceptionHandler(ContentFailure.class) public ResponseEntity<ErrorResponse> failure(ContentFailure ex,HttpServletRequest request) {
        return ResponseEntity.status(ex.status()).body(ErrorResponse.of(ex.getMessage(),"Content request could not be completed",CorrelationIdFilter.current(request)));
    }
    @ExceptionHandler(MethodArgumentTypeMismatchException.class) public ResponseEntity<ErrorResponse> invalid(MethodArgumentTypeMismatchException ex,HttpServletRequest request) {
        String code=ex.getName().equals("id")?"MSG-50":"MSG-54";
        return ResponseEntity.status(code.equals("MSG-50")?404:400).body(ErrorResponse.of(code,"Invalid content request",CorrelationIdFilter.current(request)));
    }
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.multipart.MaxUploadSizeExceededException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class})
    public ResponseEntity<ErrorResponse> invalidBody(HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(request.getRequestURI().contains("announcements")?"MSG-71":"MSG-69",
                "Invalid content request",CorrelationIdFilter.current(request)));
    }
    @ExceptionHandler({org.springframework.dao.DataAccessException.class,java.io.IOException.class})
    public ResponseEntity<ErrorResponse> unavailable(HttpServletRequest request) {
        return ResponseEntity.internalServerError().body(ErrorResponse.of("MSG-04","Content unavailable",CorrelationIdFilter.current(request)));
    }
}
