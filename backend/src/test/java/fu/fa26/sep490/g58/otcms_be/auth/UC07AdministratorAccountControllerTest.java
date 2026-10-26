package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.appointment.config.AppointmentWebConfiguration;
import fu.fa26.sep490.g58.otcms_be.appointment.web.PublicRequestRateLimitInterceptor;
import fu.fa26.sep490.g58.otcms_be.auth.controller.OwnerAdministratorController;
import fu.fa26.sep490.g58.otcms_be.auth.exception.AdministratorAccountException;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.response.CreateAdministratorAccountResponse;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.response.SessionTokenResponse;
import fu.fa26.sep490.g58.otcms_be.auth.security.JwtTokenService;
import fu.fa26.sep490.g58.otcms_be.auth.service.AccountAccessService;
import fu.fa26.sep490.g58.otcms_be.auth.service.OwnerAdministratorService;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.web.ProductionReadinessGateInterceptor;
import fu.fa26.sep490.g58.otcms_be.config.SecurityConfig;
import fu.fa26.sep490.g58.otcms_be.config.SessionSecurityProperties;
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
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OwnerAdministratorController.class)
@Import({SecurityConfig.class, UC07AdministratorAccountControllerTest.Fixture.class})
class UC07AdministratorAccountControllerTest {
    private static final String ENDPOINT = "/api/v1/owner/administrator-accounts";
    @Autowired private MockMvc mvc;
    @MockitoBean private OwnerAdministratorService accounts;
    @MockitoBean private JwtTokenService tokens;
    @MockitoBean private AccountAccessService access;
    @MockitoBean private AppointmentWebConfiguration appointmentWebConfiguration;
    @MockitoBean private PublicRequestRateLimitInterceptor publicRequestRateLimitInterceptor;
    @MockitoBean private ProductionReadinessGateInterceptor productionReadinessGateInterceptor;

    @Test
    void ownerReceivesPasswordOnlyInNonCacheableCreateResponse() throws Exception {
        String issued = new fu.fa26.sep490.g58.otcms_be.auth.service.impl.TemporaryPasswordGenerator().generate();
        when(accounts.create(any(), eq("admin")))
                .thenReturn(new CreateAdministratorAccountResponse(UUID.randomUUID(), "admin", issued));
        mvc.perform(post(ENDPOINT).with(actor(AccountRole.OWNER)).with(csrf())
                        .contentType("application/json").content("{\"username\":\"admin\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.accountType").value("ADMIN"))
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andExpect(jsonPath("$.temporaryPassword").value(issued))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        when(accounts.list(any(), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(org.springframework.data.domain.Page.empty());
        mvc.perform(get(ENDPOINT).with(actor(AccountRole.OWNER)))
                .andExpect(status().isOk())
                .andExpect(result -> assertFalse(result.getResponse().getContentAsString().contains(issued)));
        String session = JsonMapper.builder().build().writeValueAsString(
                new SessionTokenResponse("access", "Bearer", 900, UUID.randomUUID(), "ADMIN", true));
        assertFalse(session.contains("temporaryPassword"));
        assertFalse(session.contains("passwordHash"));
        assertFalse(session.contains(issued));
    }

    @Test
    void anonymousAndNonOwnerCannotCallCreate() throws Exception {
        mvc.perform(post(ENDPOINT).with(csrf()).contentType("application/json")
                .content("{\"username\":\"admin\"}")).andExpect(status().isUnauthorized());
        for (AccountRole role : AccountRole.values()) {
            if (role != AccountRole.OWNER) {
                mvc.perform(post(ENDPOINT).with(actor(role)).with(csrf()).contentType("application/json")
                        .content("{\"username\":\"admin\"}")).andExpect(status().isForbidden());
            }
        }
        verifyNoInteractions(accounts);
    }

    @Test
    void ownerCannotSupplyAnyRoleOrCredentialState() throws Exception {
        for (String field : new String[]{"role", "accountType", "account_type", "mustChangePassword", "password"}) {
            mvc.perform(post(ENDPOINT).with(actor(AccountRole.OWNER)).with(csrf()).contentType("application/json")
                            .content("{\"username\":\"admin\",\"" + field + "\":\"OWNER\"}"))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(accounts);
    }

    @Test
    void blankAndMissingUsernameAreRejected() throws Exception {
        for (String body : new String[]{"{}", "{\"username\":\"  \"}", "{\"username\":123}", "{\"username\":true}"}) {
            mvc.perform(post(ENDPOINT).with(actor(AccountRole.OWNER)).with(csrf())
                    .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(accounts);
    }

    @Test
    void duplicateUsernameUsesConflictWithoutExposingCredentials() throws Exception {
        when(accounts.create(any(), eq("duplicate")))
                .thenThrow(new AdministratorAccountException("USERNAME_ALREADY_EXISTS"));
        mvc.perform(post(ENDPOINT).with(actor(AccountRole.OWNER)).with(csrf()).contentType("application/json")
                        .content("{\"username\":\"duplicate\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("USERNAME_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.temporaryPassword").doesNotExist());
    }

    @Test
    void missingCsrfDoesNotCreateAnAccount() throws Exception {
        mvc.perform(post(ENDPOINT).with(actor(AccountRole.OWNER)).contentType("application/json")
                .content("{\"username\":\"admin\"}")).andExpect(status().isForbidden());
        verifyNoInteractions(accounts);
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
