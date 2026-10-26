package fu.fa26.sep490.g58.otcms_be.common.api;


import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import fu.fa26.sep490.g58.otcms_be.common.exception.CoordinationGuardUnavailableException;
import fu.fa26.sep490.g58.otcms_be.common.exception.ResourceMutationBlockedException;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class CoordinationFailureExceptionHandler {
    @ExceptionHandler(ResourceMutationBlockedException.class)
    public ResponseEntity<ErrorResponse> resourceMutationBlocked(ResourceMutationBlockedException exception,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.mutationBlocked(exception.impact(),
                correlationId(request)));
    }

    @ExceptionHandler(CoordinationGuardUnavailableException.class)
    public ResponseEntity<ErrorResponse> guardUnavailable(CoordinationGuardUnavailableException exception,
            HttpServletRequest request) {
        return dependencyUnavailable(request);
    }

    public ResponseEntity<ErrorResponse> guardUnavailable(CoordinationGuardUnavailableException exception) {
        return dependencyUnavailable(null);
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ResponseEntity<ErrorResponse> databaseFailure(RuntimeException exception, HttpServletRequest request) {
        String sqlState = findRelevantSqlState(exception);
        if ("23P01".equals(sqlState)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of("SLOT_UNAVAILABLE",
                    "The requested appointment slot is no longer available", correlationId(request)));
        }
        if ("55P03".equals(sqlState) || "57014".equals(sqlState) || "40P01".equals(sqlState)) {
            return dependencyUnavailable(request);
        }
        throw exception;
    }

    /** Keeps direct service callers source-compatible; MVC uses the request-aware handler above. */
    public ResponseEntity<ErrorResponse> databaseFailure(RuntimeException exception) {
        return databaseFailure(exception, null);
    }

    private ResponseEntity<ErrorResponse> dependencyUnavailable(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ErrorResponse.of("DEPENDENCY_UNAVAILABLE",
                "A required database operation is temporarily unavailable", correlationId(request)));
    }

    private String correlationId(HttpServletRequest request) {
        return request == null ? UUID.randomUUID().toString() : CorrelationIdFilter.current(request);
    }

    private String findRelevantSqlState(Throwable failure) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable cause = failure; cause != null && visited.add(cause); cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException) {
                Set<SQLException> visitedSqlExceptions = Collections.newSetFromMap(new IdentityHashMap<>());
                for (SQLException next = sqlException; next != null && visitedSqlExceptions.add(next); next = next.getNextException()) {
                    String state = next.getSQLState();
                    if ("23P01".equals(state) || "55P03".equals(state) || "57014".equals(state) || "40P01".equals(state)) {
                        return state;
                    }
                }
            }
        }
        return null;
    }
}
