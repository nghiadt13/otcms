package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AccountJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.repository.EmailChangeAccountWriter;
import fu.fa26.sep490.g58.otcms_be.auth.service.*;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Forces a non-cooperating writer to win after the availability check, with no production hook. */
@SpringBootTest
@LocalPostgresTest
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
@ExtendWith(OutputCaptureExtension.class)
class Kan29EmailChangeCollisionPostgresIntegrationTest {
    static final String SCHEMA = "kan29_collision_" + UUID.randomUUID().toString().replace("-", "");

    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        String ephemeral = UUID.randomUUID().toString() + UUID.randomUUID();
        r.add("spring.flyway.schemas", () -> SCHEMA);
        r.add("spring.flyway.default-schema", () -> SCHEMA);
        r.add("spring.datasource.hikari.schema", () -> SCHEMA);
        r.add("app.auth.session-security.jwt-secret", () -> ephemeral);
        r.add("app.auth.rate-limit-hmac-secret", () -> ephemeral);
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthSessionService sessions;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean EmailDeliveryService delivery;
    @MockitoSpyBean EmailChangeAccountWriter writer;

    @Test void postPrecheckUniqueCollisionRollsBackWithoutLoggingTarget(CapturedOutput output) throws Exception {
        UUID patient = account(), competitor = account();
        String oldEmail = jdbc.queryForObject("select normalized_email from accounts where id=?", String.class, patient);
        Instant originalUpdatedAt = jdbc.queryForObject("select updated_at from accounts where id=?", java.sql.Timestamp.class, patient).toInstant();
        String target = "collision-" + UUID.randomUUID() + "@example.test";
        String bearer = sessions.create(patient, "127.0.0.1", "kan29-collision").body().accessToken();
        var deliveredCode = new java.util.concurrent.atomic.AtomicReference<String>();
        doAnswer(invocation -> { deliveredCode.set(invocation.getArgument(1)); return null; })
                .when(delivery).sendVerificationCode(eq(target), anyString(), eq(patient));
        mvc.perform(post("/api/v1/me/email-change").with(csrf()).header("Authorization", "Bearer " + bearer)
                .contentType("application/json").content("{\"newEmail\":\"" + target + "\"}"))
                .andExpect(status().isAccepted());
        assertNotNull(deliveredCode.get());

        var reachedWriter = new CountDownLatch(1);
        var resumeWriter = new CountDownLatch(1);
        var collision = new java.util.concurrent.atomic.AtomicReference<fu.fa26.sep490.g58.otcms_be.auth.exception.EmailChangeWriteException>();
        doAnswer(invocation -> {
            reachedWriter.countDown();
            if (!resumeWriter.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Collision fixture timed out");
            try { return invocation.callRealMethod(); }
            catch (fu.fa26.sep490.g58.otcms_be.auth.exception.EmailChangeWriteException failure) {
                collision.set(failure);
                throw failure;
            }
        }).when(writer).updateVerifiedEmail(any(AccountJpaEntity.class), eq(target), any(Instant.class));

        String correlation = UUID.randomUUID().toString();
        try (var pool = Executors.newSingleThreadExecutor()) {
            var response = pool.submit(() -> mvc.perform(post("/api/v1/me/email-change/verify").with(csrf())
                    .header("Authorization", "Bearer " + bearer).header("X-Correlation-ID", correlation)
                    .contentType("application/json").content("{\"code\":\"" + deliveredCode.get() + "\"}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("MSG-08"))
                    .andExpect(jsonPath("$.error.correlationId").value(correlation)).andReturn());
            try {
                assertTrue(reachedWriter.await(15, TimeUnit.SECONDS), "Verification must reach the final JDBC writer");
                new TransactionTemplate(transactions).executeWithoutResult(status ->
                        assertEquals(1, jdbc.update("update accounts set normalized_email=? where id=?", target, competitor)));
            } finally { resumeWriter.countDown(); }
            String body = response.get(20, TimeUnit.SECONDS).getResponse().getContentAsString();
            assertFalse(body.contains(target));
            assertFalse(body.contains("23505"));
            assertFalse(body.contains("normalized_email"));
        }

        assertEquals(oldEmail, jdbc.queryForObject("select normalized_email from accounts where id=?", String.class, patient));
        assertNotNull(collision.get(), "The JDBC writer must encounter the forced database collision");
        assertEquals(fu.fa26.sep490.g58.otcms_be.auth.exception.EmailChangeWriteException.Reason.COLLISION, collision.get().reason());
        assertNull(collision.get().getCause(), "Raw PostgreSQL diagnostics must not escape the scoped writer");
        assertNull(jdbc.queryForObject("select email_verified_at from accounts where id=?", java.sql.Timestamp.class, patient));
        assertEquals(originalUpdatedAt, jdbc.queryForObject("select updated_at from accounts where id=?", java.sql.Timestamp.class, patient).toInstant());
        assertEquals(1, jdbc.queryForObject("select count(*) from accounts where normalized_email=?", Integer.class, target));
        assertEquals(competitor, jdbc.queryForObject("select id from accounts where normalized_email=?", UUID.class, target));
        assertEquals(1, jdbc.queryForObject("select count(*) from verification_codes where purpose='EMAIL_CHANGE' and account_id=? "
                + "and consumed_at is null and superseded_at is null and failed_attempts=0", Integer.class, patient));
        assertFalse(output.getAll().contains(target), "Literal target email must not appear in captured application/Hibernate logs");
        assertFalse(output.getAll().contains(deliveredCode.get()), "OTP must not appear in captured logs");
    }

    private UUID account() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into accounts(id,normalized_email,account_type,status,created_at,updated_at) "
                + "values (?,?,'PATIENT','ACTIVE',now(),now())", id, id + "@example.test");
        return id;
    }
}
