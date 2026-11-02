package fu.fa26.sep490.g58.otcms_be.reporting;
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
class ReportingPostgresIntegrationTest {
    private static final String TEST_SCHEMA="kan43_"+UUID.randomUUID().toString().replace("-", "");
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


    @Autowired fu.fa26.sep490.g58.otcms_be.reporting.application.ReportingReader reader;
    @Test void aggregatesMatchTheMigratedDatabaseAndMissingSourcesAreExplicit() throws Exception {
        var p=fu.fa26.sep490.g58.otcms_be.reporting.domain.ReportingPeriod.parse("2026-11-01","2026-11-07");
        long expected=jdbc.queryForObject("select count(*) from appointments where start_time>=? and start_time<?", Long.class,java.sql.Timestamp.from(p.start()),java.sql.Timestamp.from(p.until()));
        assertEquals(expected,reader.appointmentCounts(p).values().stream().mapToLong(Long::longValue).sum());
        assertEquals(expected,reader.daily("APPOINTMENTS",p).stream().mapToLong(row->((Number)row.get("count")).longValue()).sum());
        // These queries must run against Flyway's actual table/column names, even when empty.
        reader.visitCounts(p);reader.services(p);
        mvc.perform(get("/api/v1/admin/dashboard?from=2026-11-01&to=2026-11-07").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.appointments.total.value").value(expected))
            .andExpect(jsonPath("$.payments.status").value("NO_SOURCE"))
            .andExpect(jsonPath("$.visits.waitingByRoomStatus").value("NO_SOURCE"));
    }
    @Test void nonemptyDailyReportUsesVietnamDatesAndExclusiveNextMidnight() throws Exception {
        var fixture=new org.springframework.core.io.ClassPathResource("reporting-fixtures.sql");
        jdbc.execute(fixture.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        var p=fu.fa26.sep490.g58.otcms_be.reporting.domain.ReportingPeriod.parse("2030-01-01","2030-01-02");
        assertEquals(2L,reader.appointmentCounts(p).get("BOOKED"));
        var rows=reader.daily("APPOINTMENTS",p);
        assertEquals(2,rows.size());assertEquals("2030-01-01",rows.get(0).get("date"));
        assertEquals("2030-01-02",rows.get(1).get("date"));assertEquals(1L,rows.get(1).get("count"));
        mvc.perform(post("/api/v1/admin/reports").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")).with(csrf()).contentType("application/json")
            .content("{\"subject\":\"APPOINTMENTS\",\"from\":\"2030-01-01\",\"to\":\"2030-01-02\",\"breakdown\":\"DATE\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.hasData").value(true)).andExpect(jsonPath("$.sections[0].totalRow.count").value(2));
    }
    @Test void ownerCanReadButOtherRolesCannotAndInvalidRangesUseMessage72() throws Exception {
        String path="/api/v1/admin/dashboard?from=2026-11-01&to=2026-11-07";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        for(String role:List.of("PATIENT","DOCTOR","RECEPTIONIST","IMAGING_TECHNICIAN"))
            mvc.perform(get(path).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("other").roles(role))).andExpect(status().isForbidden());
        var owner=org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("owner").roles("OWNER");
        mvc.perform(get(path).with(owner)).andExpect(status().isOk());
        for(String query:List.of("from=2026-11-07&to=2026-11-01","from=2026-01-01&to=2026-12-31","from=2026-02-30&to=2026-03-01","from=bad&to=2026-03-01"))
            mvc.perform(get("/api/v1/admin/dashboard?"+query).with(owner)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("MSG-72"));
        mvc.perform(post("/api/v1/admin/reports").with(owner).with(csrf()).contentType("application/json").content("{\"subject\":\"PAYMENTS\",\"from\":\"2026-11-01\",\"to\":\"2026-11-07\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("MSG-72"));
    }
    @Test void emptyReportIsReadyWithZeroTotalAndNoUnapprovedExports() throws Exception {
        var admin=org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN");
        mvc.perform(post("/api/v1/admin/reports").with(admin).with(csrf()).contentType("application/json").content("{\"subject\":\"APPOINTMENTS\",\"from\":\"1900-01-01\",\"to\":\"1900-01-07\",\"breakdown\":\"DATE\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.hasData").value(false)).andExpect(jsonPath("$.sections[0].totalRow.count").value(0)).andExpect(jsonPath("$.exportFormats[0]").value("pdf"));
        mvc.perform(get("/api/v1/admin/reports/export?format=xlsx&subject=APPOINTMENTS&from=1900-01-01&to=1900-01-07").with(admin)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("MSG-72"));
    }
    @Test void pdfIsARealUnicodeDocumentAndExportIsRoleProtected() throws Exception {
        String path="/api/v1/admin/reports/export?format=pdf&subject=APPOINTMENTS&from=1900-01-01&to=1900-01-07";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("patient").roles("PATIENT"))).andExpect(status().isForbidden());
        var bytes=mvc.perform(get(path).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("owner").roles("OWNER")))
            .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
            .andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsByteArray();
        try(var reader=new org.openpdf.text.pdf.PdfReader(bytes)) {
            String text=new org.openpdf.text.pdf.parser.PdfTextExtractor(reader).getTextFromPage(1);
            org.junit.jupiter.api.Assertions.assertTrue(text.contains("Lịch hẹn"));
            org.junit.jupiter.api.Assertions.assertTrue(text.contains("Tổng số: 0"));
        }
    }

}
