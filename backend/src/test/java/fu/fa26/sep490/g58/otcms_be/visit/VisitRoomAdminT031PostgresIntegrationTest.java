package fu.fa26.sep490.g58.otcms_be.visit;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@LocalPostgresTest
class VisitRoomAdminT031PostgresIntegrationTest {
    private static final String CORRELATION = "e0e95dc8-6933-4cc8-a745-caf298d59d5c";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private UUID admin;
    private UUID receptionist;

    @BeforeEach
    void setUp() {
        admin = account(AccountType.ADMIN, "admin");
        receptionist = account(AccountType.RECEPTIONIST, "receptionist");
    }

    @AfterEach
    void cleanUp() {
        String trigger = jdbc.queryForObject("select tgenabled::text from pg_trigger where tgrelid = 'audit_logs'::regclass and tgname = 'tr_audit_logs_immutable'", String.class);
        jdbc.execute("alter table audit_logs disable trigger tr_audit_logs_immutable");
        jdbc.execute("alter table audit_logs disable trigger tr_audit_logs_no_truncate");
        try {
            jdbc.update("delete from audit_logs where actor_account_id in (?, ?)", admin, receptionist);

            jdbc.update("delete from rooms where room_code like 'T031-ADMIN-%'");
            jdbc.update("delete from accounts where id in (?, ?)", admin, receptionist);
        } finally {
            jdbc.execute("alter table audit_logs enable trigger tr_audit_logs_immutable");
            jdbc.execute("alter table audit_logs enable trigger tr_audit_logs_no_truncate");
        }
        assertEquals("O", trigger);
    }

    @Test
    void adminCreatesListsDeactivatesAndReactivatesRoomWithImmutableAudit() throws Exception {
        mvc.perform(post("/api/v1/admin/visit-rooms").with(actor(admin, AccountRole.ADMIN)).with(csrf())
                        .header("X-Correlation-ID", CORRELATION).contentType("application/json")
                        .content("{\"roomCode\":\"t031-admin-exam\",\"displayLabel\":\"Exam Room\",\"capabilities\":[\"EXAM_ROOM\"]}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.roomCode").value("T031-ADMIN-EXAM"))
                .andExpect(jsonPath("$.active").value(true)).andExpect(jsonPath("$.capabilities[0]").value("EXAM_ROOM"));
        UUID room = jdbc.queryForObject("select id from rooms where room_code = 'T031-ADMIN-EXAM'", UUID.class);
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_logs where entity_id = ? and actor_account_id = ? and source = 'room_management_audits' and action = 'CREATE_ROOM' and before_state is null and after_state ->> 'active' = 'true' and correlation_id = ?", Integer.class, room, admin, CORRELATION));

        mvc.perform(get("/api/v1/admin/visit-rooms").with(actor(admin, AccountRole.ADMIN)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[?(@.roomCode == 'T031-ADMIN-EXAM')].active").value(true));
        mvc.perform(post("/api/v1/admin/visit-rooms").with(actor(admin, AccountRole.ADMIN)).with(csrf())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()).contentType("application/json")
                        .content("{\"roomCode\":\"T031-ADMIN-EXAM\",\"displayLabel\":\"Duplicate\",\"capabilities\":[\"EXAM_ROOM\"]}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("DUPLICATE_ROOM_CODE"));

        mvc.perform(delete("/api/v1/admin/visit-rooms/{roomId}", room).with(actor(admin, AccountRole.ADMIN)).with(csrf())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/admin/visit-rooms/{roomId}/activation", room).with(actor(admin, AccountRole.ADMIN)).with(csrf())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isNoContent());
        assertEquals(2, jdbc.queryForObject("select count(*) from audit_logs where entity_id = ? and source = 'room_management_audits' and action = 'CHANGE_ROOM_STATUS'", Integer.class, room));
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("delete from audit_logs where entity_id = ? and source = 'room_management_audits'", room));
    }

    @Test
    void nonAdminCannotViewOrMutateRooms() throws Exception {
        mvc.perform(get("/api/v1/admin/visit-rooms").with(actor(receptionist, AccountRole.RECEPTIONIST)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/visit-rooms").with(actor(receptionist, AccountRole.RECEPTIONIST)).with(csrf())
                        .header("X-Correlation-ID", CORRELATION).contentType("application/json")
                        .content("{\"roomCode\":\"T031-ADMIN-DENIED\",\"displayLabel\":\"Denied\",\"capabilities\":[\"EXAM_ROOM\"]}"))
                .andExpect(status().isForbidden());
        assertEquals(0, jdbc.queryForObject("select count(*) from rooms where room_code = 'T031-ADMIN-DENIED'", Integer.class));
    }

    @Test
    void staffReadReusesAdminProjectionAndDatabaseOrderingIncludingInactiveRooms() throws Exception {
        for (String suffix : List.of("B", "A")) {
            mvc.perform(post("/api/v1/admin/visit-rooms").with(actor(admin, AccountRole.ADMIN)).with(csrf())
                    .contentType("application/json").content("{\"roomCode\":\"T031-ADMIN-KAN25-" + suffix
                            + "\",\"displayLabel\":\"Room " + suffix + "\",\"capabilities\":[\"EXAM_ROOM\"]}"))
                    .andExpect(status().isCreated());
        }
        UUID inactive = jdbc.queryForObject("select id from rooms where room_code='T031-ADMIN-KAN25-B'", UUID.class);
        mvc.perform(delete("/api/v1/admin/visit-rooms/{roomId}", inactive).with(actor(admin, AccountRole.ADMIN)).with(csrf()))
                .andExpect(status().isNoContent());
        String staff = mvc.perform(get("/api/v1/staff/rooms").with(actor(receptionist, AccountRole.RECEPTIONIST)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String adminBody = mvc.perform(get("/api/v1/admin/visit-rooms").with(actor(admin, AccountRole.ADMIN)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var json = tools.jackson.databind.json.JsonMapper.builder().build();
        assertEquals(json.readTree(adminBody), json.readTree(staff));
        org.junit.jupiter.api.Assertions.assertTrue(staff.indexOf("T031-ADMIN-KAN25-A") < staff.indexOf("T031-ADMIN-KAN25-B"));
        mvc.perform(get("/api/v1/staff/rooms").with(actor(receptionist, AccountRole.RECEPTIONIST)))
                .andExpect(jsonPath("$.items[?(@.roomCode == 'T031-ADMIN-KAN25-B')].active").value(false));
    }

    private UUID account(AccountType type, String label) {
        UUID id = UUID.randomUUID();
        Instant created = Instant.parse("2026-10-01T00:00:00Z");
        jdbc.update("insert into accounts (id, normalized_email, account_type, status, created_at, updated_at) values (?, ?, ?, 'ACTIVE', ?, ?)",
                id, "t031-admin-" + label + "-" + id + "@example.test", type.name(), Timestamp.from(created), Timestamp.from(created));
        return id;
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor actor(UUID account, AccountRole role) {
        return authentication(new UsernamePasswordAuthenticationToken(new AuthenticatedAccount(account, role, null), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }
}
