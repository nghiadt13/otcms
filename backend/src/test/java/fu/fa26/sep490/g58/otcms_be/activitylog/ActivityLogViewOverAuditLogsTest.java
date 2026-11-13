package fu.fa26.sep490.g58.otcms_be.activitylog;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import fu.fa26.sep490.g58.otcms_be.common.audit.AuditEntry;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditLogWriter;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@LocalPostgresTest
class ActivityLogViewOverAuditLogsTest {
    @Autowired AuditLogWriter writer;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired MockMvc mvc;

    @Test
    void adminCanListAndReadRoomAuditWithoutPrivateSnapshot() throws Exception {
        UUID room = UUID.randomUUID();
        String label = "P01_" + room;
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> writer.record(
                AuditEntry.builder("room_management_audits", "ROOM", "CREATE_ROOM", "ROOM")
                        .entityId(room).recordLabel(label).afterState(Map.of("private", "synthetic-detail"))
                        .metadata(Map.of("internal", "synthetic-metadata")).build()));
        String id = "room:" + jdbc.queryForObject("select id from audit_logs where record_label=?", UUID.class, label);
        var admin = actor(AccountRole.ADMIN);
        mvc.perform(get("/api/v1/admin/activity-logs").param("action", "ROOM").param("record", label).with(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(id))
                .andExpect(jsonPath("$.items[0].action").value("CREATE_ROOM"))
                .andExpect(jsonPath("$.items[0].recordLabel").value(label));
        mvc.perform(get("/api/v1/admin/activity-logs").param("record", "%' OR 1=1 --").with(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(get("/api/v1/admin/activity-logs/" + id).with(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recordCode").value(room.toString()))
                .andExpect(jsonPath("$.beforeState").doesNotExist()).andExpect(jsonPath("$.afterState").doesNotExist())
                .andExpect(jsonPath("$.metadata").doesNotExist());
        mvc.perform(get("/api/v1/admin/activity-logs/" + id).with(actor(AccountRole.OWNER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recordCode").value(room.toString()));
        mvc.perform(get("/api/v1/admin/activity-logs/" + id).with(actor(AccountRole.PATIENT)))
                .andExpect(status().isForbidden());
    }

    @Test
    void invisibleAuditCannotBeListedOrReadById() throws Exception {
        String label = "P01_hidden_" + UUID.randomUUID();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> writer.record(
                AuditEntry.builder("room_management_audits", "ROOM", "CREATE_ROOM", "ROOM")
                        .recordLabel(label).visibleInActivityLog(false).build()));
        String id = "room:" + jdbc.queryForObject("select id from audit_logs where record_label=?", UUID.class, label);
        mvc.perform(get("/api/v1/admin/activity-logs").param("record", label).with(actor(AccountRole.ADMIN)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(get("/api/v1/admin/activity-logs/" + id).with(actor(AccountRole.ADMIN)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("MSG-50"));
    }

    @Test
    @Transactional
    void visibleAuditWithNullLabelIsIncludedInAnUnfilteredList() throws Exception {
        UUID actor = UUID.randomUUID();
        jdbc.update("insert into accounts(id,normalized_email,username,account_type,status,created_at,updated_at) "
                + "values(?,?,?,'ADMIN','ACTIVE',now(),now())", actor, actor + "@phase01.test", "p01_" + actor);
        writer.record(AuditEntry.builder("room_management_audits", "ROOM", "CREATE_ROOM", "ROOM")
                .actorAccountId(actor).build());
        mvc.perform(get("/api/v1/admin/activity-logs").param("actor", "p01_" + actor).with(actor(AccountRole.ADMIN)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    @Transactional
    void paymentHoldsStayHiddenFromListAndDetailUntilConfirmed() throws Exception {
        UUID patientAccount = UUID.randomUUID(), doctorAccount = UUID.randomUUID();
        UUID patient = UUID.randomUUID(), doctor = UUID.randomUUID(), service = UUID.randomUUID();
        jdbc.update("insert into accounts(id,normalized_email,account_type,status,created_at,updated_at) "
                + "values(?,?,'PATIENT','ACTIVE',now(),now()),(?,?,'DOCTOR','ACTIVE',now(),now())",
                patientAccount, patientAccount + "@phase01.test", doctorAccount, doctorAccount + "@phase01.test");
        jdbc.update("insert into patient_profiles(id,account_id,full_name,phone_canonical,phone_owner_type,date_of_birth,gender) "
                + "values(?,?,'Synthetic patient',?,'PATIENT','1990-01-01','OTHER')", patient, patientAccount,
                "+848" + patient.toString().replace("-", "").substring(0, 9));
        jdbc.update("insert into staff_profiles(is_doctor, id,account_id,display_name,photo_url,work_contact,specialties,qualifications,"
                + "biography,consultation_information,publicly_visible,accepting_new_bookings,in_active_directory) "
                + "values(true, ?,?,'Synthetic doctor','','','','','','',true,true,true)", doctor, doctorAccount);
        jdbc.update("insert into clinic_services(id,name,duration_minutes,buffer_minutes,online_bookable,active) "
                + "values(?,'Synthetic service',30,5,true,true)", service);
        jdbc.update("insert into doctor_services(doctor_profile_id,service_id,active) values(?,?,true)", doctor, service);
        for (String state : List.of("AWAITING_PAYMENT", "PAYMENT_EXPIRED", "CONFIRMED")) {
            UUID appointment = UUID.randomUUID();
            String label = "P01_hold_" + appointment;
            jdbc.update("insert into appointments(id,patient_profile_id,service_id,doctor_profile_id,status,booking_source,"
                    + "purpose,start_time,end_time,duration_minutes,buffer_minutes,created_by_account_id,payment_deadline) "
                    + "values(?,?,?,?,?,'PATIENT_ONLINE','Synthetic hold',now()+interval '1 day',"
                    + "now()+interval '1 day 30 minutes',30,5,?,now()+interval '15 minutes')",
                    appointment, patient, service, doctor, state, patientAccount);
            writer.record(AuditEntry.builder("appointment_audits", "APPOINTMENT", "CREATED", "APPOINTMENT")
                    .entityId(appointment).recordLabel(label).build());
            String id = "appointment:" + jdbc.queryForObject("select id from audit_logs where record_label=?", UUID.class, label);
            boolean visible = state.equals("CONFIRMED");
            mvc.perform(get("/api/v1/admin/activity-logs").param("record", label).with(actor(AccountRole.ADMIN)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(visible ? 1 : 0));
            mvc.perform(get("/api/v1/admin/activity-logs/" + id).with(actor(AccountRole.ADMIN)))
                    .andExpect(visible ? status().isOk() : status().isNotFound());
            // Avoid overlap with the next synthetic booking in this rollback-only fixture.
            jdbc.update("update appointments set status='CANCELLED' where id=?", appointment);
        }
    }

    private RequestPostProcessor actor(AccountRole role) {
        var principal = new AuthenticatedAccount(UUID.randomUUID(), role, UUID.randomUUID());
        return authentication(new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }
}
