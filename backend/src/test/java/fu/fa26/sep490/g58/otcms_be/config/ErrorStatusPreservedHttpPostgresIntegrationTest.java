package fu.fa26.sep490.g58.otcms_be.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import fu.fa26.sep490.g58.otcms_be.auth.service.AuthSessionService;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

/**
 * IT-BUG-23..29: a real servlet container turns every {@code response.sendError(...)} (access denied, CSRF failure,
 * unhandled validation exception) into an ERROR dispatch to {@code /error}. That dispatch was authenticated like any
 * other request, so the client always saw an empty 401 instead of the real 400/403/500. MockMvc never performs the
 * error dispatch, so only a real HTTP request can prove the status survives.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@LocalPostgresTest
class ErrorStatusPreservedHttpPostgresIntegrationTest {
    static final String SCHEMA = "errstatus_http_" + UUID.randomUUID().toString().replace("-", "");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String ephemeral = UUID.randomUUID().toString() + UUID.randomUUID();
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.datasource.hikari.schema", () -> SCHEMA);
        registry.add("app.auth.session-security.jwt-secret", () -> ephemeral);
        registry.add("app.auth.rate-limit-hmac-secret", () -> ephemeral);
    }

    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthSessionService sessions;
    final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
    final HttpClient client = HttpClient.newHttpClient();
    String patientToken;
    String adminToken;
    String csrf;

    @BeforeAll
    static void noop() { }

    private void signIn() throws Exception {
        if (patientToken != null) {
            return;
        }
        UUID account = UUID.randomUUID();
        jdbc.update("insert into accounts(id,normalized_email,account_type,status,created_at,updated_at) "
                + "values (?,?,'PATIENT','ACTIVE',now(),now())", account, account + "@example.test");
        patientToken = sessions.create(account, "127.0.0.1", "errstatus-http").body().accessToken();
        UUID admin = UUID.randomUUID();
        jdbc.update("insert into accounts(id,normalized_email,account_type,status,created_at,updated_at) "
                + "values (?,?,'ADMIN','ACTIVE',now(),now())", admin, admin + "@example.test");
        adminToken = sessions.create(admin, "127.0.0.1", "errstatus-http").body().accessToken();
        var response = client.send(HttpRequest.newBuilder(uri("/api/auth/csrf")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        csrf = json.readTree(response.body()).path("csrfToken").asString();
        assertFalse(csrf.isBlank());
    }

    private HttpResponse<String> call(String method, String path, boolean authenticated, boolean withCsrf,
            String idempotencyKey, String body) throws Exception {
        return callWith(method, path, authenticated ? "patient" : null, withCsrf, idempotencyKey, body);
    }

    private HttpResponse<String> callWith(String method, String path, String as, boolean withCsrf,
            String idempotencyKey, String body) throws Exception {
        signIn();
        boolean authenticated = as != null;
        var builder = HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json");
        if (authenticated) {
            builder.header("Authorization", "Bearer " + ("admin".equals(as) ? adminToken : patientToken));
        }
        if (withCsrf) {
            builder.header("Cookie", "XSRF-TOKEN=" + csrf).header("X-CSRF-TOKEN", csrf);
        }
        if (idempotencyKey != null) {
            builder.header("Idempotency-Key", idempotencyKey);
        }
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** IT-BUG-24: a valid token with the wrong role is 403, not 401. */
    @Test
    void wrongRoleIsForbiddenNotUnauthorized() throws Exception {
        assertEquals(403, call("GET", "/api/v1/invoices/unpaid", true, false, null, null).statusCode());
        // and carries the standard error body, not Spring's default error page
        assertEquals("ACCESS_DENIED", json.readTree(call("GET", "/api/v1/invoices/unpaid", true, false, null, null).body()).path("error").path("code").asString());
    }

    /** Anonymous callers keep getting 401 after the ERROR dispatch is opened up. */
    @Test
    void anonymousStaysUnauthorized() throws Exception {
        assertEquals(401, call("GET", "/api/v1/invoices/unpaid", false, false, null, null).statusCode());
    }

    /** IT-BUG-25: refresh without CSRF is 403. */
    @Test
    void missingCsrfIsForbidden() throws Exception {
        assertEquals(403, call("POST", "/api/auth/refresh", false, false, null, null).statusCode());
    }

    /** IT-BUG-25: an invalid registration body is 400. */
    @Test
    void invalidRegistrationBodyIsBadRequest() throws Exception {
        assertEquals(400, call("POST", "/api/auth/patient/register", false, false, null, "{}").statusCode());
    }

    /** IT-BUG-23: optional from/to must not break the payments query. */
    @Test
    void paymentsWithoutDateRangeIsOk() throws Exception {
        assertEquals(200, callWith("GET", "/api/v1/payments?page=0&size=20", "admin", false, null, null).statusCode());
    }

    /** IT-BUG-02: out-of-range paging is a validation error, not an access-denied error. */
    @Test
    void invoicePagingOutOfRangeIsBadRequest() throws Exception {
        assertEquals(400, call("GET", "/api/v1/me/invoices?page=-1&size=51", true, false, null, null).statusCode());
    }

    /** IT-BUG-28: payment attempt without the Idempotency-Key header is 400. */
    @Test
    void missingIdempotencyKeyIsBadRequest() throws Exception {
        assertEquals(400, call("POST", "/api/v1/invoices/" + UUID.randomUUID() + "/payment-attempts", true, true, null,
                "{\"itemIds\":null,\"channel\":\"ONLINE\"}").statusCode());
    }

    /** IT-BUG-26: visit results paging violation is 400, not 500. */
    @Test
    void visitResultsPagingViolationIsBadRequest() throws Exception {
        assertEquals(400, call("GET", "/api/v1/visits/results?page=-1&size=0", true, false, null, null).statusCode());
    }

    /** IT-BUG-27: imaging history size above 100 is 400. */
    @Test
    void imagingHistoryOversizeIsBadRequest() throws Exception {
        assertEquals(400, call("GET", "/api/v1/imaging-history?page=0&size=101", true, false, null, null).statusCode());
    }

    /** IT-BUG-29: UC-51 lists a patient's own studies without a filter. */
    @Test
    void imagingHistoryWithoutFilterListsOwnStudies() throws Exception {
        assertEquals(200, call("GET", "/api/v1/imaging-history?page=0&size=20", true, false, null, null).statusCode());
    }

    /** IT-BUG-29: another patient's profile is access denied (403), not 404. */
    @Test
    void imagingHistoryOfForeignProfileIsForbidden() throws Exception {
        assertEquals(403, call("GET", "/api/v1/imaging-history?patientId=" + UUID.randomUUID(), true, false, null, null).statusCode());
    }

    URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }
}
