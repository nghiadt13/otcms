package fu.fa26.sep490.g58.otcms_be.common.api;

import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Fallback for {@code @PreAuthorize} denials in controllers whose feature advice has no AccessDenied handler:
 * a signed-in caller with the wrong role gets 403 and the standard error body instead of Spring's default
 * error page (feature advices that map to their own code take precedence).
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class AccessDeniedExceptionHandler {
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> denied(AccessDeniedException exception, HttpServletRequest request) {
        // Anonymous callers must still get the 401 entry point, so let the security filter handle them.
        if (SecurityContextHolder.getContext().getAuthentication() instanceof AnonymousAuthenticationToken) {
            throw exception;
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of("ACCESS_DENIED",
                "Access denied", CorrelationIdFilter.current(request)));
    }
}
