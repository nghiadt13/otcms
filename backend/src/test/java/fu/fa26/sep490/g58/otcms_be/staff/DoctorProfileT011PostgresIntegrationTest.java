package fu.fa26.sep490.g58.otcms_be.staff;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffDirectoryQueryService;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@LocalPostgresTest
class DoctorProfileT011PostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired StaffDirectoryQueryService staffDirectory;

    @AfterEach void cleanup() {
        jdbc.update("delete from doctor_time_off where doctor_profile_id in (select id from doctor_profiles where work_contact like 't011-%')");
        jdbc.update("delete from doctor_shifts where doctor_profile_id in (select id from doctor_profiles where work_contact like 't011-%')");
        jdbc.update("delete from doctor_profiles where work_contact like 't011-%'");
        jdbc.update("delete from accounts where normalized_email like 't011-%'");
    }

    @Test
    void cleanSchemaMigratesDoctorAndScheduleOwnershipTables() {
        String schema = "t011_clean_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("create schema " + schema);
        try {
            Flyway flyway = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                    .locations("classpath:db/migration").target("6").load();
            assertEquals(8, flyway.migrate().migrationsExecuted);
            assertEquals(1, jdbc.queryForObject("select count(*) from information_schema.tables where table_schema = ? and table_name = 'doctor_profiles'", Integer.class, schema));
            assertEquals(1, jdbc.queryForObject("select count(*) from information_schema.tables where table_schema = ? and table_name = 'doctor_work_shifts'", Integer.class, schema));
            assertEquals(1, jdbc.queryForObject("select count(*) from information_schema.tables where table_schema = ? and table_name = 'doctor_operational_time_off'", Integer.class, schema));
            assertEquals(1, jdbc.queryForObject("select count(*) from pg_constraint c join pg_class t on t.oid = c.conrelid join pg_namespace n on n.oid = t.relnamespace where n.nspname = ? and c.conname = 'ex_doctor_work_shift_no_overlap'", Integer.class, schema));
            assertEquals(1, jdbc.queryForObject("select count(*) from pg_constraint c join pg_class t on t.oid = c.conrelid join pg_namespace n on n.oid = t.relnamespace where n.nspname = ? and c.conname = 'fk_doctor_service_assignments_doctor_profile'", Integer.class, schema));
        } finally {
            jdbc.execute("drop schema if exists " + schema + " cascade");
        }
    }

    @Test
    void adminLinksExistingDoctorAccountAndUnpublishedProfileStaysPrivate() throws Exception {
        UUID accountId = createAccount(AccountType.DOCTOR, "doctor", "TEMPORARILY_LOCKED");
        String response = mvc.perform(post("/api/v1/admin/doctor-profiles").with(actor(AccountType.ADMIN)).with(csrf())
                        .contentType("application/json").content("{\"accountId\":\"" + accountId + "\",\"fullName\":\"Dr T011 Example\",\"workContact\":\"t011-contact\",\"specialties\":\"Orthopedics\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.publiclyVisible").value(false))
                .andExpect(jsonPath("$.acceptingNewBookings").value(false)).andReturn().getResponse().getContentAsString();
        UUID profileId = UUID.fromString(response.replaceAll(".*\\\"doctorProfileId\\\":\\\"([^\\\"]+)\\\".*", "$1"));
        jdbc.update("update doctor_profiles set accepting_new_bookings = true where id = ?", profileId);

        mvc.perform(get("/api/v1/doctors")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.doctorProfileId == '" + profileId + "')]").doesNotExist());
        StaffDirectoryQueryService.DoctorBookingProfile eligibility = staffDirectory.findDoctorByAccount(accountId,
                Instant.parse("2026-11-01T09:00:00Z"), Instant.parse("2026-11-01T10:00:00Z"));
        assertNotNull(eligibility);
        assertEquals(profileId, eligibility.doctorProfileId());
        assertTrue(eligibility.inActiveDirectory());
        assertFalse(eligibility.publiclyVisible());
        assertTrue(eligibility.acceptingNewBookings());
        assertFalse(eligibility.coveredByWorkShift());
        assertFalse(eligibility.coveredByOperationalTimeOff());
    }

    @Test
    void adminCannotAttachNonDoctorAccount() throws Exception {
        UUID accountId = createAccount(AccountType.RECEPTIONIST, "receptionist");
        mvc.perform(post("/api/v1/admin/doctor-profiles").with(actor(AccountType.ADMIN)).with(csrf())
                        .contentType("application/json").content("{\"accountId\":\"" + accountId + "\",\"fullName\":\"Wrong role\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("DOCTOR_ACCOUNT_UNAVAILABLE"));
    }

    private UUID createAccount(AccountType type, String label) {
        return createAccount(type, label, "ACTIVE");
    }

    private UUID createAccount(AccountType type, String label, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into accounts (id, normalized_email, account_type, status, created_at, updated_at) values (?, ?, ?, ?, now(), now())",
                id, "t011-" + label + "-" + id + "@example.test", type.name(), status);
        return id;
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor actor(AccountType type) {
        AuthenticatedAccount principal = new AuthenticatedAccount(UUID.randomUUID(), fu.fa26.sep490.g58.otcms_be.common.security.AccountRole.valueOf(type.name()), UUID.randomUUID());
        return authentication(new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + type.name()))));
    }
}
