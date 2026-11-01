package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AccountJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import fu.fa26.sep490.g58.otcms_be.auth.service.AuthSessionService;
import fu.fa26.sep490.g58.otcms_be.auth.service.impl.TemporaryPasswordGenerator;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** API-to-PostgreSQL coverage; all beans and security filters are real, and each test rolls back. */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@LocalPostgresTest
@Transactional
class OwnerAdministratorApiIntegrationTest {
    private static final String BASE = "/api/v1/owner/administrator-accounts";
    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
    private final String prefix = "owner-api-" + UUID.randomUUID();

    @Autowired private MockMvc mvc;
    @Autowired private AccountRepository accounts;
    @Autowired private AuthSessionService sessions;
    @Autowired private PasswordEncoder encoder;
    @Autowired private EntityManager entityManager;

    private String ownerToken;

    @DynamicPropertySource
    static void testSigningKey(DynamicPropertyRegistry properties) {
        String ephemeralKey = new TemporaryPasswordGenerator().generate();
        properties.add("app.auth.session-security.jwt-secret", () -> ephemeralKey);
    }

    @BeforeEach
    void ownerSession() {
        var owner = save(prefix + "-owner", AccountType.OWNER, AccountStatus.ACTIVE);
        ownerToken = tokenFor(owner);
    }

    @Test
    void ownerCreatesAdministratorAndDatabaseStoresOnlyHashedPassword() throws Exception {
        String username = prefix + "-admin";
        JsonNode response = json.readTree(performOwner(post(BASE).with(csrf())
                .contentType("application/json").content("{\"username\":\"" + username + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.accountId").isNotEmpty())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.accountType").value("ADMIN"))
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andExpect(jsonPath("$.temporaryPassword").isNotEmpty())
                .andExpect(noCredentialField("passwordHash"))
                .andReturn().getResponse().getContentAsString());

        UUID accountId = UUID.fromString(response.path("accountId").asText());
        var saved = reload(accountId);
        assertEquals(username, saved.getUsername());
        assertEquals(AccountType.ADMIN, saved.getAccountType());
        assertEquals(AccountStatus.ACTIVE, saved.getStatus());
        assertTrue(saved.isMustChangePassword());
        String temporaryPassword = response.path("temporaryPassword").asText();
        assertTrue(saved.getPasswordHash() != null && !saved.getPasswordHash().isBlank());
        assertFalse(temporaryPassword.equals(saved.getPasswordHash()));
        assertTrue(encoder.matches(temporaryPassword, saved.getPasswordHash()));
    }

    @Test
    void ownerDeactivatesAdministratorInDatabase() throws Exception {
        var admin = save(prefix + "-admin", AccountType.ADMIN, AccountStatus.ACTIVE);

        performOwner(post(BASE + "/" + admin.getId() + "/deactivate").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"))
                .andExpect(noCredentialField("passwordHash"))
                .andExpect(noCredentialField("temporaryPassword"));

        var saved = reload(admin.getId());
        assertEquals(AccountStatus.DISABLED, saved.getStatus());
        assertEquals(AccountType.ADMIN, saved.getAccountType());
    }

    @Test
    void ownerReactivatesDisabledAdministratorInDatabase() throws Exception {
        var admin = save(prefix + "-admin", AccountType.ADMIN, AccountStatus.DISABLED);

        performOwner(post(BASE + "/" + admin.getId() + "/activate").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.accountType").value("ADMIN"));

        var saved = reload(admin.getId());
        assertEquals(AccountStatus.ACTIVE, saved.getStatus());
        assertEquals(AccountType.ADMIN, saved.getAccountType());
    }

    @Test
    void ownerReissuesPasswordAndReplacesDatabaseHash() throws Exception {
        String oldPassword = new TemporaryPasswordGenerator().generate();
        var admin = save(prefix + "-admin", AccountType.ADMIN, AccountStatus.ACTIVE);
        admin.setPasswordHash(encoder.encode(oldPassword));
        admin.setMustChangePassword(false);
        accounts.saveAndFlush(admin);
        String oldHash = reload(admin.getId()).getPasswordHash();
        assertTrue(encoder.matches(oldPassword, oldHash));

        JsonNode response = json.readTree(performOwner(
                        post(BASE + "/" + admin.getId() + "/password-reissues").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.accountId").value(admin.getId().toString()))
                .andExpect(jsonPath("$.temporaryPassword").isNotEmpty())
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andExpect(noCredentialField("passwordHash"))
                .andReturn().getResponse().getContentAsString());

        var saved = reload(admin.getId());
        String temporaryPassword = response.path("temporaryPassword").asText();
        // Boolean assertions keep credentials out of assertion failure messages.
        assertFalse(oldHash.equals(saved.getPasswordHash()));
        assertFalse(encoder.matches(oldPassword, saved.getPasswordHash()));
        assertTrue(encoder.matches(temporaryPassword, saved.getPasswordHash()));
        assertFalse(temporaryPassword.equals(saved.getPasswordHash()));
        assertTrue(saved.isMustChangePassword());
        assertEquals(AccountType.ADMIN, saved.getAccountType());

        for (String endpoint : new String[]{BASE, BASE + "/" + admin.getId()}) {
            performOwner(get(endpoint).param("query", prefix))
                    .andExpect(status().isOk())
                    .andExpect(result -> {
                        String body = result.getResponse().getContentAsString();
                        assertFalse(body.contains(temporaryPassword));
                        assertFalse(body.contains(saved.getPasswordHash()));
                        assertFalse(body.contains("temporaryPassword"));
                        assertFalse(body.contains("passwordHash"));
                    });
        }
    }

    @Test
    void ownerListsOnlyAdministratorsWithDatabaseSearchStatusAndPagination() throws Exception {
        for (AccountType type : AccountType.values()) {
            save(prefix + "-" + type.name(), type, AccountStatus.ACTIVE);
        }
        var disabled = save(prefix + "-disabled", AccountType.ADMIN, AccountStatus.DISABLED);
        entityManager.clear();

        var first = json.readTree(performOwner(get(BASE)
                        .param("query", " " + prefix.toUpperCase(Locale.ROOT) + " ")
                        .param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].accountType").value("ADMIN"))
                .andExpect(noCredentialField("passwordHash"))
                .andExpect(noCredentialField("temporaryPassword"))
                .andReturn().getResponse().getContentAsString());

        performOwner(get(BASE).param("query", prefix).param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].accountType").value("ADMIN"))
                .andExpect(result -> assertFalse(first.path("content").get(0).path("accountId").asText()
                        .equals(json.readTree(result.getResponse().getContentAsString())
                                .path("content").get(0).path("accountId").asText())));

        performOwner(get(BASE).param("query", prefix).param("status", "DISABLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].accountId").value(disabled.getId().toString()))
                .andExpect(jsonPath("$.content[0].status").value("DISABLED"));
        performOwner(get(BASE).param("query", prefix).param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"));
    }

    @Test
    void ownerReadsAdministratorDetailWithoutCredentials() throws Exception {
        var admin = save(prefix + "-admin", AccountType.ADMIN, AccountStatus.ACTIVE);
        admin.setPasswordHash(encoder.encode(new TemporaryPasswordGenerator().generate()));
        accounts.saveAndFlush(admin);
        entityManager.clear();

        performOwner(get(BASE + "/" + admin.getId()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.accountId").value(admin.getId().toString()))
                .andExpect(jsonPath("$.username").value(admin.getUsername()))
                .andExpect(jsonPath("$.accountType").value("ADMIN"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(noCredentialField("passwordHash"))
                .andExpect(noCredentialField("temporaryPassword"));
    }

    @Test
    void ownerCannotReadOtherAccountTypesOrUnknownAccount() throws Exception {
        for (AccountType type : AccountType.values()) {
            if (type == AccountType.ADMIN) continue;
            var account = save(prefix + "-" + type.name(), type, AccountStatus.ACTIVE);
            performOwner(get(BASE + "/" + account.getId()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("ADMINISTRATOR_ACCOUNT_NOT_FOUND"))
                    .andExpect(jsonPath("$.accountId").doesNotExist())
                    .andExpect(noCredentialField("passwordHash"));
        }
        performOwner(get(BASE + "/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void missingCsrfCannotMutateDatabase() throws Exception {
        var admin = save(prefix + "-admin", AccountType.ADMIN, AccountStatus.ACTIVE);
        performOwner(post(BASE + "/" + admin.getId() + "/deactivate"))
                .andExpect(status().isForbidden());
        assertEquals(AccountStatus.ACTIVE, reload(admin.getId()).getStatus());

        String username = prefix + "-blocked";
        performOwner(post(BASE).contentType("application/json")
                        .content("{\"username\":\"" + username + "\"}"))
                .andExpect(status().isForbidden());
        entityManager.clear();
        assertFalse(accounts.existsByUsername(username));
    }

    @Test
    void anonymousAndAdministratorCannotUseOwnerApi() throws Exception {
        var admin = save(prefix + "-admin", AccountType.ADMIN, AccountStatus.ACTIVE);
        String adminToken = tokenFor(admin);
        String username = prefix + "-blocked";
        mvc.perform(post(BASE).with(csrf()).contentType("application/json")
                        .content("{\"username\":\"" + username + "\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(BASE).with(csrf()).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType("application/json").content("{\"username\":\"" + username + "\"}"))
                .andExpect(status().isForbidden());
        entityManager.clear();
        assertFalse(accounts.existsByUsername(username));
    }

    private ResultMatcher noCredentialField(String field) {
        return result -> assertFalse(result.getResponse().getContentAsString().contains("\"" + field + "\""));
    }

    private ResultActions performOwner(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken));
    }

    private AccountJpaEntity reload(UUID accountId) {
        entityManager.flush();
        entityManager.clear();
        return accounts.findById(accountId).orElseThrow();
    }

    private String tokenFor(AccountJpaEntity account) {
        return sessions.create(account.getId(), "127.0.0.1", "owner-api-test").body().accessToken();
    }

    private AccountJpaEntity save(String username, AccountType type, AccountStatus status) {
        var account = new AccountJpaEntity();
        account.setUsername(username);
        account.setAccountType(type);
        account.setStatus(status);
        account.setNormalizedEmail(UUID.randomUUID() + "@example.test");
        account.setCreatedAt(Instant.now());
        account.setUpdatedAt(Instant.now());
        return accounts.saveAndFlush(account);
    }
}
