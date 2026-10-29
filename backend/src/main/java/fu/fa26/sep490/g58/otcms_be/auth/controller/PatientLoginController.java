package fu.fa26.sep490.g58.otcms_be.auth.controller;

import fu.fa26.sep490.g58.otcms_be.auth.model.dto.request.CredentialLoginRequest;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.response.CredentialLoginResponse;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.response.SessionTokenResponse;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.security.StaffNetworkPolicy;
import fu.fa26.sep490.g58.otcms_be.auth.service.AuthSessionService;
import fu.fa26.sep490.g58.otcms_be.auth.service.CredentialLoginService;
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
@RequestMapping("/api/auth/patient")
public class PatientLoginController {
    private final CredentialLoginService login;
    private final StaffNetworkPolicy network;
    private final AuthSessionService sessions;
    private final SessionSecurityProperties properties;

    public PatientLoginController(CredentialLoginService login, StaffNetworkPolicy network,
                                  AuthSessionService sessions, SessionSecurityProperties properties) {
        this.login = login;
        this.network = network;
        this.sessions = sessions;
        this.properties = properties;
    }

    @PostMapping("/login")
    public ResponseEntity<SessionTokenResponse> login(@Valid @RequestBody CredentialLoginRequest request,
                                                       HttpServletRequest httpRequest,
                                                       HttpServletResponse response) {
        String ip = network.clientAddress(httpRequest.getRemoteAddr(), httpRequest.getHeader("Forwarded"),
                httpRequest.getHeader("X-Forwarded-For"));
        CredentialLoginResponse authenticated = login.login(request.email(), request.password(),
                AccountType.PATIENT, ip);
        AuthSessionService.IssuedSession issued = sessions.create(authenticated.accountId(), ip,
                httpRequest.getHeader("User-Agent"));
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(issued.refreshToken()).toString());
        return ResponseEntity.ok(issued.body());
    }

    private ResponseCookie cookie(String value) {
        return ResponseCookie.from("refresh_token", value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite(properties.cookieSameSite())
                .path("/api/auth")
                .maxAge(properties.refreshTokenSeconds())
                .build();
    }
}
