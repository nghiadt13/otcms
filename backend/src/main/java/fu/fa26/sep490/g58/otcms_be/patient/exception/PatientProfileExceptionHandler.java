package fu.fa26.sep490.g58.otcms_be.patient.exception;

import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import fu.fa26.sep490.g58.otcms_be.patient.controller.PatientProfileController;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import fu.fa26.sep490.g58.otcms_be.common.api.CoordinationFailureExceptionHandler;
import fu.fa26.sep490.g58.otcms_be.common.exception.CoordinationGuardUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.persistence.OptimisticLockException;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@RestControllerAdvice(assignableTypes = PatientProfileController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PatientProfileExceptionHandler {
    @ExceptionHandler(PatientProfileException.class)
    public ResponseEntity<ErrorResponse> handle(PatientProfileException exception) {
        String code = exception.getMessage();
        int status = switch (code) {
            case "FORBIDDEN", "ACCESS_DENIED" -> 403;
            case "PATIENT_PROFILE_NOT_FOUND" -> 404;
            case "PATIENT_MATCH_AMBIGUOUS", "PATIENT_PROFILE_DUPLICATE", "STALE_VERSION" -> 409;
            default -> 400;
        };
        return problem(status, msgCodeFor(code), exception.fieldErrors());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException error) {
        return problem(400, "MSG-17", error.getBindingResult().getFieldErrors().stream()
                .map(f -> new ErrorResponse.FieldError(f.getField(), "MSG-17")).distinct().toList());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> transport(Exception error) {
        String field = error instanceof MethodArgumentTypeMismatchException mismatch ? mismatch.getName()
                : error instanceof MissingServletRequestParameterException missing ? missing.getParameterName() : null;
        return problem(400, "MSG-17", field == null ? List.of()
                : List.of(new ErrorResponse.FieldError(field, "MSG-17")));
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<ErrorResponse> stale(Exception error) { return problem(409, "MSG-16", List.of()); }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> denied(Exception error) { return problem(403, "MSG-19", List.of()); }

    @ExceptionHandler(CoordinationGuardUnavailableException.class)
    public ResponseEntity<ErrorResponse> coordination(CoordinationGuardUnavailableException error, HttpServletRequest request) {
        return new CoordinationFailureExceptionHandler().guardUnavailable(error, request);
    }

    @ExceptionHandler({org.springframework.dao.DataAccessException.class, org.springframework.transaction.TransactionException.class})
    public ResponseEntity<ErrorResponse> database(RuntimeException error, HttpServletRequest request) {
        try { return new CoordinationFailureExceptionHandler().databaseFailure(error, request); }
        catch (RuntimeException unclassified) { return unexpected(unclassified); }
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception error) { return problem(500, "MSG-04", List.of()); }

    /** Maps the technical patient-profile code to its R3 MSG code; the technical code only drives the HTTP status. */
    private static String msgCodeFor(String technical) {
        return switch (technical) {
            case "PATIENT_PROFILE_NOT_FOUND", "ACCESS_DENIED", "FORBIDDEN" -> "MSG-19";
            case "PATIENT_MATCH_AMBIGUOUS", "PATIENT_PROFILE_DUPLICATE" -> "MSG-18";
            case "STALE_VERSION" -> "MSG-16";
            case "VALIDATION_FAILED" -> "MSG-17";
            default -> "MSG-04";
        };
    }

    private ResponseEntity<ErrorResponse> problem(int status, String code, List<ErrorResponse.FieldError> fields) {
        String message = switch (code) {
            case "MSG-17" -> "Thông tin hồ sơ chưa hợp lệ. Vui lòng kiểm tra và nhập lại.";
            case "MSG-18" -> "Chưa xác định được đúng hồ sơ bệnh nhân. Vui lòng hỏi thêm họ tên hoặc số CCCD/định danh.";
            case "MSG-19" -> "Không thể truy cập hồ sơ bệnh nhân này. Vui lòng kiểm tra lại thông tin hoặc quyền truy cập.";
            case "MSG-16" -> "Không thể hoàn tất yêu cầu. Hãy tải lại trạng thái trước khi thử lại.";
            default -> "Không thể hoàn tất yêu cầu. Vui lòng thử lại sau hoặc bắt đầu lại.";
        };
        var attributes = RequestContextHolder.getRequestAttributes();
        String correlationId = attributes instanceof ServletRequestAttributes servlet
                ? CorrelationIdFilter.current(servlet.getRequest()) : UUID.randomUUID().toString();
        return ResponseEntity.status(status).body(ErrorResponse.withFieldErrors(code, message, correlationId, fields));
    }
}
