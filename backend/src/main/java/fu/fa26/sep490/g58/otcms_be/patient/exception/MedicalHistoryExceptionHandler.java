package fu.fa26.sep490.g58.otcms_be.patient.exception;

import fu.fa26.sep490.g58.otcms_be.common.api.*;
import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse.FieldError;
import fu.fa26.sep490.g58.otcms_be.common.exception.CoordinationGuardUnavailableException;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import fu.fa26.sep490.g58.otcms_be.patient.controller.PatientMedicalHistoryController;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = PatientMedicalHistoryController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MedicalHistoryExceptionHandler {
    @ExceptionHandler(MedicalHistoryException.class)
    public ResponseEntity<ErrorResponse> business(MedicalHistoryException error, HttpServletRequest request) {
        int status = switch (error.getMessage()) {
            case "ACCESS_DENIED" -> 403;
            case "PATIENT_PROFILE_NOT_FOUND" -> 404;
            case "STALE_VERSION" -> 409;
            default -> 400;
        };
        return problem(status, error.getMessage(), error.fieldErrors(), request);
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException error, HttpServletRequest request) {
        return problem(400, "VALIDATION_FAILED", error.getBindingResult().getFieldErrors().stream()
                .map(field -> new FieldError(field.getField(), "MSG-20")).distinct().toList(), request);
    }
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> transport(Exception error, HttpServletRequest request) {
        return problem(400, "VALIDATION_FAILED", List.of(), request);
    }
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> denied(Exception error, HttpServletRequest request) {
        return problem(403, "ACCESS_DENIED", List.of(), request);
    }
    @ExceptionHandler(CoordinationGuardUnavailableException.class)
    public ResponseEntity<ErrorResponse> coordination(CoordinationGuardUnavailableException error, HttpServletRequest request) {
        return new CoordinationFailureExceptionHandler().guardUnavailable(error, request);
    }
    @ExceptionHandler({org.springframework.dao.DataAccessException.class, org.springframework.transaction.TransactionException.class})
    public ResponseEntity<ErrorResponse> database(RuntimeException error, HttpServletRequest request) {
        try { return new CoordinationFailureExceptionHandler().databaseFailure(error, request); }
        catch (RuntimeException unclassified) { return unexpected(unclassified, request); }
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception error, HttpServletRequest request) {
        return problem(500, "MSG-04", List.of(), request);
    }
    private ResponseEntity<ErrorResponse> problem(int status, String code, List<FieldError> fields, HttpServletRequest request) {
        String message = switch (code) {
            case "VALIDATION_FAILED" -> "Thông tin tiền sử bệnh chưa hợp lệ. Vui lòng kiểm tra và chỉnh sửa.";
            case "ACCESS_DENIED", "PATIENT_PROFILE_NOT_FOUND" -> "Không thể truy cập hồ sơ bệnh nhân này. Vui lòng kiểm tra lại thông tin hoặc quyền truy cập.";
            case "STALE_VERSION" -> "Không thể hoàn tất yêu cầu. Hãy tải lại trạng thái trước khi thử lại.";
            default -> "Không thể hoàn tất yêu cầu. Vui lòng thử lại sau hoặc bắt đầu lại.";
        };
        return ResponseEntity.status(status).header("Cache-Control", "no-store")
                .body(ErrorResponse.withFieldErrors(code, message, CorrelationIdFilter.current(request), fields));
    }
}
