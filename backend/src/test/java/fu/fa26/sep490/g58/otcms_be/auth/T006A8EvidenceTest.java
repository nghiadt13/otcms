package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.controller.PatientLoginController;
import fu.fa26.sep490.g58.otcms_be.auth.controller.SessionSecurityController;
import fu.fa26.sep490.g58.otcms_be.auth.controller.StaffLoginController;
import fu.fa26.sep490.g58.otcms_be.auth.security.StaffNetworkPolicy;
import fu.fa26.sep490.g58.otcms_be.auth.security.CsrfTokenService;
import fu.fa26.sep490.g58.otcms_be.auth.security.JwtAuthenticationFilter;
import fu.fa26.sep490.g58.otcms_be.auth.service.AuthSessionService;
import fu.fa26.sep490.g58.otcms_be.auth.service.CredentialLoginService;
import fu.fa26.sep490.g58.otcms_be.config.SecurityConfig;
import fu.fa26.sep490.g58.otcms_be.config.SessionSecurityProperties;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@LocalPostgresTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.auth.session-security.jwt-secret=t006-a8-test-secret-32-bytes",
        "app.auth.session-security.cookie-same-site=Strict",
        "app.auth.session-security.cookie-secure=true",
        "app.auth.session-security.patient-origins=https://patient.example.test",
        "app.auth.session-security.staff-origins=https://staff.example.test"
})
class T006A8EvidenceTest {
    @Autowired MockMvc mvc;
    @MockitoBean AuthSessionService sessions;
    @MockitoBean CredentialLoginService login;
    @BeforeEach
    void csrfFixture() { }

    @Test
    void realSecurityFilterChainPermitsAuthAndRejectsProtectedRequest() throws Exception {
        mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk());
        mvc.perform(get("/api/private-resource")).andExpect(result -> {
            int status = result.getResponse().getStatus();
            org.junit.jupiter.api.Assertions.assertTrue(status == 401 || status == 403);
        });
    }

    @Test
    void csrfAndCorsAndStaffNetworkAreEnforcedThroughMvc() throws Exception {
        mvc.perform(post("/api/auth/refresh").cookie(new jakarta.servlet.http.Cookie("refresh_token", "r")))
                .andExpect(status().isForbidden());
        mvc.perform(options("/api/auth/refresh").header(HttpHeaders.ORIGIN, "https://evil.example.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        when(sessions.accountTypeForRefresh("staff-refresh"))
                .thenReturn(Optional.of(fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType.RECEPTIONIST));
        mvc.perform(post("/api/auth/logout").cookie(new jakarta.servlet.http.Cookie("refresh_token", "staff-refresh"),
                        new jakarta.servlet.http.Cookie("XSRF-TOKEN", "csrf"))
                        .header("X-CSRF-TOKEN", "csrf").header("X-Forwarded-For", "203.0.113.10"))
                .andExpect(status().isForbidden());
    }

    @Test
    void refreshWithCsrfCookieAndFrontendHeaderReachesSessionService() throws Exception {
        UUID accountId = UUID.randomUUID();
        when(sessions.rotate("valid-refresh")).thenReturn(new AuthSessionService.IssuedSession(
                new fu.fa26.sep490.g58.otcms_be.auth.model.dto.response.SessionTokenResponse(
                        "access", "Bearer", 900, accountId, "ADMIN"), "rotated-refresh"));

        mvc.perform(post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", "valid-refresh"),
                                new jakarta.servlet.http.Cookie("XSRF-TOKEN", "csrf-token"))
                        .header("X-CSRF-TOKEN", "csrf-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountType").value("ADMIN"));
        verify(sessions).rotate("valid-refresh");
    }

    @Test
    void refreshFamilyIsolationUsesIndependentTokens() throws Exception {
        when(sessions.accountTypeForRefresh("family-a")).thenReturn(Optional.of(fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType.PATIENT));
        when(sessions.accountTypeForRefresh("family-b")).thenReturn(Optional.of(fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType.PATIENT));
        mvc.perform(post("/api/auth/logout").cookie(new jakarta.servlet.http.Cookie("refresh_token", "family-a"),
                        new jakarta.servlet.http.Cookie("XSRF-TOKEN", "csrf")).header("X-CSRF-TOKEN", "csrf").with(csrf()))
                .andExpect(status().isForbidden());
        verify(sessions, never()).logout("family-b");
    }
}
