package fu.fa26.sep490.g58.otcms_be.appointment;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@LocalPostgresTest
@SpringBootTest
@AutoConfigureMockMvc
class AppointmentQueryT023PostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private UUID patientAccount, otherAccount, receptionist, doctorAccount, patient, otherPatient, doctor, service;
    private int preexistingAppointmentCount;

    @BeforeEach void setup() {
        service = UUID.randomUUID();
        patientAccount = account("PATIENT"); otherAccount = account("PATIENT"); receptionist = account("RECEPTIONIST"); doctorAccount = account("DOCTOR");
        patient = patient(patientAccount); otherPatient = patient(otherAccount);
        preexistingAppointmentCount = jdbc.queryForObject("select count(*) from appointments", Integer.class);
        jdbc.update("insert into clinic_services(id,name,duration_minutes,buffer_minutes,online_bookable,active) values (?, 'T023',30,0,true,true)", service);
        doctor = UUID.randomUUID();
        jdbc.update("insert into doctor_profiles(id,account_id,full_name,photo_url,work_contact,specialties,qualifications,biography,consultation_information,publicly_visible,accepting_new_bookings,in_active_directory) values (?,?,'T023 Doctor','','private','','','','',false,false,false)", doctor, doctorAccount);
        jdbc.update("insert into doctor_service_assignments(doctor_profile_id,service_id,active) values (?, ?, true)", doctor, service);
        appointment(patient, Instant.parse("2026-10-12T17:00:00Z"));
        appointment(patient, Instant.parse("2026-10-13T17:00:00Z"));
        appointment(otherPatient, Instant.parse("2026-10-13T19:00:00Z"));
    }

    @AfterEach void cleanup() {
        if (service != null) {
            jdbc.update("delete from appointments where service_id=?", service);
            jdbc.update("delete from doctor_service_assignments where service_id=?", service);
            jdbc.update("delete from clinic_services where id=?", service);
        }
        if (doctor != null) jdbc.update("delete from doctor_profiles where id=?", doctor);
        if (patient != null) jdbc.update("delete from patient_profiles where id=?", patient);
        if (otherPatient != null) jdbc.update("delete from patient_profiles where id=?", otherPatient);
        for (UUID id : new UUID[]{patientAccount, otherAccount, receptionist, doctorAccount}) if (id != null) jdbc.update("delete from accounts where id=?", id);
        service = null;
    }

    @Test void patientListIsScopedAndInclusiveDateFiltersAndPaginationAreStable() throws Exception {
        mvc.perform(get("/api/v1/appointments").with(actor(patientAccount, AccountType.PATIENT)).with(csrf()).param("from", "2026-10-13").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.items[0].purpose").value("private purpose"))
                .andExpect(jsonPath("$.items[0].patientProfileId").value(patient.toString()));
    }

    @Test void detailHidesOutOfScopeRecordsAndAdminIsForbidden() throws Exception {
        UUID other = jdbc.queryForObject("select id from appointments where patient_profile_id=? order by start_time desc limit 1", UUID.class, otherPatient);
        mvc.perform(get("/api/v1/appointments/{id}", other).with(actor(patientAccount, AccountType.PATIENT)).with(csrf()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("APPOINTMENT_NOT_FOUND"));
        mvc.perform(get("/api/v1/appointments").with(actor(patientAccount, AccountType.ADMIN)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test void detailReturnsApprovedDtoAndToDateIsInclusiveInClinicZone() throws Exception {
        UUID own = jdbc.queryForObject("select id from appointments where patient_profile_id=? and start_time=?", UUID.class,
                patient, Timestamp.from(Instant.parse("2026-10-12T17:00:00Z")));
        mvc.perform(get("/api/v1/appointments/{id}", own).with(actor(patientAccount, AccountType.PATIENT)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(own.toString()))
                .andExpect(jsonPath("$.purpose").value("private purpose"));
        mvc.perform(get("/api/v1/appointments").with(actor(receptionist, AccountType.RECEPTIONIST)).with(csrf()).param("to", "2026-10-13"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test void receptionistAndDoctorListsUseClinicAndCurrentDoctorScopes() throws Exception {
        mvc.perform(get("/api/v1/appointments").with(actor(receptionist, AccountType.RECEPTIONIST)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(preexistingAppointmentCount + 3));
        mvc.perform(get("/api/v1/appointments").with(actor(doctorAccount, AccountType.DOCTOR)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test void lockedDoctorGetsAccessDeniedWithoutChangingAppointments() throws Exception {
        jdbc.update("update accounts set status='TEMPORARILY_LOCKED' where id=?", doctorAccount);
        mvc.perform(get("/api/v1/appointments").with(actor(doctorAccount, AccountType.DOCTOR)).with(csrf()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
        org.junit.jupiter.api.Assertions.assertEquals(3, jdbc.queryForObject("select count(*) from appointments where service_id=?", Integer.class, service));
    }

    @Test void badDatesAndReversedRangeAreValidationErrors() throws Exception {
        mvc.perform(get("/api/v1/appointments").with(actor(receptionist, AccountType.RECEPTIONIST)).with(csrf()).param("from", "not-a-date"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        mvc.perform(get("/api/v1/appointments").with(actor(receptionist, AccountType.RECEPTIONIST)).with(csrf()).param("from", "2026-10-14").param("to", "2026-10-13"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    private UUID account(String type) {
        UUID id=UUID.randomUUID(); Instant now=Instant.parse("2026-10-01T00:00:00Z");
        jdbc.update("insert into accounts(id,normalized_email,account_type,status,created_at,updated_at) values(?,?,?,'ACTIVE',?,?)", id, "t023-"+id+"@example.test", type, Timestamp.from(now), Timestamp.from(now)); return id;
    }
    private UUID patient(UUID account) {
        UUID id=UUID.randomUUID(); jdbc.update("insert into patient_profiles(id,account_id,full_name,phone_canonical,phone_owner_type,date_of_birth,gender) values(?,?,? ,?,'PATIENT','1990-01-01','OTHER')", id, account, "T023 Patient", "+849"+UUID.randomUUID().toString().replace("-", "").substring(0,9)); return id;
    }
    private void appointment(UUID patientId, Instant start) {
        jdbc.update("insert into appointments(patient_profile_id,service_id,doctor_profile_id,status,booking_source,purpose,start_time,end_time,duration_minutes,buffer_minutes,created_by_account_id) values(?,?,?,'CONFIRMED','RECEPTIONIST_ASSISTED','private purpose',?,?,30,0,?)", patientId, service, doctor, Timestamp.from(start), Timestamp.from(start.plusSeconds(1800)), receptionist);
    }
    private RequestPostProcessor actor(UUID id, AccountType type) {
        return authentication(new UsernamePasswordAuthenticationToken(new AuthenticatedAccount(id,fu.fa26.sep490.g58.otcms_be.common.security.AccountRole.valueOf(type.name()),UUID.randomUUID()),null,List.of(new SimpleGrantedAuthority("ROLE_"+type.name()))));
    }
}
