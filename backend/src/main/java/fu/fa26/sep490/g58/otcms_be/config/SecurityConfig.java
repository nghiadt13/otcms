package fu.fa26.sep490.g58.otcms_be.config;

import fu.fa26.sep490.g58.otcms_be.auth.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.http.HttpStatus;
import org.springframework.web.cors.*;
import java.util.*;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http,JwtAuthenticationFilter jwt)throws Exception{CookieCsrfTokenRepository csrfTokens=CookieCsrfTokenRepository.withHttpOnlyFalse();csrfTokens.setHeaderName("X-CSRF-TOKEN");http.cors(c->{}).csrf(csrf->csrf.csrfTokenRepository(csrfTokens).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()).ignoringRequestMatchers("/api/auth/csrf","/api/auth/patient/login","/api/auth/staff/login","/api/auth/patient/register","/api/auth/patient/verify-email","/api/auth/patient/resend-otp")).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)).exceptionHandling(e->e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))).authorizeHttpRequests(a->a.requestMatchers("/api/auth/**","/swagger-ui/**","/swagger-ui.html","/v3/api-docs","/v3/api-docs/**").permitAll().requestMatchers(HttpMethod.GET,"/api/v1/clinic-services","/api/v1/doctors","/api/v1/doctors/**","/api/v1/appointments/availability").permitAll().requestMatchers(HttpMethod.HEAD,"/api/v1/clinic-services","/api/v1/appointments/availability").permitAll().anyRequest().authenticated()).addFilterBefore(jwt,UsernamePasswordAuthenticationFilter.class);return http.build();}
    @Bean CorsConfigurationSource corsConfigurationSource(SessionSecurityProperties properties){CorsConfiguration c=new CorsConfiguration();List<String> origins=new ArrayList<>();origins.addAll(properties.patientOrigins());origins.addAll(properties.staffOrigins());c.setAllowedOrigins(origins);c.setAllowedMethods(List.of("GET","HEAD","POST","PUT","DELETE","OPTIONS"));c.setAllowedHeaders(List.of("Content-Type","X-CSRF-TOKEN","Authorization","Idempotency-Key","X-Correlation-ID"));c.setExposedHeaders(List.of("X-Correlation-ID"));c.setAllowCredentials(true);UrlBasedCorsConfigurationSource source=new UrlBasedCorsConfigurationSource();source.registerCorsConfiguration("/**",c);return source;}
}
