package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.exception.AdministratorAccountException;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AuthSessionRepository;
import fu.fa26.sep490.g58.otcms_be.auth.service.OwnerAdministratorService;
import fu.fa26.sep490.g58.otcms_be.auth.service.AuthSessionService;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

/** Real PostgreSQL evidence. Each test owns unique records; no shared data/schema is deleted. */
@SpringBootTest
@LocalPostgresTest
class AdministratorAccountDatabaseVerificationTest {
    @DynamicPropertySource
    static void testSigningKey(DynamicPropertyRegistry properties) {
        String ephemeralKey = new fu.fa26.sep490.g58.otcms_be.auth.service.impl.TemporaryPasswordGenerator().generate();
        properties.add("app.auth.session-security.jwt-secret", () -> ephemeralKey);
    }

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired OwnerAdministratorService administrators;
    @Autowired AccountRepository accounts;
    @Autowired AuthSessionRepository sessionRepository;
    @Autowired AuthSessionService sessions;
    @Autowired PasswordEncoder encoder;

    @Test
    void upgradeFromV019PreservesExistingAccountsAndSessionLinks() {
        String schema = "uc_accounts_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        var v19 = flyway(schema, "19");
        v19.migrate();
        List<UUID> ids = new ArrayList<>();
        for (AccountType type : AccountType.values()) {
            UUID id = UUID.randomUUID();
            ids.add(id);
            jdbc.update("insert into " + schema + ".accounts "
                            + "(id, normalized_email, account_type, status, password_hash, created_at, updated_at) "
                            + "values (?, ?, ?, 'ACTIVE', ?, now(), now())",
                    id, "upgrade-" + id + "@example.test", type.name(), encoder.encode(UUID.randomUUID().toString()));
            jdbc.update("insert into " + schema + ".auth_sessions "
                            + "(id, account_id, token_family_id, refresh_token_hash, created_at, expires_at) "
                            + "values (?, ?, ?, ?, now(), now() + interval '1 day')",
                    UUID.randomUUID(), id, UUID.randomUUID(), UUID.randomUUID().toString());
        }
        var originalAccounts = jdbc.queryForList("select to_jsonb(a)::text from " + schema + ".accounts a order by id", String.class);
        var originalSessions = jdbc.queryForList("select row_to_json(s)::text from " + schema + ".auth_sessions s order by id", String.class);
        assertEquals(1, flyway(schema, "20").migrate().migrationsExecuted);
        assertEquals(1, flyway(schema, "21").migrate().migrationsExecuted);
        flyway(schema, "21").validate();
        assertTrue(originalAccounts.equals(jdbc.queryForList("select (to_jsonb(a) - 'username' - 'must_change_password')::text "
                + "from " + schema + ".accounts a order by id", String.class)), "Existing account fields must survive upgrade");
        assertTrue(originalSessions.equals(jdbc.queryForList("select row_to_json(s)::text from " + schema + ".auth_sessions s order by id", String.class)),
                "Existing session records must survive upgrade");
        assertEquals(ids.size(), jdbc.queryForObject("select count(*) from " + schema
                + ".accounts where username is null and must_change_password = false", Integer.class));
        assertEquals(List.of(19, 20, 21), jdbc.queryForList("select version::integer from " + schema
                + ".flyway_schema_history where version in ('019','020','021','19','20','21') and success order by installed_rank", Integer.class));
        jdbc.update("update " + schema + ".accounts set must_change_password = true where account_type = 'ADMIN'");
        assertEquals(1, jdbc.queryForObject("select count(*) from " + schema
                + ".accounts where account_type = 'ADMIN' and username is null and must_change_password", Integer.class));
    }

    @Test
    void simultaneousDuplicateUsernameCreatesExactlyOneAccount() throws Exception {
        String username = "concurrent-create-" + UUID.randomUUID();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var action = (java.util.concurrent.Callable<String>) () -> {
                ready.countDown();
                await(start);
                try {
                    administrators.create(owner(), username);
                    return "CREATED";
                } catch (AdministratorAccountException exception) {
                    return exception.getMessage();
                }
            };
            var first = executor.submit(action);
            var second = executor.submit(action);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            var outcomes = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            assertEquals(1, outcomes.stream().filter("CREATED"::equals).count());
            assertEquals(1, outcomes.stream().filter("USERNAME_ALREADY_EXISTS"::equals).count());
            assertEquals(1, jdbc.queryForObject("select count(*) from accounts where username = ?", Integer.class, username));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentReissuesWaitForAccountLockAndCommitOnlyLatestCredential() throws Exception {
        var created = administrators.create(owner(), "concurrent-reissue-" + UUID.randomUUID());
        UUID id = created.getAccountId();
        sessions.create(id, "127.0.0.1", "database-verification");
        CountDownLatch firstHoldingLock = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondConnected = new CountDownLatch(1);
        AtomicInteger secondPid = new AtomicInteger();
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                var result = administrators.reissuePassword(owner(), id);
                firstHoldingLock.countDown();
                await(releaseFirst);
                return result;
            }));
            assertTrue(firstHoldingLock.await(10, TimeUnit.SECONDS));
            var second = executor.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                secondPid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                secondConnected.countDown();
                return administrators.reissuePassword(owner(), id);
            }));
            assertTrue(secondConnected.await(10, TimeUnit.SECONDS));
            boolean blocked = false;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadline && !blocked) {
                blocked = Boolean.TRUE.equals(jdbc.queryForObject(
                        "select cardinality(pg_blocking_pids(?)) > 0", Boolean.class, secondPid.get()));
                if (!blocked) Thread.sleep(25);
            }
            assertTrue(blocked, "Second transaction must wait on an actual PostgreSQL lock");
            releaseFirst.countDown();
            var firstIssued = first.get(20, TimeUnit.SECONDS);
            var secondIssued = second.get(20, TimeUnit.SECONDS);
            var saved = accounts.findById(id).orElseThrow();
            assertTrue(encoder.matches(secondIssued.getTemporaryPassword(), saved.getPasswordHash()));
            assertFalse(encoder.matches(firstIssued.getTemporaryPassword(), saved.getPasswordHash()));
            assertFalse(encoder.matches(created.getTemporaryPassword(), saved.getPasswordHash()));
            assertTrue(saved.isMustChangePassword());
            assertEquals("ACTIVE", saved.getStatus().name());
            assertEquals(AccountType.ADMIN, saved.getAccountType());
            assertTrue(sessionRepository.findAllByAccountId(id).stream().allMatch(session -> session.getRevokedAt() != null));
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void nonAdminMutationsAreRejectedAndRealQueryDtosNeverExposeCredentials() {
        UUID nonAdmin = UUID.randomUUID();
        jdbc.update("insert into accounts (id, normalized_email, account_type, status, created_at, updated_at) "
                + "values (?, ?, 'PATIENT', 'ACTIVE', now(), now())", nonAdmin, nonAdmin + "@example.test");
        assertThrows(AdministratorAccountException.class, () -> administrators.activate(owner(), nonAdmin));
        assertThrows(AdministratorAccountException.class, () -> administrators.deactivate(owner(), nonAdmin));
        assertThrows(AdministratorAccountException.class, () -> administrators.reissuePassword(owner(), nonAdmin));
        assertEquals("ACTIVE", jdbc.queryForObject("select status from accounts where id = ?", String.class, nonAdmin));
        assertThrows(AdministratorAccountException.class, () -> administrators.detail(owner(), nonAdmin));
        var created = administrators.create(owner(), "real-query-" + UUID.randomUUID());
        var mapper = JsonMapper.builder().findAndAddModules().build();
        String detail = mapper.writeValueAsString(administrators.detail(owner(), created.getAccountId()));
        String list = mapper.writeValueAsString(administrators.list(owner(), created.getUsername(), null, 0, 20).getContent());
        for (String response : List.of(detail, list)) {
            assertFalse(response.contains("passwordHash"));
            assertFalse(response.contains("temporaryPassword"));
            assertFalse(response.contains("mustChangePassword"));
            assertFalse(response.contains(created.getTemporaryPassword()));
        }
    }

    private Flyway flyway(String schema, String target) {
        return Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target(target).load();
    }

    private AuthenticatedAccount owner() {
        return new AuthenticatedAccount(UUID.randomUUID(), AccountRole.OWNER, UUID.randomUUID());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrency barrier timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrency barrier interrupted", exception);
        }
    }
}
