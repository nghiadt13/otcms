package fu.fa26.sep490.g58.otcms_be.auth.controller;

import fu.fa26.sep490.g58.otcms_be.auth.security.StaffNetworkPolicy;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.response.SessionTokenResponse;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.request.GoogleExchangeRequest;
import fu.fa26.sep490.g58.otcms_be.auth.service.GoogleAuthenticationService;
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
@RequestMapping("/api/auth/patient/google")
public class GoogleExchangeController {
    private final GoogleAuthenticationService google;
    private final StaffNetworkPolicy network;
    private final SessionSecurityProperties properties;

    public GoogleExchangeController(GoogleAuthenticationService google, StaffNetworkPolicy network, SessionSecurityProperties properties) {
        this.google = google;
        this.network = network;
        this.properties = properties;
    }

    @PostMapping("/exchange")
    public ResponseEntity<SessionTokenResponse> exchange(@Valid @RequestBody GoogleExchangeRequest request,
                                                          HttpServletRequest http, HttpServletResponse response) {
        String ip = network.clientAddress(http.getRemoteAddr(), http.getHeader("Forwarded"),
                http.getHeader("X-Forwarded-For"));
        var issued = google.exchange(request, ip, http.getHeader("User-Agent"));
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("refresh_token", issued.refreshToken())
                .httpOnly(true)
                .secure(true)
                .sameSite(properties.cookieSameSite())
                .path("/api/auth")
                .maxAge(properties.refreshTokenSeconds())
                .build()
                .toString());
        return ResponseEntity.ok(issued.body());
    }
}
