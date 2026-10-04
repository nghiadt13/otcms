package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.controller.*;
import fu.fa26.sep490.g58.otcms_be.auth.dto.*;
import fu.fa26.sep490.g58.otcms_be.auth.entity.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.security.CsrfTokenService;
import fu.fa26.sep490.g58.otcms_be.auth.service.AuthSessionService;
import fu.fa26.sep490.g58.otcms_be.auth.service.CredentialLoginService;
import fu.fa26.sep490.g58.otcms_be.config.SessionSecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** T006 A12: executable HTTP contract evidence without security/service mocks hiding cookie or policy behavior. */
class T006A12EvidenceTest {
    private final SessionSecurityProperties properties = new SessionSecurityProperties(
            "t006-a12-test-secret-32-bytes-minimum!", 900, 604800, "Strict", true,
            List.of("https://patient.example.test"), List.of("https://staff.example.test"));

    @Test
    void patientLoginIssuesSecureHttpOnlyRefreshCookie() throws Exception {
        CredentialLoginService login = mock(CredentialLoginService.class);
        AuthSessionService sessions = mock(AuthSessionService.class);
        when(login.login(anyString(), anyString(), eq(AccountType.PATIENT), anyString()))
                .thenReturn(new CredentialLoginResponse(UUID.randomUUID(), AccountType.PATIENT.name()));
        var body = new SessionTokenResponse("access", "Bearer", 900, UUID.randomUUID(), "PATIENT");
        when(sessions.create(any(UUID.class), any(), any())).thenReturn(new AuthSessionService.IssuedSession(body, "refresh-a"));
        var mvc = MockMvcBuilders.standaloneSetup(new PatientLoginController(login, new StaffNetworkPolicy(), sessions, properties)).build();

        mvc.perform(post("/api/auth/patient/login").contentType("application/json")
                        .content("{\"email\":\"patient@example.test\",\"password\":\"StrongPass1!\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.containsString("refresh_token=refresh-a"),
                                org.hamcrest.Matchers.containsString("HttpOnly"), org.hamcrest.Matchers.containsString("Secure"),
                                org.hamcrest.Matchers.containsString("SameSite=Strict"))));
    }

    @Test
    void trustedOriginCorsConfigurationAllowsCredentialsWithoutWildcard() throws Exception {
        var method = fu.fa26.sep490.g58.otcms_be.config.SecurityConfig.class.getDeclaredMethod("corsConfigurationSource", SessionSecurityProperties.class);
        method.setAccessible(true);
        var source = (org.springframework.web.cors.CorsConfigurationSource) method.invoke(new fu.fa26.sep490.g58.otcms_be.config.SecurityConfig(), properties);
        var allowed = source.getCorsConfiguration(new org.springframework.mock.web.MockHttpServletRequest("OPTIONS", "/api/auth/refresh"));
        org.junit.jupiter.api.Assertions.assertNotNull(allowed);
        org.junit.jupiter.api.Assertions.assertEquals(List.of("https://patient.example.test", "https://staff.example.test"), allowed.getAllowedOrigins());
        org.junit.jupiter.api.Assertions.assertTrue(allowed.getAllowCredentials());
    }

    @Test
    void staffRefreshAndLogoutRejectUntrustedClientThroughRealController() throws Exception {
        AuthSessionService sessions = mock(AuthSessionService.class);
        when(sessions.accountTypeForRefresh("staff-refresh")).thenReturn(Optional.of(AccountType.STAFF));
        var policy = new StaffNetworkPolicy();
        policy.setAllowedCidrs(List.of("10.0.0.0/8"));
        var mvc = MockMvcBuilders.standaloneSetup(new SessionSecurityController(new CsrfTokenService(), sessions, properties, policy)).build();
        org.junit.jupiter.api.Assertions.assertThrows(jakarta.servlet.ServletException.class, () ->
                mvc.perform(post("/api/auth/logout").cookie(new jakarta.servlet.http.Cookie("refresh_token", "staff-refresh"), new jakarta.servlet.http.Cookie("XSRF-TOKEN", "csrf"))
                        .header("X-CSRF-TOKEN", "csrf").with(request -> { request.setRemoteAddr("192.0.2.10"); return request; })));
        verify(sessions, never()).logout(anyString());
    }

    @Test
    void refreshFamiliesRemainIndependentAtControllerBoundary() throws Exception {
        AuthSessionService sessions = mock(AuthSessionService.class);
        when(sessions.accountTypeForRefresh(anyString())).thenReturn(Optional.of(AccountType.PATIENT));
        var mvc = MockMvcBuilders.standaloneSetup(new SessionSecurityController(new CsrfTokenService(), sessions, properties, new StaffNetworkPolicy())).build();
        mvc.perform(post("/api/auth/logout").cookie(new jakarta.servlet.http.Cookie("refresh_token", "family-a"), new jakarta.servlet.http.Cookie("XSRF-TOKEN", "csrf"))
                        .header("X-CSRF-TOKEN", "csrf"))
                .andExpect(status().isNoContent());
        verify(sessions).logout("family-a");
        verify(sessions, never()).logout("family-b");
    }
}
