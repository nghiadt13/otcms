package fu.fa26.sep490.g58.otcms_be.config;

import fu.fa26.sep490.g58.otcms_be.auth.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.*;
import java.util.*;

@Configuration
public class SecurityConfig {
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http,JwtAuthenticationFilter jwt)throws Exception{http.cors(c->{}).csrf(csrf->csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()).ignoringRequestMatchers("/api/auth/csrf","/api/auth/patient/login","/api/auth/staff/login","/api/auth/patient/register","/api/auth/patient/verify-email","/api/auth/patient/resend-otp")).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)).authorizeHttpRequests(a->a.requestMatchers("/api/auth/**").permitAll().anyRequest().authenticated()).addFilterBefore(jwt,UsernamePasswordAuthenticationFilter.class);return http.build();}
    @Bean CorsConfigurationSource corsConfigurationSource(SessionSecurityProperties properties){CorsConfiguration c=new CorsConfiguration();List<String> origins=new ArrayList<>();origins.addAll(properties.patientOrigins());origins.addAll(properties.staffOrigins());c.setAllowedOrigins(origins);c.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));c.setAllowedHeaders(List.of("Content-Type","X-CSRF-TOKEN","Authorization"));c.setAllowCredentials(true);UrlBasedCorsConfigurationSource source=new UrlBasedCorsConfigurationSource();source.registerCorsConfiguration("/**",c);return source;}
}
