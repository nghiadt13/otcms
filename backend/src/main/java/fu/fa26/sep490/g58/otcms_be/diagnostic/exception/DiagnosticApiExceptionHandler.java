package fu.fa26.sep490.g58.otcms_be.diagnostic.exception;

import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import fu.fa26.sep490.g58.otcms_be.diagnostic.controller.DiagnosticRequestController;
import fu.fa26.sep490.g58.otcms_be.diagnostic.controller.DiagnosticHistoryController;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = {DiagnosticRequestController.class,
        DiagnosticHistoryController.class,
        fu.fa26.sep490.g58.otcms_be.diagnostic.controller.DiagnosticFindingsController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DiagnosticApiExceptionHandler {
    @ExceptionHandler(DiagnosticOperationException.class)
    public ResponseEntity<ErrorResponse> operation(DiagnosticOperationException exception,
            HttpServletRequest request) {
        DiagnosticOperationOutcome outcome = exception.outcome();
        HttpStatus status = switch (outcome) {
            case UNAUTHORIZED_RECORD -> HttpStatus.FORBIDDEN;
            case RECORD_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case STATE_CONFLICT, VERSION_CONFLICT, IDEMPOTENCY_CONFLICT -> HttpStatus.CONFLICT;
            case DEPENDENCY_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case VISIT_NOT_ELIGIBLE, SERVICE_NOT_ELIGIBLE, ROOM_NOT_READY, EVIDENCE_MISMATCH, REPORT_NOT_READY ->
                    HttpStatus.UNPROCESSABLE_ENTITY;
            case ALLOWED -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        String code = outcome == DiagnosticOperationOutcome.UNAUTHORIZED_RECORD
                || outcome == DiagnosticOperationOutcome.RECORD_NOT_FOUND ? "NOT_AVAILABLE" : outcome.name();
        return ResponseEntity.status(status).body(ErrorResponse.of(code, "Diagnostic request operation was rejected",
                CorrelationIdFilter.current(request)));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, IllegalArgumentException.class})
    public ResponseEntity<ErrorResponse> invalid(Exception exception, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("VALIDATION_FAILED", "Invalid diagnostic request",
                CorrelationIdFilter.current(request)));
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ResponseEntity<ErrorResponse> persistence(RuntimeException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ErrorResponse.of(
                "DEPENDENCY_UNAVAILABLE", "A required diagnostic operation is temporarily unavailable",
                CorrelationIdFilter.current(request)));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> optimisticConflict(ObjectOptimisticLockingFailureException exception,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of("VERSION_CONFLICT",
                "The diagnostic request changed; reload it before retrying",
                CorrelationIdFilter.current(request)));
    }
}
