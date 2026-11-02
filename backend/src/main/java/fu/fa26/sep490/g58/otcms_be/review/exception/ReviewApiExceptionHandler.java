package fu.fa26.sep490.g58.otcms_be.review.exception;

import fu.fa26.sep490.g58.otcms_be.review.controller.*;
import fu.fa26.sep490.g58.otcms_be.review.model.dto.ReviewDtos.FieldError;
import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;

/** Uses the production error envelope, with optional root fieldErrors for validation. */
@RestControllerAdvice(assignableTypes = {PatientReviewController.class, PublicReviewController.class, AdminReviewController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ReviewApiExceptionHandler {
    @ExceptionHandler(ReviewOperationException.class)
    public ResponseEntity<ErrorResponse> business(ReviewOperationException error) {
        return problem(error.status(), error.code(), error.fieldErrors());
    }
    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<ErrorResponse> optimistic(Exception error) { return problem(409, "MSG-56", List.of()); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> integrity(DataIntegrityViolationException error) {
        // Only the review-per-visit unique constraint represents a business conflict.
        Throwable cause = error;
        while (cause != null) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                    && "uk_patient_reviews_visit".equals(violation.getConstraintName()))
                return problem(409, "MSG-56", List.of());
            if (cause instanceof java.sql.SQLException sql) {
                for (var next = sql; next != null; next = next.getNextException()) {
                    if ("23505".equals(next.getSQLState()) && next.getMessage() != null
                            && next.getMessage().contains("\"uk_patient_reviews_visit\""))
                        return problem(409, "MSG-56", List.of());
                }
            }
            cause = cause.getCause();
        }
        return problem(500, "MSG-04", List.of());
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> malformed(HttpMessageNotReadableException error, HttpServletRequest request) {
        String field = request.getRequestURI().endsWith("/reply") ? "text" : "visitId";
        Throwable cause = error;
        while (cause != null) {
            if (cause instanceof tools.jackson.databind.exc.MismatchedInputException mismatch
                    && !mismatch.getPath().isEmpty()) field = mismatch.getPath().getLast().getPropertyName();
            cause = cause.getCause();
        }
        return problem(400, "MSG-57", List.of(new FieldError(field == null ? "text" : field, "MSG-57")));
    }
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> query(Exception error) { return problem(400, "MSG-54", List.of()); }
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> denied(Exception error) { return problem(403, "MSG-50", List.of()); }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception error) { return problem(500, "MSG-04", List.of()); }
    private ResponseEntity<ErrorResponse> problem(int status, String code, List<FieldError> fields) {
        var attributes = RequestContextHolder.getRequestAttributes();
        String correlation = attributes instanceof ServletRequestAttributes servlet
                ? CorrelationIdFilter.current(servlet.getRequest()) : UUID.randomUUID().toString();
        var body = ErrorResponse.withFieldErrors(code, "Review request could not be completed", correlation,
                fields.stream().map(f -> new ErrorResponse.FieldError(f.field(), f.code())).toList());
        return ResponseEntity.status(status).body(body);
    }
}
