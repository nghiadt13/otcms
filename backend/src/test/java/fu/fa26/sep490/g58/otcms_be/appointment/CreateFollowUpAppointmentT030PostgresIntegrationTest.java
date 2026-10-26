package fu.fa26.sep490.g58.otcms_be.appointment;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@LocalPostgresTest
@Import(CreateFollowUpAppointmentT030PostgresIntegrationTest.FixedClockConfiguration.class)
class CreateFollowUpAppointmentT030PostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-14T05:00:00Z");
    private static final Instant SLOT = Instant.parse("2026-10-16T03:00:00Z");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    private UUID account;
    private UUID patient;
    private UUID doctor;
    private UUID service;
    private UUID visit;
    private String correlation;

    @BeforeEach
    void setUp() {
        account = account();
        patient = UUID.randomUUID();
        doctor = UUID.randomUUID();
        service = UUID.randomUUID();
        visit = UUID.randomUUID();
        correlation = UUID.randomUUID().toString();
        jdbc.update("insert into patient_profiles (id, account_id, full_name, phone_canonical, phone_owner_type, date_of_birth, gender) values (?, null, 'T030 Patient', ?, 'PATIENT', '1990-01-01', 'OTHER')",
                patient, "+849" + UUID.randomUUID().toString().replace("-", "").substring(0, 9));
        jdbc.update("insert into doctor_profiles (id, account_id, full_name, photo_url, work_contact, specialties, qualifications, biography, consultation_information, publicly_visible, accepting_new_bookings, in_active_directory) values (?, ?, 'T030 Doctor', '', 'private', '', '', '', '', true, true, true)",
                doctor, account);
        jdbc.update("insert into clinic_services (id, name, duration_minutes, buffer_minutes, online_bookable, active) values (?, 'T030 Follow-up', 30, 5, false, true)", service);
        jdbc.update("insert into doctor_service_assignments (doctor_profile_id, service_id, active) values (?, ?, true)", doctor, service);
        jdbc.update("insert into doctor_work_shifts (id, doctor_profile_id, starts_at, ends_at) values (?, ?, ?, ?)",
                UUID.randomUUID(), doctor, Timestamp.from(SLOT.minusSeconds(3600)), Timestamp.from(SLOT.plusSeconds(7200)));
        jdbc.update("insert into clinic_visits (id, patient_profile_id, source, arrived_at, checked_in_at, created_by_account_id) values (?, ?, 'WALK_IN', ?, ?, ?)",
                visit, patient, Timestamp.from(NOW.minusSeconds(3600)), Timestamp.from(NOW.minusSeconds(3600)), account);
        jdbc.update("insert into visit_care_assignments (visit_id, doctor_profile_id, starts_at, ends_at, status) values (?, ?, ?, ?, 'COMPLETED')",
                visit, doctor, Timestamp.from(NOW.minusSeconds(3600)), Timestamp.from(NOW.minusSeconds(1800)));
        jdbc.update("insert into visit_clinical_examinations (visit_id, doctor_profile_id, completed, follow_up_indicated, completed_at) values (?, ?, true, true, ?)",
                visit, doctor, Timestamp.from(NOW.minusSeconds(1800)));
    }

    @AfterEach
    void cleanUp() {
        if (account == null) return;
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            String triggerMode = jdbc.queryForObject("select tgenabled::text from pg_trigger where tgrelid = 'appointment_audits'::regclass and tgname = 'tr_appointment_audits_immutable'", String.class);
            jdbc.execute("alter table appointment_audits disable trigger tr_appointment_audits_immutable");
            if (service != null) {
                jdbc.update("delete from appointment_audits where appointment_id in (select id from appointments where service_id = ?)", service);
                jdbc.update("delete from appointments where service_id = ?", service);
                jdbc.update("delete from visit_clinical_examinations where visit_id = ?", visit);
                jdbc.update("delete from visit_care_assignments where visit_id = ?", visit);
                jdbc.update("delete from clinic_visits where id = ?", visit);
                jdbc.update("delete from doctor_work_shifts where doctor_profile_id = ?", doctor);
                jdbc.update("delete from doctor_service_assignments where service_id = ?", service);
                jdbc.update("delete from clinic_services where id = ?", service);
            }
            if (doctor != null) jdbc.update("delete from doctor_profiles where id = ?", doctor);
            if (patient != null) jdbc.update("delete from patient_profiles where id = ?", patient);
            if (account != null) jdbc.update("delete from accounts where id = ?", account);
            switch (triggerMode) {
                case "D" -> jdbc.execute("alter table appointment_audits disable trigger tr_appointment_audits_immutable");
                case "R" -> jdbc.execute("alter table appointment_audits enable replica trigger tr_appointment_audits_immutable");
                case "A" -> jdbc.execute("alter table appointment_audits enable always trigger tr_appointment_audits_immutable");
                default -> jdbc.execute("alter table appointment_audits enable trigger tr_appointment_audits_immutable");
            }
        });
    }

    @Test
    void createsFollowUpWithVisitDerivedPatientAndImmutableAudit() throws Exception {
        mvc.perform(post("/api/v1/visits/{visitId}/follow-up-appointments", visit)
                        .with(actor()).with(csrf()).header("X-Correlation-ID", correlation)
                        .contentType("application/json").content(request("Review symptoms")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/appointments/")))
                .andExpect(jsonPath("$.patientProfileId").value(patient.toString()))
                .andExpect(jsonPath("$.doctorId").value(doctor.toString()))
                .andExpect(jsonPath("$.bookingSource").value("DOCTOR_FOLLOW_UP"))
                .andExpect(jsonPath("$.sourceVisitId").value(visit.toString()))
                .andExpect(jsonPath("$.durationMinutes").value(30))
                .andExpect(jsonPath("$.bufferMinutes").value(5));
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject(
                "select count(*) from appointments where source_visit_id = ? and patient_profile_id = ? and ordering_doctor_profile_id = ? and booking_source = 'DOCTOR_FOLLOW_UP'",
                Integer.class, visit, patient, doctor));
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject(
                "select count(*) from appointment_audits where source_visit_id = ? and actor_account_id = ? and actor_type = 'DOCTOR' and action = 'CREATED' and reason = 'Review symptoms' and correlation_id = ?",
                Integer.class, visit, account, correlation));
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject(
                "select count(*) from clinic_visits where id = ? and status = 'OPEN'", Integer.class, visit));
    }

    @Test
    void rejectsMissingReasonAndNonDoctorWithoutWrites() throws Exception {
        mvc.perform(post("/api/v1/visits/{visitId}/follow-up-appointments", visit)
                        .with(actor()).with(csrf()).contentType("application/json")
                        .content(request(null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        mvc.perform(post("/api/v1/visits/{visitId}/follow-up-appointments", visit)
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                new AuthenticatedAccount(account, AccountRole.PATIENT, null), null,
                                java.util.List.of(new SimpleGrantedAuthority("ROLE_PATIENT")))))
                        .with(csrf()).contentType("application/json").content(request("Reason")))
                .andExpect(status().isForbidden());
        org.junit.jupiter.api.Assertions.assertEquals(0, jdbc.queryForObject(
                "select count(*) from appointments where service_id = ?", Integer.class, service));
    }

    private String request(String reason) {
        return "{\"serviceId\":\"" + service + "\",\"doctorId\":\"" + doctor
                + "\",\"startTime\":\"2026-10-16T10:00:00+07:00\",\"purpose\":\"Follow-up\""
                + (reason == null ? "" : ",\"reason\":\"" + reason + "\"") + "}";
    }

    private UUID account() {
        UUID id = UUID.randomUUID();
        Instant created = Instant.parse("2026-10-01T00:00:00Z");
        jdbc.update("insert into accounts (id, normalized_email, account_type, status, created_at, updated_at) values (?, ?, 'DOCTOR', 'ACTIVE', ?, ?)",
                id, "t030-" + id + "@example.test", Timestamp.from(created), Timestamp.from(created));
        return id;
    }

    private RequestPostProcessor actor() {
        return authentication(new UsernamePasswordAuthenticationToken(new AuthenticatedAccount(account, AccountRole.DOCTOR, null), null,
                java.util.List.of(new SimpleGrantedAuthority("ROLE_DOCTOR"))));
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean @Primary Clock t030FixedClock() { return Clock.fixed(NOW, ZoneId.of("UTC")); }
    }
}
