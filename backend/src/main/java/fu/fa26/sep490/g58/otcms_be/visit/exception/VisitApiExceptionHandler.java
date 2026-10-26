package fu.fa26.sep490.g58.otcms_be.visit.exception;

import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;

@RestControllerAdvice(assignableTypes = {
        fu.fa26.sep490.g58.otcms_be.visit.controller.WalkInVisitController.class,
        fu.fa26.sep490.g58.otcms_be.visit.controller.VisitCareAssignmentController.class,
        fu.fa26.sep490.g58.otcms_be.visit.controller.VisitRoomAdminController.class
})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class VisitApiExceptionHandler {
    @ExceptionHandler(VisitOperationException.class)
    public ResponseEntity<ErrorResponse> handle(VisitOperationException exception, HttpServletRequest request) {
        HttpStatus status = switch (exception.code()) {
            case "ACCESS_DENIED" -> HttpStatus.FORBIDDEN;
            case "RESOURCE_NOT_FOUND", "ROOM_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "IDEMPOTENCY_CONFLICT", "VISIT_LINK_CONFLICT", "VISIT_CAPACITY_NOT_AVAILABLE",
                    "VISIT_CARE_NOT_CHANGEABLE", "SLOT_UNAVAILABLE", "DUPLICATE_ROOM_CODE",
                    "ROOM_HAS_ACTIVE_RESERVATIONS" -> HttpStatus.CONFLICT;
            case "VISIT_CARE_STEP_NOT_CONFIGURED" -> HttpStatus.SERVICE_UNAVAILABLE;
            case "DEPENDENCY_UNAVAILABLE" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(ErrorResponse.of(exception.code(), "Visit command was rejected",
                CorrelationIdFilter.current(request)));
    }

    @ExceptionHandler(VisitCapacityConflictException.class)
    public ResponseEntity<ErrorResponse> capacityConflict(VisitCapacityConflictException exception,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(exception.code(),
                "Visit capacity is unavailable; coordinate the visit before continuing",
                CorrelationIdFilter.current(request)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> malformed(HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("VALIDATION_FAILED", "Invalid request body",
                CorrelationIdFilter.current(request)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> invalidRequest(MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("VALIDATION_FAILED", "Invalid request body",
                CorrelationIdFilter.current(request)));
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ResponseEntity<ErrorResponse> databaseFailure(RuntimeException exception, HttpServletRequest request) {
        String sqlState = findSqlState(exception);
        if ("23P01".equals(sqlState)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of("SLOT_UNAVAILABLE",
                    "The requested appointment slot is no longer available", CorrelationIdFilter.current(request)));
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ErrorResponse.of("DEPENDENCY_UNAVAILABLE",
                "A required database operation is temporarily unavailable", CorrelationIdFilter.current(request)));
    }

    private static String findSqlState(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException) {
                for (SQLException next = sqlException; next != null; next = next.getNextException()) {
                    if ("23P01".equals(next.getSQLState())) return "23P01";
                }
            }
        }
        return null;
    }
}
