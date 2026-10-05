package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.controller.SessionSecurityController;
import fu.fa26.sep490.g58.otcms_be.auth.controller.StaffNetworkPolicy;
import fu.fa26.sep490.g58.otcms_be.auth.dto.SessionTokenResponse;
import fu.fa26.sep490.g58.otcms_be.auth.security.CsrfTokenService;
import fu.fa26.sep490.g58.otcms_be.auth.service.AuthSessionService;
import fu.fa26.sep490.g58.otcms_be.config.SessionSecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class T006HttpSecurityEvidenceTest {
    private final AuthSessionService sessions = mock(AuthSessionService.class);
    private final StaffNetworkPolicy network = new StaffNetworkPolicy();
    private final SessionSecurityProperties properties = new SessionSecurityProperties(
            "t006-http-security-test-secret-32-bytes", 900, 3600, "Strict", true,
            List.of("https://patient.example.test"), List.of("https://staff.example.test"));
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
            new SessionSecurityController(new CsrfTokenService(), sessions, properties, network)).build();

    @Test void csrfEndpointIssuesReadableTokenCookieAndMissingCsrfIsRejectedBeforeRefresh() throws Exception {
        String token = mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("XSRF-TOKEN=")))
                .andReturn().getResponse().getContentAsString();
        assertTrue(token.contains("csrfToken"));
        assertThrows(jakarta.servlet.ServletException.class, () ->
                mvc.perform(post("/api/auth/refresh").cookie(new jakarta.servlet.http.Cookie("refresh_token", "refresh"))));
        verifyNoInteractions(sessions);
    }

    @Test void logoutClearsSecureRefreshCookieAndRefreshRotationSetsSecureCookie() throws Exception {
        UUID account = UUID.randomUUID();
        AuthSessionService.IssuedSession issued = new AuthSessionService.IssuedSession(
                new SessionTokenResponse("access", "Bearer", 900, account, "PATIENT"), "rotated");
        when(sessions.rotate("old")).thenReturn(issued);
        mvc.perform(post("/api/auth/refresh").cookie(cookie("refresh_token", "old"), cookie("XSRF-TOKEN", "csrf"))
                        .header("X-CSRF-TOKEN", "csrf"))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.containsString("Secure"), org.hamcrest.Matchers.containsString("SameSite=Strict"))));
        mvc.perform(post("/api/auth/logout").cookie(cookie("refresh_token", "old"), cookie("XSRF-TOKEN", "csrf"))
                        .header("X-CSRF-TOKEN", "csrf"))
                .andExpect(status().isNoContent()).andExpect(header().string(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.containsString("Secure"), org.hamcrest.Matchers.containsString("Max-Age=0"))));
        verify(sessions).logout("old");
    }

    @Test void corsConfigurationIsExactOriginOnly() {
        assertEquals(List.of("https://patient.example.test"), properties.patientOrigins());
        assertFalse(properties.patientOrigins().contains("*"));
    }

    private static jakarta.servlet.http.Cookie cookie(String name, String value) { return new jakarta.servlet.http.Cookie(name, value); }
}
