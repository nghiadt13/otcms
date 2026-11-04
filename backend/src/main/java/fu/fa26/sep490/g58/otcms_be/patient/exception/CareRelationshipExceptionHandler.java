package fu.fa26.sep490.g58.otcms_be.patient.exception;

import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse;
import fu.fa26.sep490.g58.otcms_be.common.api.CoordinationFailureExceptionHandler;
import fu.fa26.sep490.g58.otcms_be.common.exception.CoordinationGuardUnavailableException;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import fu.fa26.sep490.g58.otcms_be.patient.controller.*;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = {CareRelationshipController.class, CounterCareLinkController.class,
        DependentAddOtpController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CareRelationshipExceptionHandler {
    @ExceptionHandler(CareRelationshipException.class)
    public ResponseEntity<ErrorResponse> care(CareRelationshipException error, HttpServletRequest request) {
        String code = error.getMessage();
        int status = switch (code) {
            case "ACCESS_DENIED" -> 403;
            case "CARE_LINK_NOT_FOUND" -> 404;
            case "CARE_LINK_NOT_AVAILABLE" -> 409;
            case "RATE_LIMIT_EXCEEDED" -> 429;
            case "EMAIL_DELIVERY_UNAVAILABLE" -> 503;
            default -> 400;
        };
        return problem(status, "CARE_LINK_NOT_FOUND".equals(code) ? "CARE_LINK_NOT_AVAILABLE" : code, request, List.of());
    }
    @ExceptionHandler(PatientProfileException.class)
    public ResponseEntity<ErrorResponse> profile(PatientProfileException error, HttpServletRequest request) {
        return problem(400, "VALIDATION_FAILED", request, error.fieldErrors());
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException error, HttpServletRequest request) {
        return problem(400, "VALIDATION_FAILED", request, error.getBindingResult().getFieldErrors().stream()
                .map(f -> new ErrorResponse.FieldError(f.getField(), "MSG-17")).distinct().toList());
    }
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> transport(Exception error, HttpServletRequest request) {
        return problem(400, "VALIDATION_FAILED", request, List.of());
    }
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> denied(Exception error, HttpServletRequest request) {
        return problem(403, "ACCESS_DENIED", request, List.of());
    }
    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<ErrorResponse> stale(Exception error, HttpServletRequest request) {
        return problem(409, "STALE_VERSION", request, List.of());
    }
    @ExceptionHandler(CoordinationGuardUnavailableException.class)
    public ResponseEntity<ErrorResponse> coordination(CoordinationGuardUnavailableException error, HttpServletRequest request) {
        return new CoordinationFailureExceptionHandler().guardUnavailable(error, request);
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> constraint(DataIntegrityViolationException error, HttpServletRequest request) {
        return problem(409, "CARE_LINK_NOT_AVAILABLE", request, List.of());
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception error, HttpServletRequest request) {
        return problem(500, "MSG-04", request, List.of());
    }
    private ResponseEntity<ErrorResponse> problem(int status, String code, HttpServletRequest request, List<ErrorResponse.FieldError> fields) {
        String message = switch (code) {
            case "VALIDATION_FAILED" -> "Thông tin hồ sơ chưa hợp lệ. Vui lòng kiểm tra và nhập lại.";
            case "CARE_LINK_NOT_AVAILABLE" -> "Không thể quản lý hồ sơ người phụ thuộc này. Vui lòng kiểm tra lại mối quan hệ chăm sóc.";
            case "OTP_INVALID" -> "Mã xác thực không hợp lệ hoặc đã hết hạn. Vui lòng kiểm tra và thử lại.";
            case "STALE_VERSION" -> "Không thể hoàn tất yêu cầu. Hãy tải lại trạng thái trước khi thử lại.";
            case "ACCESS_DENIED" -> "Không có quyền thực hiện thao tác này.";
            default -> "Không thể hoàn tất yêu cầu. Vui lòng thử lại sau hoặc bắt đầu lại.";
        };
        return ResponseEntity.status(status).header("Cache-Control", "no-store")
                .body(ErrorResponse.withFieldErrors(code, message, CorrelationIdFilter.current(request), fields));
    }
}
