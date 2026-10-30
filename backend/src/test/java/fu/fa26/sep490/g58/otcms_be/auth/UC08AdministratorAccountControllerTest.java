package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.appointment.config.AppointmentWebConfiguration;
import fu.fa26.sep490.g58.otcms_be.appointment.web.PublicRequestRateLimitInterceptor;
import fu.fa26.sep490.g58.otcms_be.auth.controller.OwnerAdministratorController;
import fu.fa26.sep490.g58.otcms_be.auth.exception.AdministratorAccountException;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.response.AdministratorAccountResponse;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.response.ReissueAdministratorPasswordResponse;
import fu.fa26.sep490.g58.otcms_be.auth.security.JwtTokenService;
import fu.fa26.sep490.g58.otcms_be.auth.service.AccountAccessService;
import fu.fa26.sep490.g58.otcms_be.auth.service.OwnerAdministratorService;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.web.ProductionReadinessGateInterceptor;
import fu.fa26.sep490.g58.otcms_be.config.SecurityConfig;
import fu.fa26.sep490.g58.otcms_be.config.SessionSecurityProperties;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OwnerAdministratorController.class)
@Import({SecurityConfig.class, UC08AdministratorAccountControllerTest.Fixture.class})
class UC08AdministratorAccountControllerTest {
    private final UUID id = UUID.randomUUID();
    private final String base = "/api/v1/owner/administrator-accounts/" + id;
    @Autowired private MockMvc mvc;
    @MockitoBean private OwnerAdministratorService accounts;
    @MockitoBean private JwtTokenService tokens;
    @MockitoBean private AccountAccessService access;
    @MockitoBean private AppointmentWebConfiguration appointmentWebConfiguration;
    @MockitoBean private PublicRequestRateLimitInterceptor publicRequestRateLimitInterceptor;
    @MockitoBean private ProductionReadinessGateInterceptor productionReadinessGateInterceptor;

    @Test
    void ownerActivatesAndDeactivatesWithSafeResponse() throws Exception {
        when(accounts.activate(any(), eq(id))).thenReturn(response("ACTIVE"));
        when(accounts.deactivate(any(), eq(id))).thenReturn(response("DISABLED"));
        for (String action : new String[]{"activate", "deactivate"}) {
            mvc.perform(post(base + "/" + action).with(actor(AccountRole.OWNER)).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accountType").value("ADMIN"))
                    .andExpect(jsonPath("$.status").value(action.equals("activate") ? "ACTIVE" : "DISABLED"))
                    .andExpect(jsonPath("$.passwordHash").doesNotExist())
                    .andExpect(jsonPath("$.temporaryPassword").doesNotExist());
        }
    }

    @Test
    void ownerReceivesIssuedPasswordOnceAndResponseCannotBeCached() throws Exception {
        String issued = new fu.fa26.sep490.g58.otcms_be.auth.service.impl.TemporaryPasswordGenerator().generate();
        when(accounts.reissuePassword(any(), eq(id))).thenReturn(new ReissueAdministratorPasswordResponse(id, issued));
        mvc.perform(post(base + "/password-reissues").with(actor(AccountRole.OWNER)).with(csrf()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.temporaryPassword").value(issued))
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void anonymousAndAllNonOwnersDeniedOnEveryAction() throws Exception {
        for (String action : new String[]{"activate", "deactivate", "password-reissues"}) {
            mvc.perform(post(base + "/" + action).with(csrf())).andExpect(status().isUnauthorized());
            for (AccountRole role : AccountRole.values()) {
                if (role != AccountRole.OWNER) {
                    mvc.perform(post(base + "/" + action).with(actor(role)).with(csrf()))
                            .andExpect(status().isForbidden());
                }
            }
        }
        verifyNoInteractions(accounts);
    }

    @Test
    void wrongTargetIs404AndNoCredentialsAreReturned() throws Exception {
        when(accounts.reissuePassword(any(), eq(id)))
                .thenThrow(new AdministratorAccountException("ADMINISTRATOR_ACCOUNT_NOT_FOUND"));
        mvc.perform(post(base + "/password-reissues").with(actor(AccountRole.OWNER)).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.temporaryPassword").doesNotExist());
    }

    @Test
    void csrfAndInvalidIdAreRejectedAndDeleteIsUnavailable() throws Exception {
        mvc.perform(post(base + "/activate").with(actor(AccountRole.OWNER))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/owner/administrator-accounts/invalid/activate")
                        .with(actor(AccountRole.OWNER)).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(delete(base).with(actor(AccountRole.OWNER)).with(csrf())).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(accounts);
    }

    private AdministratorAccountResponse response(String status) {
        return new AdministratorAccountResponse(id, "admin", null, "ADMIN", status, Instant.EPOCH, Instant.EPOCH);
    }

    private RequestPostProcessor actor(AccountRole role) {
        var principal = new AuthenticatedAccount(UUID.randomUUID(), role, UUID.randomUUID());
        return authentication(new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    @TestConfiguration
    @EnableWebSecurity
    @EnableConfigurationProperties(SessionSecurityProperties.class)
    static class Fixture { }
}
