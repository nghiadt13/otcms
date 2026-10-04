package fu.fa26.sep490.g58.otcms_be.auth.security;

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
    public JwtAuthenticationFilter(JwtTokenService tokens){this.tokens=tokens;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        String value=request.getHeader(HttpHeaders.AUTHORIZATION);
        if(value!=null&&value.startsWith("Bearer "))try{var c=tokens.validate(value.substring(7));var p=new AuthenticatedAccount(c.accountId(),c.accountType(),c.sessionId());SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,List.of(new SimpleGrantedAuthority("ROLE_"+c.accountType().name()))));}catch(JwtTokenService.JwtValidationException ignored){SecurityContextHolder.clearContext();}
        chain.doFilter(request,response);
    }
    public record AuthenticatedAccount(java.util.UUID accountId,fu.fa26.sep490.g58.otcms_be.auth.entity.AccountType accountType,java.util.UUID sessionId){}
}
