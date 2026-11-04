package fu.fa26.sep490.g58.otcms_be.auth.exception;

import fu.fa26.sep490.g58.otcms_be.auth.controller.MyAccountController;
import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = MyAccountController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MyAccountApiExceptionHandler implements org.springframework.web.servlet.HandlerExceptionResolver {
    /** Multipart parsing may fail before Spring can identify the controller for scoped advice. */
    @Override
    public org.springframework.web.servlet.ModelAndView resolveException(HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response, Object handler, Exception exception) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!(exception instanceof org.springframework.web.multipart.MaxUploadSizeExceededException)
                || !"PUT".equals(request.getMethod()) || !"/api/v1/me/avatar".equals(path)) return null;
        var error = problem(400, "MSG-08", request);
        try {
            response.setStatus(400);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Cache-Control", "no-store");
            response.getWriter().write(tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build()
                    .writeValueAsString(error.getBody()));
            return new org.springframework.web.servlet.ModelAndView();
        } catch (java.io.IOException ignored) { return null; }
    }
    @ExceptionHandler(MyAccountOperationException.class)
    public ResponseEntity<ErrorResponse> operation(MyAccountOperationException e, HttpServletRequest r) {
        return switch (e.reason()) {
            case ACCESS_DENIED -> problem(403, "MSG-14", r);
            case AVATAR_NOT_FOUND -> problem(404, "MSG-08", r);
            case OTP_INVALID -> problem(400, "MSG-82", r);
            default -> problem(400, "MSG-08", r);
        };
    }
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> denied(Exception e, HttpServletRequest r) { return problem(403, "MSG-14", r); }

    @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.multipart.MultipartException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class,
            org.springframework.web.HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<ErrorResponse> invalid(Exception e, HttpServletRequest r) { return problem(400, "MSG-08", r); }

    @ExceptionHandler(EmailChangeWriteException.class)
    public ResponseEntity<ErrorResponse> emailWrite(EmailChangeWriteException e, HttpServletRequest r) {
        return e.reason() == EmailChangeWriteException.Reason.COLLISION
                ? problem(400, "MSG-08", r) : problem(500, "MSG-04", r);
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception e, HttpServletRequest r) { return problem(500, "MSG-04", r); }

    private ResponseEntity<ErrorResponse> problem(int status, String code, HttpServletRequest r) {
        String message = switch (code) {
            case "MSG-14" -> "Bạn không có quyền thực hiện thao tác này hoặc tài khoản không còn khả dụng.";
            case "MSG-82" -> "Mã OTP không đúng hoặc đã hết hạn.";
            case "MSG-08" -> "Không thể hoàn tất yêu cầu với thông tin hiện tại.";
            default -> "Không thể hoàn tất yêu cầu. Vui lòng thử lại sau hoặc bắt đầu lại.";
        };
        return ResponseEntity.status(status).header("Cache-Control", "no-store")
                .body(ErrorResponse.of(code, message, CorrelationIdFilter.current(r)));
    }
}
