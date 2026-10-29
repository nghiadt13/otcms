package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.appointment.config.AppointmentWebConfiguration;
import fu.fa26.sep490.g58.otcms_be.appointment.web.PublicRequestRateLimitInterceptor;
import fu.fa26.sep490.g58.otcms_be.auth.controller.OwnerAdministratorController;
import fu.fa26.sep490.g58.otcms_be.auth.exception.AdministratorAccountException;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.response.AdministratorAccountResponse;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.response.CreateAdministratorAccountResponse;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.response.ReissueAdministratorPasswordResponse;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OwnerAdministratorController.class)
@Import({SecurityConfig.class, UC09AdministratorAccountControllerTest.Fixture.class})
class UC09AdministratorAccountControllerTest {
    private static final String BASE = "/api/v1/owner/administrator-accounts";
    private final UUID id = UUID.randomUUID();
    @Autowired private MockMvc mvc;
    @MockitoBean private OwnerAdministratorService accounts;
    @MockitoBean private JwtTokenService tokens;
    @MockitoBean private AccountAccessService access;
    @MockitoBean private AppointmentWebConfiguration appointmentWebConfiguration;
    @MockitoBean private PublicRequestRateLimitInterceptor publicRequestRateLimitInterceptor;
    @MockitoBean private ProductionReadinessGateInterceptor productionReadinessGateInterceptor;

    @Test
    void ownerListsOnlyAdministratorDataWithPaginationAndSafeFields() throws Exception {
        when(accounts.list(any(), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(response()), PageRequest.of(0, 20), 1));
        mvc.perform(get(BASE).with(actor(AccountRole.OWNER)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.content[0].accountType").value("ADMIN"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.content[0].temporaryPassword").doesNotExist())
                .andExpect(jsonPath("$.content[0].mustChangePassword").doesNotExist())
                .andExpect(jsonPath("$.content[0].lockedUntil").doesNotExist());
    }

    @Test
    void searchFilterAndPaginationAreForwardedWithoutAcceptingRoleSelection() throws Exception {
        when(accounts.list(any(), eq("admin"), eq(AccountStatus.DISABLED), eq(1), eq(2)))
                .thenReturn(new PageImpl<>(List.of(response()), PageRequest.of(1, 2), 5));
        mvc.perform(get(BASE).with(actor(AccountRole.OWNER)).param("query", "admin")
                        .param("status", "DISABLED").param("page", "1").param("size", "2")
                        .param("role", "PATIENT").param("accountType", "OWNER"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content[0].accountType").value("ADMIN"));
        verify(accounts).list(any(), eq("admin"), eq(AccountStatus.DISABLED), eq(1), eq(2));
    }

    @Test
    void ownerReadsSafeAdministratorDetail() throws Exception {
        when(accounts.detail(any(), eq(id))).thenReturn(response());
        mvc.perform(get(BASE + "/" + id).with(actor(AccountRole.OWNER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accountId").value(id.toString()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.temporaryPassword").doesNotExist())
                .andExpect(jsonPath("$.failedCredentialCount").doesNotExist());
    }

    @Test
    void anonymousAndEveryNonOwnerCannotListOrReadDetails() throws Exception {
        for (String endpoint : new String[]{BASE, BASE + "/" + id}) {
            mvc.perform(get(endpoint)).andExpect(status().isUnauthorized());
            for (AccountRole role : AccountRole.values()) {
                if (role != AccountRole.OWNER) {
                    mvc.perform(get(endpoint).with(actor(role))).andExpect(status().isForbidden());
                }
            }
        }
        verifyNoInteractions(accounts);
    }

    @Test
    void nonAdminOrUnknownTargetDoesNotRevealAccountData() throws Exception {
        when(accounts.detail(any(), eq(id)))
                .thenThrow(new AdministratorAccountException("ADMINISTRATOR_ACCOUNT_NOT_FOUND"));
        mvc.perform(get(BASE + "/" + id).with(actor(AccountRole.OWNER)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.accountId").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void invalidQueryTypesAndPaginationReturn400() throws Exception {
        mvc.perform(get(BASE).with(actor(AccountRole.OWNER)).param("status", "invalid"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(BASE).with(actor(AccountRole.OWNER)).param("page", "invalid"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(BASE + "/invalid").with(actor(AccountRole.OWNER)))
                .andExpect(status().isBadRequest());
        when(accounts.list(any(), isNull(), isNull(), eq(-1), eq(20)))
                .thenThrow(new AdministratorAccountException("VALIDATION_FAILED"));
        mvc.perform(get(BASE).with(actor(AccountRole.OWNER)).param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createAndReissueSecretsAreAbsentFromSubsequentListAndDetail() throws Exception {
        String issued = new fu.fa26.sep490.g58.otcms_be.auth.service.impl.TemporaryPasswordGenerator().generate();
        when(accounts.create(any(), eq("admin"))).thenReturn(new CreateAdministratorAccountResponse(id, "admin", issued));
        when(accounts.reissuePassword(any(), eq(id))).thenReturn(new ReissueAdministratorPasswordResponse(id, issued));
        when(accounts.list(any(), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(response())));
        when(accounts.detail(any(), eq(id))).thenReturn(response());
        mvc.perform(post(BASE).with(actor(AccountRole.OWNER)).with(csrf())
                        .contentType("application/json").content("{\"username\":\"admin\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.temporaryPassword").value(issued));
        mvc.perform(post(BASE + "/" + id + "/password-reissues").with(actor(AccountRole.OWNER)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.temporaryPassword").value(issued));
        for (String endpoint : new String[]{BASE, BASE + "/" + id}) {
            mvc.perform(get(endpoint).with(actor(AccountRole.OWNER))).andExpect(status().isOk())
                    .andExpect(result -> assertFalse(result.getResponse().getContentAsString().contains(issued)));
        }
    }

    private AdministratorAccountResponse response() {
        return new AdministratorAccountResponse(id, "admin", null, "ADMIN", "ACTIVE", Instant.EPOCH, Instant.EPOCH);
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
