package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.controller.StaffLoginController;
import fu.fa26.sep490.g58.otcms_be.auth.exception.AuthExceptionHandler;
import fu.fa26.sep490.g58.otcms_be.auth.exception.CredentialLoginException;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.response.CredentialLoginResponse;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.response.SessionTokenResponse;
import fu.fa26.sep490.g58.otcms_be.auth.security.StaffNetworkPolicy;
import fu.fa26.sep490.g58.otcms_be.auth.service.AuthSessionService;
import fu.fa26.sep490.g58.otcms_be.auth.service.CredentialLoginService;
import fu.fa26.sep490.g58.otcms_be.auth.service.impl.StaffAuthenticationServiceImpl;
import fu.fa26.sep490.g58.otcms_be.config.SessionSecurityProperties;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UC08StaffAuthenticationTest {
    private final CredentialLoginService credentials = mock(CredentialLoginService.class);
    private final AuthSessionService sessions = mock(AuthSessionService.class);
    private final StaffAuthenticationServiceImpl authentication = new StaffAuthenticationServiceImpl(credentials, sessions);

    @Test
    void legacyEmailAndUsernameLoginsShareSessionOrchestrationAfterCredentialVerification() {
        UUID id = UUID.randomUUID();
        when(credentials.loginStaff(anyString(), anyString(), anyString()))
                .thenReturn(new CredentialLoginResponse(id, "ADMIN"));
        when(credentials.loginAdministrator(anyString(), anyString(), anyString()))
                .thenReturn(new CredentialLoginResponse(id, "ADMIN"));
        var issued = issued(id);
        when(sessions.create(id, "127.0.0.1", "test")).thenReturn(issued);
        assertSame(issued, authentication.login("admin@example.test", "issued-password", "127.0.0.1", "test", false));
        assertSame(issued, authentication.login("admin", "issued-password", "127.0.0.1", "test", true));
        var order = inOrder(credentials, sessions);
        order.verify(credentials).loginStaff("admin@example.test", "issued-password", "127.0.0.1");
        order.verify(sessions).create(id, "127.0.0.1", "test");
        order.verify(credentials).loginAdministrator("admin", "issued-password", "127.0.0.1");
        order.verify(sessions).create(id, "127.0.0.1", "test");
    }

    @Test
    void rejectedPasswordNeverCreatesSession() {
        when(credentials.loginAdministrator(anyString(), anyString(), anyString()))
                .thenThrow(new CredentialLoginException("AUTHENTICATION_FAILED"));
        assertThrows(CredentialLoginException.class,
                () -> authentication.login("admin", "old-password", "127.0.0.1", "test", true));
        verifyNoInteractions(sessions);
    }

    @Test
    void administratorLoginRetainsCookieFlagAndDoesNotReturnIssuedPassword() throws Exception {
        UUID id = UUID.randomUUID();
        when(credentials.loginAdministrator(eq("admin"), anyString(), anyString()))
                .thenReturn(new CredentialLoginResponse(id, "ADMIN"));
        when(sessions.create(eq(id), any(), any())).thenReturn(issued(id));
        var mvc = MockMvcBuilders.standaloneSetup(controller(List.of("127.0.0.0/8")))
                .setControllerAdvice(new AuthExceptionHandler()).build();
        mvc.perform(post("/api/auth/staff/administrator/login").contentType("application/json")
                        .content("{\"username\":\"admin\",\"password\":\"issued-password-123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mustChangePassword").value(true))
                .andExpect(jsonPath("$.temporaryPassword").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("HttpOnly"),
                        org.hamcrest.Matchers.containsString("Secure"),
                        org.hamcrest.Matchers.containsString("SameSite=Strict"))));
    }

    @Test
    void staffNetworkAdmissionStillRejectsUntrustedClientsBeforeCredentials() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(controller(List.of("10.0.0.0/8")))
                .setControllerAdvice(new AuthExceptionHandler()).build();
        for (String endpoint : new String[]{"/login", "/administrator/login"}) {
            String body = endpoint.equals("/login")
                    ? "{\"email\":\"admin@example.test\",\"password\":\"issued-password-123\"}"
                    : "{\"username\":\"admin\",\"password\":\"issued-password-123\"}";
            mvc.perform(post("/api/auth/staff" + endpoint).contentType("application/json").content(body))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(credentials, sessions);
    }

    private StaffLoginController controller(List<String> allowed) {
        var network = new StaffNetworkPolicy();
        network.setAllowedCidrs(allowed);
        var properties = new SessionSecurityProperties("test-session-secret-32-characters-minimum", 900, 604800,
                "Strict", true, List.of(), List.of());
        return new StaffLoginController(authentication, network, properties);
    }

    private AuthSessionService.IssuedSession issued(UUID id) {
        return new AuthSessionService.IssuedSession(
                new SessionTokenResponse("access", "Bearer", 900, id, "ADMIN", true), "test-refresh");
    }
}
