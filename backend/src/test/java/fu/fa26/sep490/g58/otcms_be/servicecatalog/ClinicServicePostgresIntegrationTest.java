package fu.fa26.sep490.g58.otcms_be.servicecatalog;

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

import javax.sql.DataSource;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@LocalPostgresTest
class ClinicServicePostgresIntegrationTest {
    private final List<UUID> actorAccounts = new ArrayList<>();
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;

    @AfterEach void cleanup() {
        jdbc.execute("alter table service_catalog_management_audits disable trigger tr_service_catalog_audits_immutable");
        try {
            jdbc.update("delete from service_catalog_management_audits where service_id in (select id from clinic_services where name like 'T010 %')");
            jdbc.update("delete from clinic_services where name like 'T010 %'");
            for (UUID accountId : actorAccounts) jdbc.update("delete from accounts where id = ?", accountId);
            actorAccounts.clear();
        } finally {
            jdbc.execute("alter table service_catalog_management_audits enable trigger tr_service_catalog_audits_immutable");
        }
    }

    @Test
    void cleanSchemaMigratesThroughV004() {
        String schema = "t010_clean_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("create schema " + schema);
        try {
            Flyway flyway = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                    .locations("classpath:db/migration").target("4").load();
            assertEquals(4, flyway.migrate().migrationsExecuted);
            assertEquals(1, jdbc.queryForObject("select count(*) from information_schema.tables where table_schema = ? and table_name = 'clinic_services'", Integer.class, schema));
            assertEquals(1, jdbc.queryForObject("select count(*) from information_schema.tables where table_schema = ? and table_name = 'doctor_service_assignments'", Integer.class, schema));
        } finally {
            jdbc.execute("drop schema if exists " + schema + " cascade");
        }
    }

    @Test
    void publicCatalogOmitsInactiveAndNonOnlineServices() throws Exception {
        jdbc.update("insert into clinic_services (id,name,duration_minutes,buffer_minutes,online_bookable,active) values (?,?,?,?,?,?)",
                UUID.randomUUID(), "T010 online", 30, 5, true, true);
        jdbc.update("insert into clinic_services (id,name,duration_minutes,buffer_minutes,online_bookable,active) values (?,?,?,?,?,?)",
                UUID.randomUUID(), "T010 staff only", 30, 5, false, true);
        jdbc.update("insert into clinic_services (id,name,duration_minutes,buffer_minutes,online_bookable,active) values (?,?,?,?,?,?)",
                UUID.randomUUID(), "T010 inactive online", 30, 5, true, false);
        mvc.perform(get("/api/v1/clinic-services").param("size", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[?(@.name == 'T010 online')]").exists())
                .andExpect(jsonPath("$.items[?(@.name == 'T010 staff only')]").doesNotExist())
                .andExpect(jsonPath("$.items[?(@.name == 'T010 inactive online')]").doesNotExist());
    }

    @Test
    void staffCatalogReturnsEveryActiveServiceAndOmitsInactiveServices() throws Exception {
        jdbc.update("insert into clinic_services (id,name,duration_minutes,buffer_minutes,online_bookable,active) values (?,?,?,?,?,?)",
                UUID.randomUUID(), "T010 staff online", 30, 5, true, true);
        jdbc.update("insert into clinic_services (id,name,duration_minutes,buffer_minutes,online_bookable,active) values (?,?,?,?,?,?)",
                UUID.randomUUID(), "T010 staff only", 30, 5, false, true);
        jdbc.update("insert into clinic_services (id,name,duration_minutes,buffer_minutes,online_bookable,active) values (?,?,?,?,?,?)",
                UUID.randomUUID(), "T010 staff inactive", 30, 5, true, false);
        mvc.perform(get("/api/v1/staff/clinic-services").with(actor(AccountType.RECEPTIONIST)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[?(@.name == 'T010 staff online')]").exists())
                .andExpect(jsonPath("$.items[?(@.name == 'T010 staff only')]").exists())
                .andExpect(jsonPath("$.items[?(@.name == 'T010 staff inactive')]").doesNotExist());
    }

    @Test
    void adminDeactivationPreservesServiceRowAndWritesAudit() throws Exception {
        String response = mvc.perform(post("/api/v1/admin/clinic-services").with(actor(AccountType.ADMIN)).with(csrf()).contentType("application/json")
                        .content("{\"name\":\"T010 diagnostic\",\"description\":\"Test service\",\"durationMinutes\":40,\"bufferMinutes\":10,\"onlineBookable\":true}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.active").value(true)).andReturn().getResponse().getContentAsString();
        String id = response.replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");
        mvc.perform(delete("/api/v1/admin/clinic-services/" + id).with(actor(AccountType.ADMIN)).with(csrf()))
                .andExpect(status().isNoContent());
        assertEquals(1, jdbc.queryForObject("select count(*) from clinic_services where id = ? and active = false", Integer.class, UUID.fromString(id)));
        assertEquals(1, jdbc.queryForObject("select count(*) from service_catalog_management_audits where service_id = ? and action = 'DEACTIVATED'", Integer.class, UUID.fromString(id)));

        jdbc.update("update clinic_services set active = false where id = ?", UUID.fromString(id));
        mvc.perform(post("/api/v1/admin/clinic-services/" + id + "/activation").with(actor(AccountType.ADMIN)).with(csrf()))
                .andExpect(status().isNoContent());
        assertEquals(1, jdbc.queryForObject("select count(*) from clinic_services where id = ? and active = true", Integer.class, UUID.fromString(id)));
    }

    @Test
    void receptionistCannotManageServices() throws Exception {
        mvc.perform(post("/api/v1/admin/clinic-services").with(actor(AccountType.RECEPTIONIST)).with(csrf()).contentType("application/json")
                .content("{\"name\":\"T010 denied\",\"durationMinutes\":30,\"bufferMinutes\":0,\"onlineBookable\":false}"))
                .andExpect(status().isForbidden());
    }

    private RequestPostProcessor actor(AccountType type) {
        UUID accountId = UUID.randomUUID();
        actorAccounts.add(accountId);
        jdbc.update("insert into accounts (id, normalized_email, account_type, status, created_at, updated_at) values (?, ?, ?, 'ACTIVE', now(), now())",
                accountId, accountId + "@t010.test", type.name());
        var principal = new AuthenticatedAccount(accountId, fu.fa26.sep490.g58.otcms_be.common.security.AccountRole.valueOf(type.name()), UUID.randomUUID());
        return authentication(new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + type.name()))));
    }
}
