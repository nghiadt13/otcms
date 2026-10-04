package fu.fa26.sep490.g58.otcms_be.auth.controller;

import fu.fa26.sep490.g58.otcms_be.auth.service.CredentialLoginException;
import fu.fa26.sep490.g58.otcms_be.auth.service.SessionException;
import fu.fa26.sep490.g58.otcms_be.auth.service.GoogleAuthenticationException;
import fu.fa26.sep490.g58.otcms_be.auth.service.PasswordRecoveryException;
import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

@RestControllerAdvice
public class AuthExceptionHandler {
    @ExceptionHandler(PasswordRecoveryException.class)
    public ResponseEntity<ErrorResponse> password(PasswordRecoveryException ex) {
        HttpStatus status = "AUTHENTICATION_FAILED".equals(ex.getMessage()) ? HttpStatus.UNAUTHORIZED : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(ErrorResponse.of(ex.getMessage(), "Password recovery request failed", UUID.randomUUID().toString()));
    }
    @ExceptionHandler(GoogleAuthenticationException.class)
    public ResponseEntity<ErrorResponse> google(GoogleAuthenticationException ex) {
        HttpStatus status = "IDENTITY_LINK_CONFLICT".equals(ex.getMessage()) ? HttpStatus.CONFLICT
                : "GOOGLE_ONBOARDING_REQUIRED".equals(ex.getMessage()) ? HttpStatus.CONFLICT : HttpStatus.UNAUTHORIZED;
        return ResponseEntity.status(status).body(ErrorResponse.of(ex.getMessage(),
                "GOOGLE_ONBOARDING_REQUIRED".equals(ex.getMessage()) ? "Google onboarding required" : "Authentication failed",
                UUID.randomUUID().toString()));
    }
    @ExceptionHandler(SessionException.class)
    public ResponseEntity<ErrorResponse> session(SessionException ex) {
        HttpStatus status = "CSRF_VALIDATION_FAILED".equals(ex.getMessage()) ? HttpStatus.FORBIDDEN : HttpStatus.UNAUTHORIZED;
        String message = status == HttpStatus.FORBIDDEN ? "CSRF validation failed" : "Authentication failed";
        return ResponseEntity.status(status).body(ErrorResponse.of(ex.getMessage(), message, UUID.randomUUID().toString()));
    }
    @ExceptionHandler(CredentialLoginException.class)
    public ResponseEntity<ErrorResponse> credential(CredentialLoginException ex) {
        HttpStatus status = "RATE_LIMIT_EXCEEDED".equals(ex.getMessage()) ? HttpStatus.TOO_MANY_REQUESTS
                : "RATE_LIMIT_UNAVAILABLE".equals(ex.getMessage()) ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.UNAUTHORIZED;
        String code = ex.getMessage();
        String message = status == HttpStatus.TOO_MANY_REQUESTS ? "Too many authentication attempts"
                : status == HttpStatus.SERVICE_UNAVAILABLE ? "Authentication service temporarily unavailable" : "Authentication failed";
        return ResponseEntity.status(status).body(ErrorResponse.of(code, message, UUID.randomUUID().toString()));
    }

    @ExceptionHandler(StaffNetworkAccessException.class)
    public ResponseEntity<ErrorResponse> staffNetwork() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of("STAFF_NETWORK_ACCESS_DENIED", "Staff network access denied", UUID.randomUUID().toString()));
    }
}
