package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.controller.SessionSecurityController;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.response.SessionTokenResponse;
import fu.fa26.sep490.g58.otcms_be.auth.security.CsrfTokenService;
import fu.fa26.sep490.g58.otcms_be.auth.security.JwtAuthenticationFilter;
import fu.fa26.sep490.g58.otcms_be.auth.security.JwtTokenService;
import fu.fa26.sep490.g58.otcms_be.auth.security.StaffNetworkPolicy;
import fu.fa26.sep490.g58.otcms_be.auth.service.AuthSessionService;
import fu.fa26.sep490.g58.otcms_be.config.SecurityConfig;
import fu.fa26.sep490.g58.otcms_be.config.SessionSecurityProperties;
import fu.fa26.sep490.g58.otcms_be.appointment.config.AppointmentWebConfiguration;
import fu.fa26.sep490.g58.otcms_be.appointment.web.PublicRequestRateLimitInterceptor;
import fu.fa26.sep490.g58.otcms_be.common.web.ProductionReadinessGateInterceptor;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SessionSecurityController.class)
@Import({SecurityConfig.class, SessionCsrfWebMvcTest.Fixture.class})
@TestPropertySource(properties = {
        "app.auth.session-security.jwt-secret=session-csrf-web-mvc-test-secret-32-bytes",
        "app.auth.session-security.cookie-same-site=Strict",
        "app.auth.session-security.cookie-secure=true",
        "app.auth.session-security.patient-origins=https://patient.example.test",
        "app.auth.session-security.staff-origins=http://localhost:3000"
})
class SessionCsrfWebMvcTest {
    @Autowired MockMvc mvc;
    @MockitoBean AuthSessionService sessions;
    @MockitoBean JwtTokenService tokens;
    @MockitoBean fu.fa26.sep490.g58.otcms_be.auth.service.AccountAccessService accountAccess;
    @MockitoBean AppointmentWebConfiguration appointmentWebConfiguration;
    @MockitoBean PublicRequestRateLimitInterceptor publicRequestRateLimitInterceptor;
    @MockitoBean ProductionReadinessGateInterceptor productionReadinessGateInterceptor;

    @Test
    void csrfEndpointTokenAllowsRefreshThroughSecurityFilter() throws Exception {
        String token = mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()
                .replaceAll(".*\"csrfToken\":\"([^\"]+)\".*", "$1");
        when(sessions.rotate("refresh-token")).thenReturn(new AuthSessionService.IssuedSession(
                new SessionTokenResponse("access-token", "Bearer", 900, UUID.randomUUID(), "ADMIN"),
                "rotated-token"));

        mvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("XSRF-TOKEN", token), new Cookie("refresh_token", "refresh-token"))
                        .header("X-CSRF-TOKEN", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountType").value("ADMIN"));
    }

    @Test
    void refreshWithoutMatchingCsrfHeaderDoesNotReachSessionService() throws Exception {
        mvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("XSRF-TOKEN", "cookie-token"),
                                new Cookie("refresh_token", "refresh-token"))
                        .header("X-CSRF-TOKEN", "different-token"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(sessions);
    }

    @TestConfiguration
    @EnableWebSecurity
    @EnableConfigurationProperties(SessionSecurityProperties.class)
    static class Fixture {
        @Bean CsrfTokenService csrfTokenService() { return new CsrfTokenService(); }
        @Bean StaffNetworkPolicy staffNetworkPolicy() { return new StaffNetworkPolicy(); }
    }
}
