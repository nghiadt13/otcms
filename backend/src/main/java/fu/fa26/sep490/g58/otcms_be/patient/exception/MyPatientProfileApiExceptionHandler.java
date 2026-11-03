package fu.fa26.sep490.g58.otcms_be.patient.exception;

import fu.fa26.sep490.g58.otcms_be.patient.controller.MyPatientProfileController;
import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = MyPatientProfileController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MyPatientProfileApiExceptionHandler {
    @ExceptionHandler(MyPatientProfileException.class)
    public ResponseEntity<ErrorResponse> operation(MyPatientProfileException e, HttpServletRequest r) {
        return switch (e.reason()) {
            case ACCESS_DENIED -> problem(403, "MSG-14", List.of(), r);
            case NOT_FOUND -> problem(404, "MSG-14", List.of(), r);
            case STALE_VERSION -> problem(409, "STALE_VERSION", List.of(), r);
            case INVALID_INPUT -> problem(400, "MSG-08", e.fields(), r);
        };
    }
    @ExceptionHandler({org.springframework.dao.OptimisticLockingFailureException.class, jakarta.persistence.OptimisticLockException.class})
    public ResponseEntity<ErrorResponse> stale(Exception e, HttpServletRequest r) { return problem(409, "STALE_VERSION", List.of(), r); }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> denied(Exception e, HttpServletRequest r) { return problem(403, "MSG-14", List.of(), r); }

    @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> invalid(Exception e, HttpServletRequest r) { return problem(400, "MSG-08", List.of(), r); }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception e, HttpServletRequest r) { return problem(500, "MSG-04", List.of(), r); }

    private ResponseEntity<ErrorResponse> problem(int status, String code, List<ErrorResponse.FieldError> fields, HttpServletRequest r) {
        String message = switch (code) {
            case "MSG-14" -> "Bạn không có quyền thực hiện thao tác này hoặc tài khoản không còn khả dụng.";
            case "STALE_VERSION" -> "Không thể hoàn tất yêu cầu. Hãy tải lại trạng thái trước khi thử lại.";
            case "MSG-08" -> "Không thể hoàn tất yêu cầu với thông tin hiện tại.";
            default -> "Không thể hoàn tất yêu cầu. Vui lòng thử lại sau hoặc bắt đầu lại.";
        };
        return ResponseEntity.status(status).header("Cache-Control", "no-store")
                .body(ErrorResponse.withFieldErrors(code, message, CorrelationIdFilter.current(r), fields));
    }
}
