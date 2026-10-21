package fu.fa26.sep490.g58.otcms_be.auth.controller;

import fu.fa26.sep490.g58.otcms_be.auth.exception.SessionException;
import fu.fa26.sep490.g58.otcms_be.auth.exception.StaffNetworkAccessException;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.response.CsrfTokenResponse;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.response.SessionTokenResponse;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.security.CsrfTokenService;
import fu.fa26.sep490.g58.otcms_be.auth.security.StaffNetworkPolicy;
import fu.fa26.sep490.g58.otcms_be.auth.service.AuthSessionService;
import fu.fa26.sep490.g58.otcms_be.config.SessionSecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class SessionSecurityController {
    private final CsrfTokenService csrf;
    private final AuthSessionService sessions;
    private final SessionSecurityProperties properties;
    private final StaffNetworkPolicy network;

    public SessionSecurityController(CsrfTokenService csrf, AuthSessionService sessions,
                                    SessionSecurityProperties properties, StaffNetworkPolicy network) {
        this.csrf = csrf;
        this.sessions = sessions;
        this.properties = properties;
        this.network = network;
    }

    @GetMapping("/csrf")
    public ResponseEntity<CsrfTokenResponse> csrf(HttpServletResponse response) {
        String token = csrf.issue();
        // Remove the old auth-only cookie so it cannot shadow the shared CSRF cookie.
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("XSRF-TOKEN", "")
                .httpOnly(false)
                .secure(properties.cookieSecure())
                .sameSite(properties.cookieSameSite())
                .path("/api/auth")
                .maxAge(0)
                .build()
                .toString());
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("XSRF-TOKEN", token)
                .httpOnly(false)
                .secure(properties.cookieSecure())
                .sameSite(properties.cookieSameSite())
                .path("/")
                .maxAge(properties.refreshTokenSeconds())
                .build()
                .toString());
        return ResponseEntity.ok(new CsrfTokenResponse(token));
    }

    @PostMapping("/refresh")
    public ResponseEntity<SessionTokenResponse> refresh(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            @CookieValue(name = "XSRF-TOKEN", required = false) String csrfCookie,
            @RequestHeader(name = "X-CSRF-TOKEN", required = false) String csrfHeader,
            HttpServletRequest request,
            HttpServletResponse response) {
        requireCsrf(csrfCookie, csrfHeader);
        requireStaffNetwork(refreshToken, request);
        try {
            AuthSessionService.IssuedSession issued = sessions.rotate(refreshToken);
            response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(issued.refreshToken()).toString());
            return ResponseEntity.ok(issued.body());
        } catch (SessionException exception) {
            response.addHeader(HttpHeaders.SET_COOKIE, clearRefresh().toString());
            throw exception;
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            @CookieValue(name = "XSRF-TOKEN", required = false) String csrfCookie,
            @RequestHeader(name = "X-CSRF-TOKEN", required = false) String csrfHeader,
            HttpServletRequest request,
            HttpServletResponse response) {
        requireCsrf(csrfCookie, csrfHeader);
        requireStaffNetwork(refreshToken, request);
        sessions.logout(refreshToken);
        response.addHeader(HttpHeaders.SET_COOKIE, clearRefresh().toString());
        return ResponseEntity.noContent().build();
    }

    private void requireCsrf(String cookie, String header) {
        if (!csrf.valid(cookie, header)) {
            throw new SessionException("CSRF_VALIDATION_FAILED");
        }
    }

    private ResponseCookie refreshCookie(String value) {
        return ResponseCookie.from("refresh_token", value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite(properties.cookieSameSite())
                .path("/api/auth")
                .maxAge(properties.refreshTokenSeconds())
                .build();
    }

    private ResponseCookie clearRefresh() {
        return ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite(properties.cookieSameSite())
                .path("/api/auth")
                .maxAge(0)
                .build();
    }

    private void requireStaffNetwork(String refreshToken, HttpServletRequest request) {
        AccountType accountType = sessions.accountTypeForRefresh(refreshToken).orElse(null);
        if (accountType != null && accountType.isStaff()) {
            String clientAddress = network.clientAddress(request.getRemoteAddr(), request.getHeader("Forwarded"),
                    request.getHeader("X-Forwarded-For"));
            if (!network.allows(clientAddress)) {
                throw new StaffNetworkAccessException();
            }
        }
    }
}
