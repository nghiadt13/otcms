package fu.fa26.sep490.g58.otcms_be.auth.controller;

import fu.fa26.sep490.g58.otcms_be.auth.dto.SessionTokenResponse;
import fu.fa26.sep490.g58.otcms_be.auth.security.CsrfTokenService;
import fu.fa26.sep490.g58.otcms_be.auth.service.AuthSessionService;
import fu.fa26.sep490.g58.otcms_be.auth.service.SessionException;
import fu.fa26.sep490.g58.otcms_be.auth.entity.AccountType;
import jakarta.servlet.http.HttpServletRequest;
import fu.fa26.sep490.g58.otcms_be.config.SessionSecurityProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/auth")
public class SessionSecurityController {
    private final CsrfTokenService csrf; private final AuthSessionService sessions; private final SessionSecurityProperties properties; private final StaffNetworkPolicy network;
    public SessionSecurityController(CsrfTokenService csrf,AuthSessionService sessions,SessionSecurityProperties properties,StaffNetworkPolicy network){this.csrf=csrf;this.sessions=sessions;this.properties=properties;this.network=network;}
    @GetMapping("/csrf") public ResponseEntity<CsrfResponse> csrf(HttpServletResponse response){String token=csrf.issue();response.addHeader(HttpHeaders.SET_COOKIE,ResponseCookie.from("XSRF-TOKEN",token).httpOnly(false).secure(properties.cookieSecure()).sameSite(properties.cookieSameSite()).path("/api/auth").maxAge(properties.refreshTokenSeconds()).build().toString());return ResponseEntity.ok(new CsrfResponse(token));}
    @PostMapping("/refresh") public ResponseEntity<SessionTokenResponse> refresh(@CookieValue(name="refresh_token",required=false)String refresh,@CookieValue(name="XSRF-TOKEN",required=false)String csrfCookie,@RequestHeader(name="X-CSRF-TOKEN",required=false)String csrfHeader,HttpServletRequest request,HttpServletResponse response){requireCsrf(csrfCookie,csrfHeader);requireStaffNetwork(refresh,request);try{var issued=sessions.rotate(refresh);response.addHeader(HttpHeaders.SET_COOKIE,refreshCookie(issued.refreshToken()).toString());return ResponseEntity.ok(issued.body());}catch(SessionException ex){response.addHeader(HttpHeaders.SET_COOKIE,clearRefresh().toString());throw ex;}}
    @PostMapping("/logout") public ResponseEntity<Void> logout(@CookieValue(name="refresh_token",required=false)String refresh,@CookieValue(name="XSRF-TOKEN",required=false)String csrfCookie,@RequestHeader(name="X-CSRF-TOKEN",required=false)String csrfHeader,HttpServletRequest request,HttpServletResponse response){requireCsrf(csrfCookie,csrfHeader);requireStaffNetwork(refresh,request);sessions.logout(refresh);response.addHeader(HttpHeaders.SET_COOKIE,clearRefresh().toString());return ResponseEntity.noContent().build();}
    private void requireCsrf(String cookie,String header){if(!csrf.valid(cookie,header))throw new SessionException("CSRF_VALIDATION_FAILED");}
    private ResponseCookie refreshCookie(String value){return ResponseCookie.from("refresh_token",value).httpOnly(true).secure(true).sameSite(properties.cookieSameSite()).path("/api/auth").maxAge(properties.refreshTokenSeconds()).build();}
    private ResponseCookie clearRefresh(){return ResponseCookie.from("refresh_token","").httpOnly(true).secure(true).sameSite(properties.cookieSameSite()).path("/api/auth").maxAge(0).build();}
    private void requireStaffNetwork(String refresh,HttpServletRequest request){if(sessions.accountTypeForRefresh(refresh).orElse(null)==AccountType.STAFF){String ip=network.clientAddress(request.getRemoteAddr(),request.getHeader("Forwarded"),request.getHeader("X-Forwarded-For"));if(!network.allows(ip))throw new StaffNetworkAccessException();}}
    public record CsrfResponse(String csrfToken){}
}
