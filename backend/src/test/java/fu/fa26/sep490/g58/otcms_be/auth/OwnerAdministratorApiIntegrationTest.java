package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AccountJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import fu.fa26.sep490.g58.otcms_be.auth.service.impl.TemporaryPasswordGenerator;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full-stack integration evidence for Owner administrator management.
 *
 * <p>Unlike the controller slice tests, these cases use the real controller,
 * service, repository, transaction management, security chain and PostgreSQL
 * test database.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@LocalPostgresTest
@Transactional
class OwnerAdministratorApiIntegrationTest {
    private static final String ENDPOINT = "/api/v1/owner/administrator-accounts";

    @DynamicPropertySource
    static void testSigningKey(DynamicPropertyRegistry properties) {
        String ephemeralKey = new TemporaryPasswordGenerator().generate();
        properties.add("app.auth.session-security.jwt-secret", () -> ephemeralKey);
    }

    @Autowired private MockMvc mvc;
    @Autowired private AccountRepository accounts;
    @Autowired private PasswordEncoder encoder;

    @Test
    void createEndpointPersistsAdministratorThroughControllerServiceRepositoryAndPostgres() throws Exception {
        String username = "owner-api-" + UUID.randomUUID();

        mvc.perform(post(ENDPOINT)
                        .with(owner())
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"username\":\"" + username + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.accountType").value("ADMIN"))
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andExpect(jsonPath("$.temporaryPassword").isString())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        AccountJpaEntity saved = accounts.findByUsername(username).orElseThrow();
        assertEquals(AccountType.ADMIN, saved.getAccountType());
        assertEquals(AccountStatus.ACTIVE, saved.getStatus());
        assertTrue(saved.isMustChangePassword());
        assertNotNull(saved.getPasswordHash());
        assertFalse(saved.getPasswordHash().isBlank());
    }

    @Test
    void managementEndpointsPersistDeactivateActivateAndPasswordReissue() throws Exception {
        AccountJpaEntity administrator = administrator();
        UUID accountId = administrator.getId();
        String oldHash = administrator.getPasswordHash();

        mvc.perform(post(ENDPOINT + "/" + accountId + "/deactivate")
                        .with(owner())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.accountType").value("ADMIN"))
                .andExpect(jsonPath("$.status").value("DISABLED"));

        assertEquals(AccountStatus.DISABLED,
                accounts.findById(accountId).orElseThrow().getStatus());

        mvc.perform(post(ENDPOINT + "/" + accountId + "/activate")
                        .with(owner())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertEquals(AccountStatus.ACTIVE,
                accounts.findById(accountId).orElseThrow().getStatus());

        mvc.perform(post(ENDPOINT + "/" + accountId + "/password-reissues")
                        .with(owner())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andExpect(jsonPath("$.temporaryPassword").isString())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        AccountJpaEntity saved = accounts.findById(accountId).orElseThrow();
        assertTrue(saved.isMustChangePassword());
        assertNotEquals(oldHash, saved.getPasswordHash());
        assertFalse(encoder.matches("old-password-123", saved.getPasswordHash()));
    }

    private AccountJpaEntity administrator() {
        AccountJpaEntity account = new AccountJpaEntity();
        account.setUsername("owner-api-" + UUID.randomUUID());
        account.setAccountType(AccountType.ADMIN);
        account.setStatus(AccountStatus.ACTIVE);
        account.setPasswordHash(encoder.encode("old-password-123"));
        account.setCreatedAt(Instant.now());
        account.setUpdatedAt(Instant.now());
        return accounts.saveAndFlush(account);
    }

    private RequestPostProcessor owner() {
        var principal = new AuthenticatedAccount(UUID.randomUUID(), AccountRole.OWNER, UUID.randomUUID());
        return authentication(new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_OWNER"))));
    }
}
