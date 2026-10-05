package fu.fa26.sep490.g58.otcms_be.auth.controller;

import fu.fa26.sep490.g58.otcms_be.auth.dto.*;
import fu.fa26.sep490.g58.otcms_be.auth.entity.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.service.AuthSessionService;
import fu.fa26.sep490.g58.otcms_be.auth.service.CredentialLoginService;
import fu.fa26.sep490.g58.otcms_be.config.SessionSecurityProperties;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/auth/staff")
public class StaffLoginController {
    private final CredentialLoginService login; private final StaffNetworkPolicy network; private final AuthSessionService sessions; private final SessionSecurityProperties properties;
    public StaffLoginController(CredentialLoginService login,StaffNetworkPolicy network,AuthSessionService sessions,SessionSecurityProperties properties){this.login=login;this.network=network;this.sessions=sessions;this.properties=properties;}
    @PostMapping("/login") public ResponseEntity<SessionTokenResponse> login(@Valid @RequestBody CredentialLoginRequest request,HttpServletRequest http,HttpServletResponse response){String ip=network.clientAddress(http.getRemoteAddr(),http.getHeader("Forwarded"),http.getHeader("X-Forwarded-For"));if(!network.allows(ip))throw new StaffNetworkAccessException();CredentialLoginResponse authenticated=login.login(request.email(),request.password(),AccountType.STAFF,ip);var issued=sessions.create(authenticated.accountId(),ip,http.getHeader("User-Agent"));response.addHeader(HttpHeaders.SET_COOKIE,ResponseCookie.from("refresh_token",issued.refreshToken()).httpOnly(true).secure(true).sameSite(properties.cookieSameSite()).path("/api/auth").maxAge(properties.refreshTokenSeconds()).build().toString());return ResponseEntity.ok(issued.body());}
}
