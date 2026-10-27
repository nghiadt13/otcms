package fu.fa26.sep490.g58.otcms_be.auth.security;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.auth.service.AccountAccessService;
import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse;
import tools.jackson.databind.json.JsonMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenService tokens;
    private final AccountAccessService access;
    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();

    public JwtAuthenticationFilter(JwtTokenService tokens, AccountAccessService access) {
        this.tokens = tokens;
        this.access = access;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String value = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (value != null && value.startsWith("Bearer ")) {
            try {
                var claims = tokens.validate(value.substring(7));
                var principal = new AuthenticatedAccount(claims.accountId(),
                        AccountRole.valueOf(claims.accountType().name()), claims.sessionId());
                AccountAccessService.AccessState state = access.evaluate(principal);
                if (state == null || state == AccountAccessService.AccessState.INVALID_SESSION) {
                    SecurityContextHolder.clearContext();
                    reject(response, 401, "AUTHENTICATION_FAILED");
                    return;
                }
                if (state == AccountAccessService.AccessState.PASSWORD_CHANGE_REQUIRED && !passwordChangeRoute(request)) {
                    SecurityContextHolder.clearContext();
                    reject(response, 403, "PASSWORD_CHANGE_REQUIRED");
                    return;
                }
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + claims.accountType().name()))));
            } catch (JwtTokenService.JwtValidationException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }

    private boolean passwordChangeRoute(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path.isEmpty()) path = request.getRequestURI().substring(request.getContextPath().length());
        return ("POST".equals(request.getMethod())
                && ("/api/auth/change-password".equals(path) || "/api/auth/logout".equals(path)))
                || ("GET".equals(request.getMethod()) && "/api/auth/csrf".equals(path));
    }

    private void reject(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(json.writeValueAsString(ErrorResponse.of(code,
                "Authentication action required", java.util.UUID.randomUUID().toString())));
    }
}
