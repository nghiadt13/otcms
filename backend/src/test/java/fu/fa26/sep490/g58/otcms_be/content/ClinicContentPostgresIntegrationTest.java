package fu.fa26.sep490.g58.otcms_be.content;
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


@SpringBootTest
@AutoConfigureMockMvc
@LocalPostgresTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ClinicContentPostgresIntegrationTest {
    private static final String TEST_SCHEMA="kan41_"+UUID.randomUUID().toString().replace("-", "");
    private static final String TEST_DATABASE_URL="jdbc:postgresql://127.0.0.1:5432/otcms_test_db";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired tools.jackson.databind.ObjectMapper json;
    @DynamicPropertySource
    static void isolateFlywayAndJpaInTemporarySchema(DynamicPropertyRegistry registry) {
        String username = System.getenv().getOrDefault("OTCMS_TEST_DB_USERNAME", "postgres");
        String password = System.getenv("OTCMS_TEST_DB_PASSWORD");
        if (password == null) {
            throw new IllegalStateException("OTCMS_TEST_DB_PASSWORD is required for the local ClinicContent integration test");
        }
        try (Connection connection = DriverManager.getConnection(TEST_DATABASE_URL, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("create schema " + TEST_SCHEMA);
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not create the isolated ClinicContent test schema", exception);
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


    @Test void articleSurvivesReloadAndPublicHomepageReadsPublishedDataOnly() throws Exception {
        var auth=actor();
        var response=mvc.perform(post("/api/v1/admin/website-contents").with(auth).with(csrf()).contentType("application/json")
                .content("""
                {"type":"HEALTH_ARTICLE","title":"Chăm sóc sau chấn thương","body":"Nội dung **an toàn**","imageUrl":null}
                """)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var payload=json.readTree(response); String id=payload.get("id").asText(); String publishedAt=payload.get("publishedAt").asText();
        assertEquals(1,jdbc.queryForObject("select count(*) from website_contents where id=?",Integer.class,UUID.fromString(id)));
        mvc.perform(get("/api/v1/admin/website-contents/"+id).with(auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("Nội dung **an toàn**"));
        mvc.perform(get("/api/v1/public/website-content")).andExpect(status().isOk())
                .andExpect(jsonPath("$.articles[?(@.id == '"+id+"')]").exists());
        mvc.perform(get("/api/v1/public/articles/"+id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("Nội dung **an toàn**"));
        mvc.perform(put("/api/v1/admin/website-contents/"+id).with(auth).with(csrf()).contentType("application/json")
                .content("""
                {"title":"Nội dung đã sửa","body":"Cập nhật","imageUrl":null}
                """)).andExpect(status().isOk()).andExpect(jsonPath("$.publishedAt").value(publishedAt));
        assertEquals(1L,jdbc.queryForObject("select version from website_contents where id=?",Long.class,UUID.fromString(id)));
        assertEquals(2,jdbc.queryForObject("select count(*) from content_audits where record_id=?",Integer.class,UUID.fromString(id)));
        jdbc.update("update website_contents set status='DRAFT' where id=?",UUID.fromString(id));
        mvc.perform(get("/api/v1/public/articles/"+id)).andExpect(status().isNotFound());
    }
    @Test void structuredClinicContactIsStoredAndReturnedToPublic() throws Exception {
        mvc.perform(post("/api/v1/admin/website-contents").with(actor()).with(csrf()).contentType("application/json")
                .content("""
                {"type":"CLINIC_INFO","title":"Liên hệ","body":{"clinicName":"Phòng khám kiểm thử","address":"Địa chỉ kiểm thử","hotline":"0900000000","email":"info@clinic.test","openingHours":["Thứ hai: 8–17"]}}
                """)).andExpect(status().isCreated());
        mvc.perform(get("/api/v1/public/website-content")).andExpect(status().isOk())
                .andExpect(jsonPath("$.contact.clinicName").value("Phòng khám kiểm thử"))
                .andExpect(jsonPath("$.contact.openingHours[0]").value("Thứ hai: 8–17"));
    }
    @Test void publicNoticePersistsButStaffNoticeCannotBeReadPubliclyAndUnavailableDeliveryDoesNotWrite() throws Exception {
        var auth=actor();
        mvc.perform(post("/api/v1/admin/announcements").with(auth).with(csrf()).contentType("application/json")
                .content("""
                {"title":"Public notice","body":"Open Monday","audience":"PUBLIC","channels":["IN_APP"]}
                """)).andExpect(status().isCreated());
        UUID id=UUID.randomUUID();
        jdbc.update("insert into announcements(id,title,body,audience,recipient_role,published_at,created_by,version) values(?,?,?,'STAFF','RECEPTIONIST',now(),?,0)",
                id,"Staff only","Private body",lastActor);
        mvc.perform(get("/api/v1/public/announcements")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '"+id+"')]").doesNotExist());
        mvc.perform(get("/api/v1/public/announcements/"+id)).andExpect(status().isNotFound());
        int before=jdbc.queryForObject("select count(*) from announcements",Integer.class);
        mvc.perform(post("/api/v1/admin/announcements").with(auth).with(csrf()).contentType("application/json")
                .content("""
                {"title":"Private request","body":"Body","audience":"PATIENT","channels":["IN_APP"]}
                """)).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error.code").value("MSG-04"));
        assertEquals(before,jdbc.queryForObject("select count(*) from announcements",Integer.class));
    }
    @Test void uploadStoresImageAndServesTheExactBytesWithSafeMimeType() throws Exception {
        byte[] png=java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aVZcAAAAASUVORK5CYII=");
        var auth=actor(); var file=new org.springframework.mock.web.MockMultipartFile("file","cover.png","image/png",png);
        var response=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/admin/website-contents/images")
                .file(file).with(auth).with(csrf())).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String url=json.readTree(response).get("url").asText();
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().contentType("image/png")).andExpect(content().bytes(png));
        var fake=new org.springframework.mock.web.MockMultipartFile("file","fake.png","image/png","<html>not an image</html>".getBytes());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/admin/website-contents/images")
                .file(fake).with(auth).with(csrf())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("MSG-69"));
    }
    private UUID lastActor;
    private RequestPostProcessor actor() {
        UUID accountId=UUID.randomUUID(); lastActor=accountId;
        jdbc.update("insert into accounts(id,normalized_email,account_type,status,created_at,updated_at) values(?,?,'ADMIN','ACTIVE',now(),now())",accountId,accountId+"@kan41.test");
        var principal=new AuthenticatedAccount(accountId,fu.fa26.sep490.g58.otcms_be.common.security.AccountRole.ADMIN,UUID.randomUUID());
        return authentication(new UsernamePasswordAuthenticationToken(principal,null,List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }
}
