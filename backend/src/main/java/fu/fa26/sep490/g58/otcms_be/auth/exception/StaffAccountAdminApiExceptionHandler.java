package fu.fa26.sep490.g58.otcms_be.auth.exception;

import fu.fa26.sep490.g58.otcms_be.auth.controller.StaffAccountAdminController;
import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = StaffAccountAdminController.class)
public class StaffAccountAdminApiExceptionHandler {
    private static final String DENIED = "Bạn không có quyền thực hiện thao tác này hoặc tài khoản không còn khả dụng.";

    @ExceptionHandler(StaffAccountAdminException.class)
    public ResponseEntity<ErrorResponse> handle(StaffAccountAdminException exception, HttpServletRequest request) {
        return switch (exception.reason()) {
            case CREATE_INPUT -> invalid(false, request);
            case SEARCH_INPUT -> invalid(true, request);
            case USERNAME_TAKEN -> error(409, "USERNAME_TAKEN",
                    "Tên đăng nhập này không thể dùng để tạo tài khoản mới. Vui lòng chọn tên đăng nhập khác.", request);
            case ACTOR_DENIED -> error(403, "ACCESS_DENIED", DENIED, request);
            case TARGET_UNAVAILABLE -> error(404, "ACCESS_DENIED", DENIED, request);
            case STATUS_UNCHANGED -> error(409, "ACCOUNT_STATUS_UNCHANGED",
                    "Không thể thực hiện thao tác này với trạng thái hiện tại.", request);
            case OPERATION_FAILED -> error(500, "ACCOUNT_OPERATION_FAILED",
                    "Không thể hoàn tất yêu cầu. Hãy tải lại trạng thái trước khi thử lại.", request);
        };
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, HandlerMethodValidationException.class})
    public ResponseEntity<ErrorResponse> validation(Exception ignored, HttpServletRequest request) {
        boolean search = "GET".equals(request.getMethod())
                && "/api/v1/admin/staff-accounts".equals(request.getRequestURI().substring(request.getContextPath().length()));
        return invalid(search, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> forbidden(AccessDeniedException ignored, HttpServletRequest request) {
        return error(403, "ACCESS_DENIED", DENIED, request);
    }

    @ExceptionHandler({org.springframework.dao.DataAccessException.class, org.springframework.transaction.TransactionException.class})
    public ResponseEntity<ErrorResponse> persistenceFailure(RuntimeException ignored,
            HttpServletRequest request) {
        return error(500, "ACCOUNT_OPERATION_FAILED",
                "Không thể hoàn tất yêu cầu. Hãy tải lại trạng thái trước khi thử lại.", request);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> unexpected(RuntimeException ignored, HttpServletRequest request) {
        return error(500, "ACCOUNT_OPERATION_FAILED",
                "Không thể hoàn tất yêu cầu. Hãy tải lại trạng thái trước khi thử lại.", request);
    }

    private ResponseEntity<ErrorResponse> invalid(boolean search, HttpServletRequest request) {
        return error(400, "VALIDATION_FAILED", search
                ? "Tiêu chí tìm kiếm không hợp lệ. Vui lòng kiểm tra lại."
                : "Thông tin không hợp lệ. Vui lòng kiểm tra và nhập lại.", request);
    }

    private ResponseEntity<ErrorResponse> error(int status, String code, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).header("Cache-Control", "no-store")
                .body(ErrorResponse.of(code, message, CorrelationIdFilter.current(request)));
    }
}
