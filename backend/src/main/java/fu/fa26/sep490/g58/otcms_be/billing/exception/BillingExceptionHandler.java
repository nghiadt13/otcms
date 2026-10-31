package fu.fa26.sep490.g58.otcms_be.billing.exception;

import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class BillingExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(BillingExceptionHandler.class);

    @ExceptionHandler(BillingException.class)
    public ResponseEntity<ErrorResponse> handle(BillingException exception, HttpServletRequest request) {
        String correlationId = CorrelationIdFilter.current(request);
        if ("GATEWAY_UNAVAILABLE".equals(exception.code())) {
            log.error("Payment gateway request failed; correlationId={}", correlationId, exception);
        }
        HttpStatus status = switch (exception.code()) {
            case "INVOICE_NOT_FOUND", "PAYMENT_ATTEMPT_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "ACCESS_DENIED" -> HttpStatus.FORBIDDEN;
            case "GATEWAY_UNAVAILABLE", "PAYMENT_RECONCILIATION_FAILED" -> HttpStatus.SERVICE_UNAVAILABLE;
            case "INVALID_WEBHOOK" -> HttpStatus.BAD_REQUEST;
            case "IDEMPOTENCY_CONFLICT", "INVOICE_NOT_PAYABLE", "PAYMENT_AMOUNT_INVALID", "SOURCE_NOT_BILLABLE" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(ErrorResponse.of(exception.code(),
                "The billing request could not be completed", correlationId));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraint(DataIntegrityViolationException exception,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of("BILLING_CONFLICT",
                "The billing record changed or already exists", CorrelationIdFilter.current(request)));
    }
}
