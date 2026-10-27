package fu.fa26.sep490.g58.otcms_be.appointment;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@LocalPostgresTest
@Import(CancelAppointmentT025PostgresIntegrationTest.FixedClockConfiguration.class)
class CancelAppointmentT025PostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-14T05:00:00Z");
    private static final Instant START = Instant.parse("2026-10-16T02:00:00Z");
    private static final String CORRELATION = "d993cbe6-861e-46df-bdee-41a33d90a30a";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private UUID patientAccount;
    private UUID otherPatientAccount;
    private UUID receptionistAccount;
    private UUID doctorAccount;
    private UUID patientProfile;
    private UUID otherPatientProfile;
    private UUID doctorProfile;
    private UUID service;
    private UUID appointment;

    @BeforeEach
    void setUp() {
        patientAccount = account("PATIENT", "patient");
        otherPatientAccount = account("PATIENT", "other");
        receptionistAccount = account("RECEPTIONIST", "receptionist");
        doctorAccount = account("DOCTOR", "doctor");
        patientProfile = patient(patientAccount, "T025 Patient");
        otherPatientProfile = patient(otherPatientAccount, "T025 Other");
        doctorProfile = UUID.randomUUID();
        service = UUID.randomUUID();
        jdbc.update("insert into clinic_services (id, name, duration_minutes, buffer_minutes, online_bookable, active) "
                + "values (?, 'T025 Cancellation', 30, 0, true, true)", service);
        jdbc.update("insert into doctor_profiles (id, account_id, full_name, photo_url, work_contact, specialties, "
                        + "qualifications, biography, consultation_information, publicly_visible, accepting_new_bookings, in_active_directory) "
                        + "values (?, ?, 'T025 Doctor', '', 'private', '', '', '', '', true, true, true)", doctorProfile, doctorAccount);
        jdbc.update("insert into doctor_service_assignments (doctor_profile_id, service_id, active) values (?, ?, true)",
                doctorProfile, service);
        appointment = insertAppointment(patientProfile, patientAccount, START, "T025 private purpose");
    }

    @AfterEach
    void cleanUp() {
        String triggerMode = jdbc.queryForObject("select tgenabled::text from pg_trigger "
                        + "where tgrelid = 'appointment_audits'::regclass and tgname = 'tr_appointment_audits_immutable'",
                String.class);
        jdbc.execute("alter table appointment_audits disable trigger tr_appointment_audits_immutable");
        try {
            if (service != null) {
                jdbc.update("delete from appointment_audits where appointment_id in (select id from appointments where service_id = ?)", service);
                jdbc.update("delete from appointments where service_id = ?", service);
                jdbc.update("delete from doctor_service_assignments where service_id = ?", service);
                jdbc.update("delete from clinic_services where id = ?", service);
            }
            if (doctorProfile != null) jdbc.update("delete from doctor_profiles where id = ?", doctorProfile);
            if (patientProfile != null) jdbc.update("delete from patient_profiles where id = ?", patientProfile);
            if (otherPatientProfile != null) jdbc.update("delete from patient_profiles where id = ?", otherPatientProfile);
            for (UUID id : new UUID[]{patientAccount, otherPatientAccount, receptionistAccount, doctorAccount}) {
                if (id != null) jdbc.update("delete from accounts where id = ?", id);
            }
        } finally {
            switch (triggerMode) {
                case "D" -> jdbc.execute("alter table appointment_audits disable trigger tr_appointment_audits_immutable");
                case "R" -> jdbc.execute("alter table appointment_audits enable replica trigger tr_appointment_audits_immutable");
                case "A" -> jdbc.execute("alter table appointment_audits enable always trigger tr_appointment_audits_immutable");
                default -> jdbc.execute("alter table appointment_audits enable trigger tr_appointment_audits_immutable");
            }
        }
    }

    @Test
    void patientCancellationUpdatesStatusWritesAuditAndReleasesOccupiedSlot() throws Exception {
        mvc.perform(post("/api/v1/appointments/{id}/cancel", appointment)
                        .with(actor(patientAccount, AccountType.PATIENT)).with(csrf())
                        .header("X-Correlation-ID", CORRELATION).contentType("application/json").content("{}"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", CORRELATION))
                .andExpect(jsonPath("$.id").value(appointment.toString()))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.startTime").value(START.toString()))
                .andExpect(jsonPath("$.purpose").value("T025 private purpose"));
        assertEquals(1, jdbc.queryForObject("select count(*) from appointment_audits where appointment_id = ? "
                + "and actor_account_id = ? and actor_type = 'PATIENT' and action = 'CANCELLED' "
                + "and old_status = 'CONFIRMED' and new_status = 'CANCELLED' and old_start_time = new_start_time "
                + "and old_end_time = new_end_time and reason is null and correlation_id = ?", Integer.class,
                appointment, patientAccount, CORRELATION));
        UUID releasedSlotBooking = insertAppointment(otherPatientProfile, otherPatientAccount, START, "Released slot");
        assertEquals(1, jdbc.queryForObject("select count(*) from appointments where id = ? and status = 'CONFIRMED'",
                Integer.class, releasedSlotBooking));
    }

    @Test
    void receptionistRequiresReasonAndMayOverridePatientDeadline() throws Exception {
        mvc.perform(post("/api/v1/appointments/{id}/cancel", appointment)
                        .with(actor(otherPatientAccount, AccountType.PATIENT)).with(csrf())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("APPOINTMENT_NOT_FOUND"));
        mvc.perform(post("/api/v1/appointments/{id}/cancel", appointment)
                        .with(actor(receptionistAccount, AccountType.RECEPTIONIST)).with(csrf())
                        .contentType("application/json").content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        assertEquals("CONFIRMED", jdbc.queryForObject("select status from appointments where id = ?", String.class, appointment));
        Instant withinPatientDeadline = NOW.plusSeconds(12 * 3600L);
        jdbc.update("update appointments set start_time = ?, end_time = ? where id = ?",
                Timestamp.from(withinPatientDeadline), Timestamp.from(withinPatientDeadline.plusSeconds(1800)), appointment);
        mvc.perform(post("/api/v1/appointments/{id}/cancel", appointment)
                        .with(actor(receptionistAccount, AccountType.RECEPTIONIST)).with(csrf())
                        .contentType("application/json").content("{\"reason\":\"Clinic requested cancellation\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        assertEquals(1, jdbc.queryForObject("select count(*) from appointment_audits where appointment_id = ? "
                + "and actor_type = 'RECEPTIONIST' and reason = 'Clinic requested cancellation'", Integer.class, appointment));
    }

    @Test
    void patientInsideDeadlineCannotCancel() throws Exception {
        Instant insideDeadline = NOW.plusSeconds(1439 * 60L);
        jdbc.update("update appointments set start_time = ?, end_time = ? where id = ?",
                Timestamp.from(insideDeadline), Timestamp.from(insideDeadline.plusSeconds(1800)), appointment);
        mvc.perform(post("/api/v1/appointments/{id}/cancel", appointment)
                        .with(actor(patientAccount, AccountType.PATIENT)).with(csrf())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("APPOINTMENT_CHANGE_TOO_LATE"));
        assertEquals("CONFIRMED", jdbc.queryForObject("select status from appointments where id = ?", String.class, appointment));
    }

    private UUID insertAppointment(UUID profileId, UUID creator, Instant start, String purpose) {
        UUID id = UUID.randomUUID();
        Instant end = start.plusSeconds(1800);
        jdbc.update("insert into appointments (id, patient_profile_id, service_id, doctor_profile_id, status, "
                        + "booking_source, purpose, start_time, end_time, duration_minutes, buffer_minutes, created_by_account_id) "
                        + "values (?, ?, ?, ?, 'CONFIRMED', 'PATIENT_ONLINE', ?, ?, ?, 30, 0, ?)",
                id, profileId, service, doctorProfile, purpose, Timestamp.from(start), Timestamp.from(end), creator);
        jdbc.update("insert into appointment_audits (appointment_id, actor_account_id, actor_type, action, old_status, "
                        + "new_status, old_doctor_profile_id, new_doctor_profile_id, new_start_time, new_end_time, occurred_at, correlation_id) "
                        + "values (?, ?, 'PATIENT', 'CREATED', null, 'CONFIRMED', null, ?, ?, ?, ?, ?)",
                id, creator, doctorProfile, Timestamp.from(start), Timestamp.from(end), Timestamp.from(NOW), UUID.randomUUID().toString());
        return id;
    }

    private UUID account(String type, String suffix) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into accounts (id, normalized_email, account_type, status, created_at, updated_at) "
                        + "values (?, ?, ?, 'ACTIVE', ?, ?)", id, "t025-" + suffix + "-" + id + "@example.test", type,
                Timestamp.from(NOW), Timestamp.from(NOW));
        return id;
    }

    private UUID patient(UUID accountId, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into patient_profiles (id, account_id, full_name, phone_canonical, phone_owner_type, "
                        + "date_of_birth, gender) values (?, ?, ?, ?, 'PATIENT', '1990-01-01', 'OTHER')",
                id, accountId, name, "+849" + UUID.randomUUID().toString().replace("-", "").substring(0, 9));
        return id;
    }

    private RequestPostProcessor actor(UUID id, AccountType type) {
        var principal = new AuthenticatedAccount(id, fu.fa26.sep490.g58.otcms_be.common.security.AccountRole.valueOf(type.name()), UUID.randomUUID());
        return authentication(new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + type.name()))));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Primary @Bean Clock t025Clock() { return Clock.fixed(NOW, ZoneId.of("UTC")); }
    }
}
