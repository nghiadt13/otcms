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
        fu.fa26.sep490.g58.otcms_be.diagnostic.controller.DiagnosticTechnicianRequestController.class,
        fu.fa26.sep490.g58.otcms_be.diagnostic.controller.DiagnosticFindingsController.class,
        fu.fa26.sep490.g58.otcms_be.diagnostic.controller.DiagnosticAcquisitionController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DiagnosticApiExceptionHandler {
    /** Wrong role: same body as an unauthorized record, so diagnostic callers see one consistent 403. */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> accessDenied(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of("NOT_AVAILABLE",
                "Diagnostic request operation was rejected", CorrelationIdFilter.current(request)));
    }

    @ExceptionHandler(DiagnosticOperationException.class)
    public ResponseEntity<ErrorResponse> operation(DiagnosticOperationException exception,
            HttpServletRequest request) {
        DiagnosticOperationOutcome outcome = exception.outcome();
        HttpStatus status = switch (outcome) {
            case UNAUTHORIZED_RECORD -> HttpStatus.FORBIDDEN;
            case RECORD_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case STATE_CONFLICT, VERSION_CONFLICT, IDEMPOTENCY_CONFLICT, ORDER_NOT_PAID, PATIENT_NOT_CALLED -> HttpStatus.CONFLICT;
            case DEPENDENCY_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case VISIT_NOT_ELIGIBLE, SERVICE_NOT_ELIGIBLE, ROOM_NOT_READY, EVIDENCE_MISMATCH, REPORT_NOT_READY ->
                    HttpStatus.UNPROCESSABLE_ENTITY;
            case ALLOWED -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        String code = outcome == DiagnosticOperationOutcome.ORDER_NOT_PAID ? "MSG-95"
                : outcome == DiagnosticOperationOutcome.PATIENT_NOT_CALLED ? "MSG-42"
                : outcome == DiagnosticOperationOutcome.UNAUTHORIZED_RECORD
                || outcome == DiagnosticOperationOutcome.RECORD_NOT_FOUND ? "NOT_AVAILABLE" : outcome.name();
        if (isFindings(request.getRequestURI())) {
            code = switch (outcome) {
                case UNAUTHORIZED_RECORD -> "MSG-40";
                case RECORD_NOT_FOUND -> "MSG-41";
                case EVIDENCE_MISMATCH -> request.getRequestURI().endsWith("/reports") ? "MSG-47" : "MSG-39";
                case STATE_CONFLICT, VERSION_CONFLICT, IDEMPOTENCY_CONFLICT, VISIT_NOT_ELIGIBLE,
                        REPORT_NOT_READY -> "MSG-42";
                case DEPENDENCY_UNAVAILABLE -> "MSG-04";
                default -> code;
            };
        }
        return ResponseEntity.status(status).body(ErrorResponse.of(code, "Diagnostic request operation was rejected",
                CorrelationIdFilter.current(request)));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, IllegalArgumentException.class,
            jakarta.validation.ConstraintViolationException.class})
    public ResponseEntity<ErrorResponse> invalid(Exception exception, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(isFindings(request.getRequestURI()) ? "MSG-39" : "VALIDATION_FAILED", "Invalid diagnostic request",
                CorrelationIdFilter.current(request)));
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ResponseEntity<ErrorResponse> persistence(RuntimeException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ErrorResponse.of(
                isFindings(request.getRequestURI()) ? "MSG-04" : "DEPENDENCY_UNAVAILABLE", "A required diagnostic operation is temporarily unavailable",
                CorrelationIdFilter.current(request)));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> optimisticConflict(ObjectOptimisticLockingFailureException exception,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(isFindings(request.getRequestURI()) ? "MSG-42" : "VERSION_CONFLICT",
                "The diagnostic request changed; reload it before retrying",
                CorrelationIdFilter.current(request)));
    }

    private static boolean isFindings(String uri) {
        return uri.matches("/api/v1/imaging-requests/[^/]+/(?:reviews|findings(?:/.*)?|reports)");
    }
}
