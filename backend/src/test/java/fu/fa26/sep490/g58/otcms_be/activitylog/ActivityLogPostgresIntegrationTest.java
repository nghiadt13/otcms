package fu.fa26.sep490.g58.otcms_be.activitylog;
import fu.fa26.sep490.g58.otcms_be.activitylog.repository.*;
import fu.fa26.sep490.g58.otcms_be.activitylog.exception.*;
import fu.fa26.sep490.g58.otcms_be.activitylog.service.impl.*;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@org.springframework.context.annotation.Import(fu.fa26.sep490.g58.otcms_be.content.HttpContractCapture.class)
@SpringBootTest
@AutoConfigureMockMvc
@LocalPostgresTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ActivityLogPostgresIntegrationTest {
    private static final String TEST_SCHEMA="kan40_"+UUID.randomUUID().toString().replace("-", "");
    private static final String TEST_DATABASE_URL="jdbc:postgresql://127.0.0.1:" + System.getenv().getOrDefault("OTCMS_TEST_DB_PORT", "5432") + "/otcms_test_db";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired tools.jackson.databind.ObjectMapper json;
    @DynamicPropertySource
    static void isolateFlywayAndJpaInTemporarySchema(DynamicPropertyRegistry registry) {
        String username = System.getenv().getOrDefault("OTCMS_TEST_DB_USERNAME", "postgres");
        String password = System.getenv("OTCMS_TEST_DB_PASSWORD");
        if (password == null) {
            throw new IllegalStateException("OTCMS_TEST_DB_PASSWORD is required for the local ActivityLog integration test");
        }
        try (Connection connection = DriverManager.getConnection(TEST_DATABASE_URL, username, password);
        Statement statement = connection.createStatement()) {
            statement.execute("create schema " + TEST_SCHEMA);
        }
        catch (SQLException exception) {
            throw new IllegalStateException("Could not create the isolated ActivityLog test schema", exception);
        }
        registry.add("spring.datasource.url", () -> TEST_DATABASE_URL + "?currentSchema=" + TEST_SCHEMA);
        registry.add("spring.flyway.schemas", () -> TEST_SCHEMA);
        registry.add("spring.flyway.default-schema", () -> TEST_SCHEMA);
    }
    @org.junit.jupiter.api.AfterAll
    static void dropTemporarySchema() throws SQLException {
        String username = System.getenv().getOrDefault("OTCMS_TEST_DB_USERNAME", "postgres");
        String password = System.getenv("OTCMS_TEST_DB_PASSWORD");
        if (password == null) {
            return;
        }
        try (Connection connection = DriverManager.getConnection(TEST_DATABASE_URL, username, password);
        Statement statement = connection.createStatement()) {
            statement.execute("drop schema if exists " + TEST_SCHEMA + " cascade");
        }
    }
    @Test void catalogCreateAndUpdateAreCommittedWithSafeSearchableAuditRows() throws Exception {
        var auth=actor();
        String body="""
                {"name":"KAN40 audit service","type":"EXAMINATION","price":200000,"durationMinutes":20,"bufferMinutes":0,"onlineBookable":true,"indicators":[]}
                """;
        String result=mvc.perform(post("/api/v1/admin/clinic-services").with(auth).with(csrf()).contentType("application/json").content(body))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id=json.readTree(result).get("id").asText();
        mvc.perform(put("/api/v1/admin/clinic-services/"+id).with(auth).with(csrf()).contentType("application/json")
        .content(body.replace("200000","250000"))).andExpect(status().isOk());
        assertEquals(2,jdbc.queryForObject("select count(*) from service_audits where service_id=?",Integer.class,UUID.fromString(id)));
        String list=mvc.perform(get("/api/v1/admin/activity-logs").param("action","SERVICE").param("record","KAN40 audit service").with(auth))
        .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(2))
        .andExpect(jsonPath("$.items[0].action").value("UPDATE_SERVICE"))
        .andExpect(jsonPath("$.items[0].allowedActions[0]").value("view"))
        .andReturn().getResponse().getContentAsString();
        String auditId=json.readTree(list).get("items").get(0).get("id").asText();
        mvc.perform(get("/api/v1/admin/activity-logs/"+auditId).with(auth)).andExpect(status().isOk())
        .andExpect(jsonPath("$.recordCode").value(id)).andExpect(jsonPath("$.beforeState").doesNotExist())
        .andExpect(jsonPath("$.afterState").doesNotExist());
    }
    @Test void creatingRoomProducesVisibleRoomEventAndSearchIsLiteral() throws Exception {
        var auth=actor();
        String code="K40_"+UUID.randomUUID().toString().substring(0,8);
        String service=mvc.perform(post("/api/v1/admin/clinic-services").with(auth).with(csrf()).contentType("application/json")
        .content("""
                {"name":"KAN40 room service","type":"EXAMINATION","price":200000,"durationMinutes":20,"bufferMinutes":0,"onlineBookable":true,"indicators":[]}
                """)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String serviceId=json.readTree(service).get("id").asText();
        mvc.perform(post("/api/v1/admin/visit-rooms").with(auth).with(csrf()).contentType("application/json")
        .content("{\"roomCode\":\""+code+"\",\"displayLabel\":\"Audit test room\",\"type\":\"EXAMINATION\",\"capabilities\":[],\"serviceIds\":[\""+serviceId+"\"]}"))
        .andExpect(status().isCreated());
        mvc.perform(get("/api/v1/admin/activity-logs").param("action","ROOM").param("record",code).with(auth))
        .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].action").value("CREATE_ROOM"));
        mvc.perform(get("/api/v1/admin/activity-logs").param("record","%' OR 1=1 --").with(auth))
        .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0));
    }
    @Test void invalidDateRangeUsesMessage54AndMissingCompositeIdUsesMessage50() throws Exception {
        var auth=actor();
        mvc.perform(get("/api/v1/admin/activity-logs").param("from","2026-11-07").param("to","2026-11-01").with(auth))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("MSG-54"));
        mvc.perform(get("/api/v1/admin/activity-logs/service:"+UUID.randomUUID()).with(auth))
        .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("MSG-50"));
    }
    @Test void recordedAccountAndDiagnosticActionsAreVisibleWithoutPrivateMetadata() throws Exception {
        var auth = actor();
        UUID actorId = UUID.randomUUID(), target = UUID.randomUUID(), audit = UUID.randomUUID(), diagnostic = UUID.randomUUID();
        jdbc.update("insert into accounts(id,normalized_email,username,account_type,status,created_at,updated_at) values(?,?,?,'ADMIN','ACTIVE',now(),now())",
                actorId, actorId + "@audit.test", "recorded-actor-" + actorId);
        jdbc.update("insert into account_management_audits(id,account_id,actor_account_id,action,occurred_at,correlation_id) values(?,?,?,'ACTIVATE',now(),'uc67')",
                audit, actorId, actorId);
        jdbc.update("insert into diagnostic_audit_events(id,actor_account_id,visit_id,action,outcome,correlation_id,metadata) values(?,?,?,'SAVE_FINDINGS','SUCCESS','uc67','{\"findings\":\"PRIVATE-PAYLOAD\"}'::jsonb)",
                diagnostic, actorId, target);
        mvc.perform(get("/api/v1/admin/activity-logs").param("action", "ACCOUNT").param("record", actorId.toString()).with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(get("/api/v1/admin/activity-logs/diagnostic:" + diagnostic).with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recordCode").value(target.toString()))
                .andExpect(jsonPath("$.actorUsername").value("recorded-actor-" + actorId))
                .andExpect(jsonPath("$.action").value("SAVE_FINDINGS:SUCCESS"))
                .andExpect(jsonPath("$.metadata").doesNotExist()).andExpect(jsonPath("$.payload").doesNotExist());
        mvc.perform(get("/api/v1/admin/activity-logs").param("action", "DIAGNOSTIC").param("record", target.toString()).with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1));
    }

    private RequestPostProcessor actor() {
        UUID accountId=UUID.randomUUID();
        jdbc.update("insert into accounts(id,normalized_email,username,account_type,status,created_at,updated_at) values(?,?,?,'ADMIN','ACTIVE',now(),now())",
        accountId,accountId+"@kan40.test","admin_"+accountId);
        var principal=new AuthenticatedAccount(accountId,fu.fa26.sep490.g58.otcms_be.common.security.AccountRole.ADMIN,UUID.randomUUID());
        return authentication(new UsernamePasswordAuthenticationToken(principal,null,List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }
}
