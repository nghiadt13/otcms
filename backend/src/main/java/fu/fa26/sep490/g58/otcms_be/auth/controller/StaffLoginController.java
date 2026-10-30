package fu.fa26.sep490.g58.otcms_be.auth.controller;

import fu.fa26.sep490.g58.otcms_be.auth.model.dto.request.CredentialLoginRequest;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.request.AdministratorLoginRequest;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.response.SessionTokenResponse;
import fu.fa26.sep490.g58.otcms_be.auth.service.AuthSessionService;
import fu.fa26.sep490.g58.otcms_be.auth.service.StaffAuthenticationService;
import fu.fa26.sep490.g58.otcms_be.auth.exception.StaffNetworkAccessException;
import fu.fa26.sep490.g58.otcms_be.auth.security.StaffNetworkPolicy;
import fu.fa26.sep490.g58.otcms_be.config.SessionSecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/staff")
public class StaffLoginController {
    private final StaffAuthenticationService login;
    private final StaffNetworkPolicy network;
    private final SessionSecurityProperties properties;

    public StaffLoginController(StaffAuthenticationService login, StaffNetworkPolicy network,
                                SessionSecurityProperties properties) {
        this.login = login;
        this.network = network;
        this.properties = properties;
    }

    @PostMapping("/login")
    public ResponseEntity<SessionTokenResponse> login(@Valid @RequestBody CredentialLoginRequest request,
                                                       HttpServletRequest httpRequest,
                                                       HttpServletResponse response) {
        String ip = network.clientAddress(httpRequest.getRemoteAddr(), httpRequest.getHeader("Forwarded"),
                httpRequest.getHeader("X-Forwarded-For"));
        if (!network.allows(ip)) {
            throw new StaffNetworkAccessException();
        }

        return issue(login.login(request.email(), request.password(), ip,
                httpRequest.getHeader("User-Agent"), false), response);
    }

    @PostMapping("/administrator/login")
    public ResponseEntity<SessionTokenResponse> administratorLogin(
            @Valid @RequestBody AdministratorLoginRequest request, HttpServletRequest httpRequest,
            HttpServletResponse response) {
        String ip = network.clientAddress(httpRequest.getRemoteAddr(), httpRequest.getHeader("Forwarded"),
                httpRequest.getHeader("X-Forwarded-For"));
        if (!network.allows(ip)) {
            throw new StaffNetworkAccessException();
        }
        return issue(login.login(request.getUsername(), request.getPassword(), ip,
                httpRequest.getHeader("User-Agent"), true), response);
    }

    private ResponseEntity<SessionTokenResponse> issue(AuthSessionService.IssuedSession issued,
            HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                ResponseCookie.from("refresh_token", issued.refreshToken())
                        .httpOnly(true)
                        .secure(properties.cookieSecure())
                        .sameSite(properties.cookieSameSite())
                        .path("/api/auth")
                        .maxAge(properties.refreshTokenSeconds())
                        .build()
                        .toString());
        return ResponseEntity.ok(issued.body());
    }
}
