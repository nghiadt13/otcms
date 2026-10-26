package fu.fa26.sep490.g58.otcms_be.patient;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.patient.exception.CareRelationshipException;
import fu.fa26.sep490.g58.otcms_be.patient.exception.PatientAuthorizationDeniedException;
import fu.fa26.sep490.g58.otcms_be.patient.service.AuthorizePatientProfileService;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import javax.sql.DataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@LocalPostgresTest
@Transactional
class CareRelationshipT008PostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource datasource;
    @Autowired AuthorizePatientProfileService authorization;
    private final List<UUID> accounts = new ArrayList<>(), profiles = new ArrayList<>();

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void cleanAndV011UpgradeCreateRelationshipSchemaAndPreserveIdentity() {
        String schema = "t008_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("create schema " + schema);
        try {
            var config = Flyway.configure().dataSource(datasource).schemas(schema).defaultSchema(schema).locations("classpath:db/migration");
            assertEquals(13, config.target("11").load().migrate().migrationsExecuted);
            UUID account = UUID.randomUUID(), profile = UUID.randomUUID();
            jdbc.update("insert into " + schema + ".accounts(id,normalized_email,account_type,status,created_at,updated_at) values (?,?,'PATIENT','ACTIVE',now(),now())", account, account + "@example.test");
            jdbc.update("insert into " + schema + ".patient_profiles(id,account_id,full_name,phone_canonical,phone_owner_type,date_of_birth,gender) values (?,?,'T008 Existing','+84912345678','GUARDIAN','1990-01-01','OTHER')", profile, account);
            assertEquals(1, Flyway.configure().dataSource(datasource).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("12").load().migrate().migrationsExecuted);
            assertEquals(account, jdbc.queryForObject("select account_id from " + schema + ".patient_profiles where id = ?", UUID.class, profile));
            assertEquals(0, jdbc.queryForObject("select count(*) from " + schema + ".patient_care_relationships", Integer.class));
            UUID requester = UUID.randomUUID(), relation = UUID.randomUUID();
            jdbc.update("insert into " + schema + ".accounts(id,normalized_email,account_type,status,created_at,updated_at) values (?,?,'PATIENT','ACTIVE',now(),now())", requester, requester + "@example.test");
            jdbc.update("insert into " + schema + ".patient_care_relationships(id,requester_account_id,dependent_patient_profile_id,confirming_account_id,state,requested_at) values (?,?,?,?,'REQUESTED',now())", relation, requester, profile, account);
            jdbc.update("update " + schema + ".patient_care_relationships set state='CONFIRMED', confirmed_at=now() where id=?", relation);
            assertThrows(DataAccessException.class, () -> jdbc.update("update " + schema + ".patient_care_relationships set state='REQUESTED', confirmed_at=null where id=?", relation));
            jdbc.update("update " + schema + ".patient_care_relationships set state='REVOKED', revoked_at=now(), revoked_by_account_id=? where id=?", account, relation);
            assertThrows(DataAccessException.class, () -> jdbc.update("delete from " + schema + ".patient_care_relationships where id=?", relation));
            assertThrows(DataAccessException.class, () -> jdbc.update("update " + schema + ".patient_care_relationships set requested_at=now() where id=?", relation));
            jdbc.update("insert into " + schema + ".patient_care_relationships(id,requester_account_id,dependent_patient_profile_id,confirming_account_id,state,requested_at) values (?,?,?,?,'REQUESTED',now())", UUID.randomUUID(), requester, profile, account);
            assertEquals(2, jdbc.queryForObject("select count(*) from " + schema + ".patient_care_relationships", Integer.class));
        } finally { jdbc.execute("drop schema if exists " + schema + " cascade"); }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void cleanMigrationRunsAllTwelveVersions() {
        String schema = "t008_clean_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("create schema " + schema);
        try {
            assertEquals(14, Flyway.configure().dataSource(datasource).schemas(schema).defaultSchema(schema)
                    .locations("classpath:db/migration").target("12").load().migrate().migrationsExecuted);
        } finally { jdbc.execute("drop schema if exists " + schema + " cascade"); }
    }

    @Test
    void linkedProfileIsAuthorizedButOtherPatientProfilesAndDependentRoutesAreDenied() throws Exception {
        UUID requester = account(), dependent = account(), stranger = account();
        UUID own = profile(requester), target = profile(dependent);
        profile(stranger);
        assertEquals(AuthorizePatientProfileService.AuthorizationScope.SELF,
                authorization.authorize(requester, own).scope());
        UUID relationship = UUID.randomUUID();
        jdbc.update("insert into patient_caregivers(id,requester_account_id,dependent_patient_profile_id,confirming_account_id,state,requested_at,confirmed_at) values (?,?,?,?,'CONFIRMED',now(),now())",
                relationship, requester, target, dependent);
        assertThrows(PatientAuthorizationDeniedException.class, () -> authorization.authorize(requester, target));
        assertThrows(PatientAuthorizationDeniedException.class, () -> authorization.authorize(stranger, target));
        mvc.perform(post("/api/v1/care-relationships").with(actor(requester, AccountType.PATIENT)).with(csrf())
                .contentType("application/json").content("{\"dependentPatientProfileId\":\"" + target + "\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/care-relationships/" + relationship + "/confirm").with(actor(dependent, AccountType.PATIENT)).with(csrf()))
                .andExpect(status().isNotFound());
        assertEquals(1, jdbc.queryForObject("select count(*) from patient_profiles where id = ? and phone_owner_type = 'GUARDIAN'", Integer.class, target));
    }

    @Test
    void noAccountProfileRemainsReceptionistManagedAndDependentRoutesAreAbsent() throws Exception {
        UUID requester = account(); profile(requester);
        UUID noAccount = profile(null);
        mvc.perform(post("/api/v1/care-relationships").with(actor(requester, AccountType.PATIENT)).with(csrf())
                .contentType("application/json").content("{\"dependentPatientProfileId\":\"" + noAccount + "\"}"))
                .andExpect(status().isNotFound());
        for (AccountType role : AccountType.values()) if (role != AccountType.PATIENT) {
            mvc.perform(post("/api/v1/care-relationships").with(actor(requester, role)).with(csrf())
                    .contentType("application/json").content("{\"dependentPatientProfileId\":\"" + noAccount + "\"}"))
                    .andExpect(status().isNotFound());
        }
        assertThrows(PatientAuthorizationDeniedException.class, () -> authorization.authorize(requester, noAccount));
    }

    private UUID account() {
        UUID id = UUID.randomUUID(); accounts.add(id);
        jdbc.update("insert into accounts(id,normalized_email,account_type,status,created_at,updated_at) values (?,?,'PATIENT','ACTIVE',now(),now())", id, id + "@example.test");
        return id;
    }
    private UUID profile(UUID account) {
        UUID id = UUID.randomUUID(); profiles.add(id);
        jdbc.update("insert into patient_profiles(id,account_id,full_name,phone_canonical,phone_owner_type,date_of_birth,gender) values (?,?,'T008 Patient','+84912345678','GUARDIAN','1990-01-01','OTHER')", id, account);
        return id;
    }
    private RequestPostProcessor actor(UUID account, AccountType role) {
        return authentication(new UsernamePasswordAuthenticationToken(new AuthenticatedAccount(account, fu.fa26.sep490.g58.otcms_be.common.security.AccountRole.valueOf(role.name()), UUID.randomUUID()), null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }
}
