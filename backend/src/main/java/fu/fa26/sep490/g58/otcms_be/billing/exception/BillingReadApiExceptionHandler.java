package fu.fa26.sep490.g58.otcms_be.billing.exception;

import fu.fa26.sep490.g58.otcms_be.billing.controller.BillingReadController;
import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = BillingReadController.class)
public class BillingReadApiExceptionHandler {
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> invalid(MethodArgumentTypeMismatchException failure, HttpServletRequest request) {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(
                ErrorResponse.of("MSG-39", "Invalid request parameters", CorrelationIdFilter.current(request)));
    }
}
