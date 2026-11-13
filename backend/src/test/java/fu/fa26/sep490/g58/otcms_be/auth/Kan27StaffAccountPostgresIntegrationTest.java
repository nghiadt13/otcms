package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.*;
import fu.fa26.sep490.g58.otcms_be.auth.repository.*;
import fu.fa26.sep490.g58.otcms_be.auth.service.*;
import fu.fa26.sep490.g58.otcms_be.auth.service.impl.TemporaryPasswordGenerator;
import fu.fa26.sep490.g58.otcms_be.common.security.*;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real PostgreSQL + security filters + HTTP requests. No mocked concurrency or persistence. */
@SpringBootTest
@LocalPostgresTest
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class Kan27StaffAccountPostgresIntegrationTest {
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        String ephemeral = new TemporaryPasswordGenerator().generate();
        registry.add("app.auth.session-security.jwt-secret", () -> ephemeral);
        registry.add("app.auth.rate-limit-hmac-secret", () -> ephemeral);
        registry.add("app.auth.login.requests", () -> 10000);
        registry.add("app.auth.login.window-seconds", () -> 60);
        registry.add("app.auth.staff-network.allowed-cidrs", () -> "127.0.0.1/32");
    }
    @Autowired MockMvc mvc;
    @Autowired AccountRepository accounts;
    @Autowired StaffAccountAdminService staff;
    @Autowired AuthSessionService sessions;
    @Autowired PasswordEncoder encoder;
    @Autowired VerificationCodeRepository resets;
    @Autowired fu.fa26.sep490.g58.otcms_be.auth.security.JwtTokenService tokens;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
    private final TemporaryPasswordGenerator passwords = new TemporaryPasswordGenerator();
    private AuthenticatedAccount actor;
    private String adminToken;
    private final String prefix = "k27-" + UUID.randomUUID();
    private final String correlation = UUID.randomUUID().toString();

    @BeforeEach void admin() {
        var admin = account(AccountType.ADMIN, false);
        var issued = sessions.create(admin.getId(), "127.0.0.1", "kan27-test");
        actor = new AuthenticatedAccount(admin.getId(), AccountRole.ADMIN, null);
        adminToken = issued.body().accessToken();
    }

    @ParameterizedTest @ValueSource(strings = {"RECEPTIONIST", "ORTHOPEDIC_DOCTOR", "IMAGING_TECHNICIAN", "LAB_TECHNICIAN"})
    void createPersistsOnlySuppliedHashAndSafeAudit(String role) throws Exception {
        String password = passwords.generate();
        var result = create(role, prefix, password).andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.role").value(role)).andExpect(jsonPath("$.initialPassword").doesNotExist())
                .andExpect(jsonPath("$.temporaryPassword").doesNotExist()).andReturn();
        UUID id = UUID.fromString(body(result).path("accountId").asString());
        var saved = accounts.findById(id).orElseThrow();
        assertNull(saved.getNormalizedEmail());
        assertNull(saved.getFullName());
        assertNull(saved.getDisplayName());
        assertTrue(saved.isMustChangePassword());
        assertEquals(AccountStatus.ACTIVE, saved.getStatus());
        assertTrue(encoder.matches(password, saved.getPasswordHash()));
        assertNotEquals(password, saved.getPasswordHash());
        assertEquals(0, count("select count(*) from staff_profiles where account_id = ?", id));
        assertAudit(id, "CREATE", correlation);
        assertFalse(result.getResponse().getContentAsString().contains(password));
        create(role, prefix, password).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("USERNAME_TAKEN"));
        assertEquals(1, count("select count(*) from audit_logs where source = 'account_management_audits' and entity_id = ?", id));
    }

    @Test void forbiddenActorsAndTargetCategoriesAreHidden() throws Exception {
        for (AccountType type : AccountType.values()) {
            if (type == AccountType.ADMIN) continue;
            var other = account(type, false);
            String token = sessions.create(other.getId(), "127.0.0.1", "scope").body().accessToken();
            mvc.perform(get("/api/v1/admin/staff-accounts").header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
        for (AccountType type : List.of(AccountType.PATIENT, AccountType.ADMIN, AccountType.OWNER)) {
            var target = account(type, false);
            mvc.perform(get("/api/v1/admin/staff-accounts/" + target.getId()).header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
            mutation(target.getId(), "password-reissues").andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/v1/admin/staff-accounts/" + UUID.randomUUID()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
        mvc.perform(get("/api/v1/admin/staff-accounts")).andExpect(status().isUnauthorized());
    }

    @Test void directoryLiteralSearchProfilesFiltersAndStablePagination() throws Exception {
        String password = passwords.generate();
        UUID first = staff.create(actor, "ORTHOPEDIC_DOCTOR", prefix + "%_!", password, correlation).accountId();
        UUID second = staff.create(actor, "RECEPTIONIST", prefix + "plain", password, correlation).accountId();
        UUID profile = UUID.randomUUID();
        jdbc.update("insert into staff_profiles(id,account_id,display_name,work_contact,status) values (?,?,?,?,'ACTIVE')",
                profile, first, "Visible summary", "private-contact");
        jdbc.update("update accounts set created_at = ? where id in (?,?)", java.sql.Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")), first, second);
        mvc.perform(get("/api/v1/admin/staff-accounts").param("q", prefix + "%_!")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].role").value("ORTHOPEDIC_DOCTOR"))
                .andExpect(jsonPath("$.items[0].staffProfile.id").value(profile.toString()))
                .andExpect(jsonPath("$.items[0].staffProfile.displayName").value("Visible summary"))
                .andExpect(jsonPath("$.items[0].staffProfile.workContact").doesNotExist())
                .andExpect(jsonPath("$.items[0].passwordHash").doesNotExist());
        var page0 = body(mvc.perform(get("/api/v1/admin/staff-accounts").param("q", prefix).param("size", "1")
                .header("Authorization", "Bearer " + adminToken)).andExpect(status().isOk()).andReturn());
        var page1 = body(mvc.perform(get("/api/v1/admin/staff-accounts").param("q", prefix).param("size", "1").param("page", "1")
                .header("Authorization", "Bearer " + adminToken)).andExpect(status().isOk()).andReturn());
        var ordered = jdbc.queryForList("select id::text from accounts where id in (?,?) order by created_at desc,id desc", String.class, first, second);
        assertEquals(ordered.get(0), page0.path("items").get(0).path("accountId").asString());
        assertEquals(ordered.get(1), page1.path("items").get(0).path("accountId").asString());
        mvc.perform(get("/api/v1/admin/staff-accounts").param("q", prefix).param("role", "ORTHOPEDIC_DOCTOR")
                .header("Authorization", "Bearer " + adminToken)).andExpect(jsonPath("$.totalItems").value(1));
        mutation(second, "deactivation").andExpect(status().isOk());
        for (String status : List.of("ACTIVE", "INACTIVE")) mvc.perform(get("/api/v1/admin/staff-accounts")
                .param("q", prefix).param("status", status).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(get("/api/v1/admin/staff-accounts").param("q", UUID.randomUUID().toString())
                .header("Authorization", "Bearer " + adminToken)).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0));
        for (String[] invalid : List.of(new String[]{"page", "-1"}, new String[]{"size", "0"}, new String[]{"size", "101"},
                new String[]{"role", "DOCTOR"}, new String[]{"status", "DISABLED"}, new String[]{"q", "x".repeat(101)}))
            mvc.perform(get("/api/v1/admin/staff-accounts").param(invalid[0], invalid[1]).header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.message").value("Tiêu chí tìm kiếm không hợp lệ. Vui lòng kiểm tra lại."));
        assertEquals(1, count("select count(*) from staff_profiles where account_id = ?", first));
    }

    @Test void firstLoginRequiresChangeAndBothUsernameAtAndLegacyEmailRemainUsable() throws Exception {
        String initial = passwords.generate(), replacement = passwords.generate();
        String username = prefix + "@clinic";
        UUID id = staff.create(actor, "ORTHOPEDIC_DOCTOR", username, initial, correlation).accountId();
        var first = body(login(username, initial).andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true)).andReturn());
        String token = first.path("accessToken").asString();
        mvc.perform(get("/api/v1/clinic-services").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PASSWORD_CHANGE_REQUIRED"));
        mvc.perform(post("/api/auth/change-password").with(csrf()).header("Authorization", "Bearer " + token)
                        .contentType("application/json").content(json.writeValueAsString(Map.of("currentPassword", initial, "newPassword", replacement))))
                .andExpect(status().isNoContent());
        assertFalse(accounts.findById(id).orElseThrow().isMustChangePassword());
        login(username, initial).andExpect(status().isUnauthorized());
        String fresh = body(login(username, replacement).andExpect(status().isOk()).andReturn()).path("accessToken").asString();
        mvc.perform(get("/api/v1/clinic-services").header("Authorization", "Bearer " + fresh)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/clinic-services").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        var legacy = account(AccountType.RECEPTIONIST, false);
        String legacyPassword = passwords.generate();
        jdbc.update("update accounts set username = null,password_hash = ? where id = ?", encoder.encode(legacyPassword), legacy.getId());
        login(legacy.getNormalizedEmail(), legacyPassword).andExpect(status().isOk());
    }

    @Test void statusTransitionsPreserveProfileAndOldJwtCannotRevive() throws Exception {
        var fixture = usableStaff();
        UUID profileId = UUID.randomUUID();
        jdbc.update("insert into staff_profiles(id,account_id,display_name,work_contact,status) values (?,?,?,?,'ACTIVE')",
                profileId, fixture.id(), "Retained profile", "private-contact");
        String token = body(login(fixture.username(), fixture.password()).andExpect(status().isOk()).andReturn()).path("accessToken").asString();
        mvc.perform(get("/api/v1/clinic-services").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mutation(fixture.id(), "deactivation").andExpect(status().isOk());
        assertAudit(fixture.id(), "DEACTIVATE", correlation);
        mvc.perform(get("/api/v1/clinic-services").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        mutation(fixture.id(), "deactivation").andExpect(status().isConflict());
        mutation(fixture.id(), "activation").andExpect(status().isOk());
        assertAudit(fixture.id(), "ACTIVATE", correlation);
        mutation(fixture.id(), "activation").andExpect(status().isConflict());
        mvc.perform(get("/api/v1/clinic-services").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        login(fixture.username(), fixture.password()).andExpect(status().isOk());
        jdbc.update("update accounts set status = 'TEMPORARILY_LOCKED',failed_credential_count = 4,locked_until = now() + interval '10 minutes' where id = ?", fixture.id());
        mutation(fixture.id(), "activation").andExpect(status().isConflict());
        assertEquals(1, count("select count(*) from staff_profiles where id = ?", profileId));
        assertEquals(3, count("select count(*) from audit_logs where source = 'account_management_audits' and entity_id = ?", fixture.id()));
    }

    @Test void reissueRevokesSessionsSupersedesResetAndDoesNotActivateDisabledStaff() throws Exception {
        var fixture = usableStaff();
        String token = body(login(fixture.username(), fixture.password()).andExpect(status().isOk()).andReturn()).path("accessToken").asString();
        var reset = new VerificationCodeJpaEntity();
        reset.setAccountId(fixture.id());
        reset.setPurpose(VerificationPurpose.PASSWORD_RESET);
        reset.setCodeHash(UUID.randomUUID().toString());
        reset.setCreatedAt(Instant.now());
        reset.setExpiresAt(Instant.now().plusSeconds(600));
        resets.saveAndFlush(reset);
        var result = mutation(fixture.id(), "password-reissues").andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.mustChangePassword").value(true)).andReturn();
        String temporary = body(result).path("temporaryPassword").asString();
        assertTrue(encoder.matches(temporary, accounts.findById(fixture.id()).orElseThrow().getPasswordHash()));
        assertNotNull(resets.findById(reset.getId()).orElseThrow().getSupersededAt());
        assertAudit(fixture.id(), "REISSUE_PASSWORD", correlation);
        login(fixture.username(), fixture.password()).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/clinic-services").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        login(fixture.username(), temporary).andExpect(status().isOk()).andExpect(jsonPath("$.mustChangePassword").value(true));
        jdbc.update("update accounts set status = 'DISABLED',failed_credential_count = 4,locked_until = now() + interval '10 minutes' where id = ?", fixture.id());
        mutation(fixture.id(), "password-reissues").andExpect(status().isOk());
        var saved = accounts.findById(fixture.id()).orElseThrow();
        assertEquals(AccountStatus.DISABLED, saved.getStatus());
        assertEquals(4, saved.getFailedCredentialCount());
        assertNotNull(saved.getLockedUntil());
        var auditJson = jdbc.queryForList("select row_to_json(a)::text from audit_logs a where source = 'account_management_audits' and entity_id = ?", String.class, fixture.id());
        assertTrue(auditJson.stream().noneMatch(a -> a.contains(temporary) || a.contains("password_hash") || a.contains("token")));
    }

    @Test void expiredAndForeignAccountSessionsCannotAuthorizeStaffJwt() throws Exception {
        var fixture = usableStaff();
        String token = body(login(fixture.username(), fixture.password()).andExpect(status().isOk()).andReturn()).path("accessToken").asString();
        UUID sessionId = tokens.validate(token).sessionId();
        jdbc.update("update sessions set created_at=now()-interval '2 hours',expires_at=now()-interval '1 hour' where id=?", sessionId);
        mvc.perform(get("/api/v1/clinic-services").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        String foreign = tokens.issue(fixture.id(), AccountType.RECEPTIONIST, tokens.validate(adminToken).sessionId());
        mvc.perform(get("/api/v1/clinic-services").header("Authorization", "Bearer " + foreign)).andExpect(status().isUnauthorized());
    }

    @Test void patientStatusAuditsAndFailedOrRolledBackMutationsLeaveNoSuccessAudit() throws Exception {
        var patient = account(AccountType.PATIENT, false);
        for (String action : List.of("deactivation", "activation")) mvc.perform(post("/api/v1/admin/accounts/" + patient.getId() + "/" + action)
                .with(csrf()).header("Authorization", "Bearer " + adminToken).header("X-Correlation-ID", correlation)).andExpect(status().isOk());
        assertAudit(patient.getId(), "ACTIVATE", correlation);
        assertAudit(patient.getId(), "DEACTIVATE", correlation);
        mvc.perform(post("/api/v1/admin/accounts/" + patient.getId() + "/activation").with(csrf())
                .header("Authorization", "Bearer " + adminToken)).andExpect(status().isConflict());
        assertEquals(2, count("select count(*) from audit_logs where source = 'account_management_audits' and entity_id = ?", patient.getId()));
        String rollbackUsername = prefix + "rb";
        assertThrows(ForcedRollback.class, () -> new TransactionTemplate(transactions).execute(status -> {
            staff.create(actor, "RECEPTIONIST", rollbackUsername, passwords.generate(), "audit-rollback");
            throw new ForcedRollback();
        }));
        assertEquals(0, count("select count(*) from accounts where username = ?", rollbackUsername));
        assertEquals(0, count("select count(*) from audit_logs where source = 'account_management_audits' "
                + "and action = 'CREATE' and actor_account_id = ?", actor.accountId()));
        String auditFailUsername = prefix + "af";
        // An audit write that fails must roll back the business change made in the same transaction. The failure is
        // forced by the database: a constraint that only rejects audit rows written by this test's admin.
        jdbc.execute("alter table audit_logs add constraint ck_k27_audit_failure check "
                + "(actor_account_id is distinct from '" + actor.accountId() + "'::uuid) not valid");
        try {
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> staff.create(actor, "RECEPTIONIST", auditFailUsername, passwords.generate(), "audit-failure"));
        } finally {
            jdbc.execute("alter table audit_logs drop constraint ck_k27_audit_failure");
        }
        assertEquals(0, count("select count(*) from accounts where username = ?", auditFailUsername));
    }

    @RepeatedTest(3) void duplicateCreateHasOneHttpWinnerAndOneAudit() throws Exception {
        String password = passwords.generate();
        var results = concurrent(() -> create("RECEPTIONIST", prefix, password).andReturn(),
                () -> create("RECEPTIONIST", prefix, password).andReturn());
        assertEquals(List.of(201, 409), results.stream().map(r -> r.getResponse().getStatus()).sorted().toList());
        var loser = results.stream().filter(r -> r.getResponse().getStatus() == 409).findFirst().orElseThrow();
        assertEquals("USERNAME_TAKEN", body(loser).path("error").path("code").asString());
        assertEquals(1, count("select count(*) from accounts where username = ?", prefix));
        assertEquals(1, count("select count(*) from audit_logs a join accounts x on x.id=a.entity_id where a.source='account_management_audits' and x.username=? and a.action='CREATE'", prefix));
    }

    @RepeatedTest(3) void loginVersusReissueCannotLeaveOldCredentialSessionUsable() throws Exception { loginRace("password-reissues"); }
    @RepeatedTest(3) void loginVersusDeactivateCannotLeaveOldCredentialSessionUsable() throws Exception { loginRace("deactivation"); }

    private void loginRace(String action) throws Exception {
        var fixture = usableStaff();
        String existing = body(login(fixture.username(), fixture.password()).andExpect(status().isOk()).andReturn()).path("accessToken").asString();
        var results = concurrent(() -> login(fixture.username(), fixture.password()).andReturn(),
                () -> mutation(fixture.id(), action).andReturn());
        assertEquals(200, results.get(1).getResponse().getStatus());
        assertTrue(Set.of(200, 401).contains(results.getFirst().getResponse().getStatus()));
        assertEquals(0, count("select count(*) from sessions where account_id=? and revoked_at is null and expires_at > now()", fixture.id()));
        if (results.getFirst().getResponse().getStatus() == 200) {
            String racedToken = body(results.getFirst()).path("accessToken").asString();
            mvc.perform(get("/api/v1/clinic-services").header("Authorization", "Bearer " + racedToken)).andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/v1/clinic-services").header("Authorization", "Bearer " + existing)).andExpect(status().isUnauthorized());
        login(fixture.username(), fixture.password()).andExpect(status().isUnauthorized());
        if (action.equals("deactivation")) assertEquals(AccountStatus.DISABLED, accounts.findById(fixture.id()).orElseThrow().getStatus());
        else assertFalse(encoder.matches(fixture.password(), accounts.findById(fixture.id()).orElseThrow().getPasswordHash()));
    }

    private List<MvcResult> concurrent(Callable<MvcResult> first, Callable<MvcResult> second) throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            Callable<MvcResult> a = () -> { ready.countDown(); if (!start.await(10, TimeUnit.SECONDS)) throw new TimeoutException(); return first.call(); };
            Callable<MvcResult> b = () -> { ready.countDown(); if (!start.await(10, TimeUnit.SECONDS)) throw new TimeoutException(); return second.call(); };
            var one = pool.submit(a);
            var two = pool.submit(b);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            return List.of(one.get(30, TimeUnit.SECONDS), two.get(30, TimeUnit.SECONDS));
        }
    }

    private record Fixture(UUID id, String username, String password) { }
    private Fixture usableStaff() {
        String username = "k27-" + UUID.randomUUID(), password = passwords.generate();
        UUID id = staff.create(actor, "RECEPTIONIST", username, password, correlation).accountId();
        jdbc.update("update accounts set must_change_password=false where id=?", id);
        return new Fixture(id, username, password);
    }
    private AccountJpaEntity account(AccountType type, boolean forced) {
        var account = new AccountJpaEntity();
        account.setNormalizedEmail(UUID.randomUUID() + "@example.test");
        account.setAccountType(type);
        account.setStatus(AccountStatus.ACTIVE);
        account.setMustChangePassword(forced);
        account.setCreatedAt(Instant.now());
        account.setUpdatedAt(Instant.now());
        return accounts.saveAndFlush(account);
    }
    private ResultActions create(String role, String username, String password) throws Exception {
        return mvc.perform(post("/api/v1/admin/staff-accounts").with(csrf()).header("Authorization", "Bearer " + adminToken)
                .header("X-Correlation-ID", correlation).contentType("application/json")
                .content(json.writeValueAsString(Map.of("role", role, "username", username, "initialPassword", password))));
    }
    private ResultActions login(String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/staff/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("username", username, "password", password))));
    }
    private ResultActions mutation(UUID id, String action) throws Exception {
        return mvc.perform(post("/api/v1/admin/staff-accounts/" + id + "/" + action).with(csrf())
                .header("Authorization", "Bearer " + adminToken).header("X-Correlation-ID", correlation));
    }
    private JsonNode body(MvcResult result) { return json.readTree(result.getResponse().getContentAsByteArray()); }
    private int count(String sql, Object... arguments) { return jdbc.queryForObject(sql, Integer.class, arguments); }
    private void assertAudit(UUID id, String action, String requestCorrelation) {
        assertEquals(1, count("select count(*) from audit_logs where source='account_management_audits' and entity_id=? and actor_account_id=? and action=? and correlation_id=? and occurred_at is not null",
                id, actor.accountId(), action, requestCorrelation));
    }
    private static class ForcedRollback extends RuntimeException { }
}
