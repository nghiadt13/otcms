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

@RestController @RequestMapping("/api/auth/patient")
public class PatientLoginController {
    private final CredentialLoginService login; private final StaffNetworkPolicy network; private final AuthSessionService sessions; private final SessionSecurityProperties properties;
    public PatientLoginController(CredentialLoginService login,StaffNetworkPolicy network,AuthSessionService sessions,SessionSecurityProperties properties){this.login=login;this.network=network;this.sessions=sessions;this.properties=properties;}
    @PostMapping("/login") public ResponseEntity<SessionTokenResponse> login(@Valid @RequestBody CredentialLoginRequest request,HttpServletRequest http,HttpServletResponse response){String ip=network.clientAddress(http.getRemoteAddr(),http.getHeader("Forwarded"),http.getHeader("X-Forwarded-For"));CredentialLoginResponse authenticated=login.login(request.email(),request.password(),AccountType.PATIENT,ip);var issued=sessions.create(authenticated.accountId(),ip,http.getHeader("User-Agent"));response.addHeader(HttpHeaders.SET_COOKIE,cookie(issued.refreshToken()).toString());return ResponseEntity.ok(issued.body());}
    private ResponseCookie cookie(String value){return ResponseCookie.from("refresh_token",value).httpOnly(true).secure(true).sameSite(properties.cookieSameSite()).path("/api/auth").maxAge(properties.refreshTokenSeconds()).build();}
}
