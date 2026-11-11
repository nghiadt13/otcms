package fu.fa26.sep490.g58.otcms_be.imaging.exception;

import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "fu.fa26.sep490.g58.otcms_be.imaging.controller")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ImagingApiExceptionHandler {
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> invalidRequest(ConstraintViolationException exception,
            HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("VALIDATION_FAILED",
                "Invalid imaging request", CorrelationIdFilter.current(request)));
    }

    @ExceptionHandler({HandlerMethodValidationException.class, MissingRequestHeaderException.class,
            IllegalArgumentException.class})
    public ResponseEntity<ErrorResponse> malformedRequest(Exception exception, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("VALIDATION_FAILED",
                "Invalid imaging request", CorrelationIdFilter.current(request)));
    }

    @ExceptionHandler(ImagingOperationException.class)
    public ResponseEntity<ErrorResponse> operation(ImagingOperationException exception,
            HttpServletRequest request) {
        ImagingOperationOutcome outcome = exception.outcome();
        HttpStatus status = switch (outcome) {
            case UNAUTHORIZED_RECORD, RECORD_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case STATE_CONFLICT, VERSION_CONFLICT, IDEMPOTENCY_CONFLICT, EVIDENCE_MISMATCH,
                    ARCHIVE_STUDY_ALREADY_LINKED -> HttpStatus.CONFLICT;
            case FILE_INVALID -> HttpStatus.UNPROCESSABLE_ENTITY;
            case DEPENDENCY_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case RECONCILIATION_REQUIRED -> HttpStatus.ACCEPTED;
        };
        String code = outcome == ImagingOperationOutcome.UNAUTHORIZED_RECORD
                || outcome == ImagingOperationOutcome.RECORD_NOT_FOUND ? "UNAUTHORIZED_RECORD"
                : outcome == ImagingOperationOutcome.FILE_INVALID ? "IMAGING_FILE_INVALID" : outcome.name();
        return ResponseEntity.status(status).body(ErrorResponse.of(code,
                "Imaging operation was rejected", CorrelationIdFilter.current(request)));
    }
}
