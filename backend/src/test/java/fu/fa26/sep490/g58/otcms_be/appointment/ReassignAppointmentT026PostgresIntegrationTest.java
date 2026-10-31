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
@Import(ReassignAppointmentT026PostgresIntegrationTest.FixedClockConfiguration.class)
class ReassignAppointmentT026PostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-14T05:00:00Z");
    private static final Instant SLOT = Instant.parse("2026-10-16T03:00:00Z");
    private static final String CORRELATION = "154d7bed-fc75-4b57-a527-581a6f5b3ed2";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private UUID patientAccount;
    private UUID receptionistAccount;
    private UUID currentDoctorAccount;
    private UUID replacementDoctorAccount;
    private UUID unassignedDoctorAccount;
    private UUID patientProfile;
    private UUID currentDoctor;
    private UUID replacementDoctor;
    private UUID unassignedDoctor;
    private UUID service;
    private UUID appointment;
    private UUID replacementCollision;

    @BeforeEach
    void setUp() {
        patientAccount = account("PATIENT", "patient");
        receptionistAccount = account("RECEPTIONIST", "receptionist");
        currentDoctorAccount = account("DOCTOR", "current-doctor");
        replacementDoctorAccount = account("DOCTOR", "replacement-doctor");
        unassignedDoctorAccount = account("DOCTOR", "unassigned-doctor");
        patientProfile = patient(patientAccount);
        service = UUID.randomUUID();
        jdbc.update("insert into clinic_services (id, name, duration_minutes, buffer_minutes, online_bookable, active) "
                + "values (?, 'T026 Reassignment', 30, 5, true, true)", service);
        currentDoctor = doctor(currentDoctorAccount, "T026 Current Doctor");
        replacementDoctor = doctor(replacementDoctorAccount, "T026 Replacement Doctor");
        unassignedDoctor = doctor(unassignedDoctorAccount, "T026 Unassigned Doctor");
        assignment(currentDoctor);
        assignment(replacementDoctor);
        shift(currentDoctor);
        shift(replacementDoctor);
        appointment = insertAppointment(patientProfile, currentDoctor, SLOT, "T026 private purpose");
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
                jdbc.update("delete from doctor_shifts where doctor_profile_id in (?, ?, ?)", currentDoctor, replacementDoctor, unassignedDoctor);
                jdbc.update("delete from doctor_services where service_id = ?", service);
                jdbc.update("delete from clinic_services where id = ?", service);
            }
            for (UUID id : new UUID[]{currentDoctor, replacementDoctor, unassignedDoctor}) {
                if (id != null) jdbc.update("delete from doctor_profiles where id = ?", id);
            }
            if (patientProfile != null) jdbc.update("delete from patient_profiles where id = ?", patientProfile);
            for (UUID id : new UUID[]{patientAccount, receptionistAccount, currentDoctorAccount, replacementDoctorAccount, unassignedDoctorAccount}) {
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
    void receptionistReassignsToDifferentEligibleDoctorAtSameSlotWithAudit() throws Exception {
        mvc.perform(post("/api/v1/appointments/{id}/reassign", appointment)
                        .with(actor(receptionistAccount, AccountType.RECEPTIONIST)).with(csrf())
                        .header("X-Correlation-ID", CORRELATION).contentType("application/json")
                        .content("{\"doctorId\":\"" + replacementDoctor + "\",\"reason\":\"Doctor unavailable\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", CORRELATION))
                .andExpect(jsonPath("$.id").value(appointment.toString()))
                .andExpect(jsonPath("$.doctorId").value(replacementDoctor.toString()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.patientProfileId").value(patientProfile.toString()))
                .andExpect(jsonPath("$.purpose").value("T026 private purpose"))
                .andExpect(jsonPath("$.startTime").value(SLOT.toString()))
                .andExpect(jsonPath("$.durationMinutes").value(30))
                .andExpect(jsonPath("$.bufferMinutes").value(5));
        assertEquals(1, jdbc.queryForObject("select count(*) from appointment_audits where appointment_id = ? "
                + "and action = 'DOCTOR_REASSIGNED' and actor_account_id = ? and actor_type = 'RECEPTIONIST' "
                + "and old_doctor_profile_id = ? and new_doctor_profile_id = ? and old_start_time = new_start_time "
                + "and old_end_time = new_end_time and reason = 'Doctor unavailable' and correlation_id = ?",
                Integer.class, appointment, receptionistAccount, currentDoctor, replacementDoctor, CORRELATION));
    }

    @Test
    void onlyReceptionistMayReassignAndInvalidReplacementDoesNotChangeBooking() throws Exception {
        mvc.perform(post("/api/v1/appointments/{id}/reassign", appointment)
                        .with(actor(patientAccount, AccountType.PATIENT)).with(csrf()).contentType("application/json")
                        .content("{\"doctorId\":\"" + replacementDoctor + "\",\"reason\":\"Doctor unavailable\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/appointments/{id}/reassign", appointment)
                        .with(actor(receptionistAccount, AccountType.RECEPTIONIST)).with(csrf()).contentType("application/json")
                        .content("{\"doctorId\":\"" + currentDoctor + "\",\"reason\":\"Same Doctor\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        mvc.perform(post("/api/v1/appointments/{id}/reassign", appointment)
                        .with(actor(receptionistAccount, AccountType.RECEPTIONIST)).with(csrf()).contentType("application/json")
                        .content("{\"doctorId\":\"" + unassignedDoctor + "\",\"reason\":\"Doctor unavailable\"}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("DOCTOR_NOT_ELIGIBLE"));
        assertEquals(currentDoctor, jdbc.queryForObject("select doctor_profile_id from appointments where id = ?", UUID.class, appointment));
        assertEquals("CONFIRMED", jdbc.queryForObject("select status from appointments where id = ?", String.class, appointment));
        assertEquals(0, jdbc.queryForObject("select count(*) from appointment_audits where appointment_id = ? "
                + "and action = 'DOCTOR_REASSIGNED'", Integer.class, appointment));
    }

    @Test
    void occupiedReplacementSlotLeavesOriginalDoctorAndAuditUnchanged() throws Exception {
        replacementCollision = insertAppointment(patientProfile, replacementDoctor, SLOT, "T026 occupied target");
        mvc.perform(post("/api/v1/appointments/{id}/reassign", appointment)
                        .with(actor(receptionistAccount, AccountType.RECEPTIONIST)).with(csrf()).contentType("application/json")
                        .content("{\"doctorId\":\"" + replacementDoctor + "\",\"reason\":\"Doctor unavailable\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("SLOT_UNAVAILABLE"));
        assertEquals(currentDoctor, jdbc.queryForObject("select doctor_profile_id from appointments where id = ?", UUID.class, appointment));
        assertEquals("CONFIRMED", jdbc.queryForObject("select status from appointments where id = ?", String.class, appointment));
        assertEquals(0, jdbc.queryForObject("select count(*) from appointment_audits where appointment_id = ? "
                + "and action = 'DOCTOR_REASSIGNED'", Integer.class, appointment));
    }

    private UUID insertAppointment(UUID profile, UUID doctor, Instant start, String purpose) {
        UUID id = UUID.randomUUID();
        Instant end = start.plusSeconds(1800);
        jdbc.update("insert into appointments (id, patient_profile_id, service_id, doctor_profile_id, status, "
                        + "booking_source, purpose, start_time, end_time, duration_minutes, buffer_minutes, created_by_account_id) "
                        + "values (?, ?, ?, ?, 'CONFIRMED', 'PATIENT_ONLINE', ?, ?, ?, 30, 5, ?)",
                id, profile, service, doctor, purpose, Timestamp.from(start), Timestamp.from(end), patientAccount);
        jdbc.update("insert into appointment_audits (appointment_id, actor_account_id, actor_type, action, old_status, "
                        + "new_status, old_doctor_profile_id, new_doctor_profile_id, new_start_time, new_end_time, occurred_at, correlation_id) "
                        + "values (?, ?, 'PATIENT', 'CREATED', null, 'CONFIRMED', null, ?, ?, ?, ?, ?)",
                id, patientAccount, doctor, Timestamp.from(start), Timestamp.from(end), Timestamp.from(NOW), UUID.randomUUID().toString());
        return id;
    }

    private UUID doctor(UUID account, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into doctor_profiles (id, account_id, full_name, photo_url, work_contact, specialties, "
                        + "qualifications, biography, consultation_information, publicly_visible, accepting_new_bookings, in_active_directory) "
                        + "values (?, ?, ?, '', 'private', '', '', '', '', false, true, true)", id, account, name);
        return id;
    }

    private void assignment(UUID doctor) {
        jdbc.update("insert into doctor_services (doctor_profile_id, service_id, active) values (?, ?, true)",
                doctor, service);
    }

    private void shift(UUID doctor) {
        jdbc.update("insert into doctor_shifts (id, doctor_profile_id, starts_at, ends_at) values (?, ?, ?, ?)",
                UUID.randomUUID(), doctor, Timestamp.from(Instant.parse("2026-10-16T02:00:00Z")),
                Timestamp.from(Instant.parse("2026-10-16T05:00:00Z")));
    }

    private UUID account(String type, String suffix) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into accounts (id, normalized_email, account_type, status, created_at, updated_at) "
                        + "values (?, ?, ?, 'ACTIVE', ?, ?)", id, "t026-" + suffix + "-" + id + "@example.test", type,
                Timestamp.from(NOW), Timestamp.from(NOW));
        return id;
    }

    private UUID patient(UUID accountId) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into patient_profiles (id, account_id, full_name, phone_canonical, phone_owner_type, "
                        + "date_of_birth, gender) values (?, ?, 'T026 Patient', ?, 'PATIENT', '1990-01-01', 'OTHER')",
                id, accountId, "+849" + UUID.randomUUID().toString().replace("-", "").substring(0, 9));
        return id;
    }

    private RequestPostProcessor actor(UUID id, AccountType type) {
        var principal = new AuthenticatedAccount(id, fu.fa26.sep490.g58.otcms_be.common.security.AccountRole.valueOf(type.name()), UUID.randomUUID());
        return authentication(new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + type.name()))));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Primary @Bean Clock t026Clock() { return Clock.fixed(NOW, ZoneId.of("UTC")); }
    }
}
