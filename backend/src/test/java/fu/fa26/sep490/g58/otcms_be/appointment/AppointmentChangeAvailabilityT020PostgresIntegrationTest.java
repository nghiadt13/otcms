package fu.fa26.sep490.g58.otcms_be.appointment;

import fu.fa26.sep490.g58.otcms_be.appointment.model.*;

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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@LocalPostgresTest
@Import(AppointmentChangeAvailabilityT020PostgresIntegrationTest.FixedClockConfiguration.class)
class AppointmentChangeAvailabilityT020PostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-14T05:00:00Z");
    private static final Instant APPOINTMENT_START = Instant.parse("2026-10-16T07:00:00Z");
    private static final LocalDate CHANGE_DATE = LocalDate.of(2026, 10, 16);
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private UUID serviceId;
    private UUID patientAccountId;
    private UUID otherPatientAccountId;
    private UUID receptionistAccountId;
    private UUID currentDoctorAccountId;
    private UUID replacementDoctorAccountId;
    private UUID patientProfileId;
    private UUID otherPatientProfileId;
    private UUID currentDoctorId;
    private UUID replacementDoctorId;
    private UUID confirmedAppointmentId;
    private UUID pastAppointmentId;
    private UUID cancelledAppointmentId;

    @BeforeEach
    void setUp() {
        serviceId = UUID.randomUUID();
        patientAccountId = UUID.randomUUID();
        otherPatientAccountId = UUID.randomUUID();
        receptionistAccountId = UUID.randomUUID();
        currentDoctorAccountId = UUID.randomUUID();
        replacementDoctorAccountId = UUID.randomUUID();
        patientProfileId = UUID.randomUUID();
        otherPatientProfileId = UUID.randomUUID();
        currentDoctorId = UUID.randomUUID();
        replacementDoctorId = UUID.randomUUID();
        confirmedAppointmentId = UUID.randomUUID();
        pastAppointmentId = UUID.randomUUID();
        cancelledAppointmentId = UUID.randomUUID();

        Instant createdAt = Instant.parse("2026-10-01T00:00:00Z");
        insertAccount(patientAccountId, "PATIENT", "t020-patient-");
        insertAccount(otherPatientAccountId, "PATIENT", "t020-other-patient-");
        insertAccount(receptionistAccountId, "RECEPTIONIST", "t020-receptionist-");
        insertAccount(currentDoctorAccountId, "DOCTOR", "t020-current-doctor-");
        insertAccount(replacementDoctorAccountId, "DOCTOR", "t020-replacement-doctor-");
        insertPatient(patientProfileId, patientAccountId, "T020 Patient");
        insertPatient(otherPatientProfileId, otherPatientAccountId, "T020 Other Patient");

        jdbc.update("insert into clinic_services (id, name, duration_minutes, buffer_minutes, online_bookable, active) "
                        + "values (?, 'T020 existing service', 30, 5, true, false)", serviceId);
        insertDoctor(currentDoctorId, currentDoctorAccountId, "T020 Current Doctor", false, false);
        insertDoctor(replacementDoctorId, replacementDoctorAccountId, "T020 Replacement Doctor", true, true);
        for (UUID doctorId : List.of(currentDoctorId, replacementDoctorId)) {
            jdbc.update("insert into doctor_shifts (id, doctor_profile_id, starts_at, ends_at) values (?, ?, ?, ?)",
                    UUID.randomUUID(), doctorId, shiftStart(), shiftEnd());
            jdbc.update("insert into doctor_services (doctor_profile_id, service_id, active) values (?, ?, true)",
                    doctorId, serviceId);
        }

        insertAppointment(confirmedAppointmentId, patientProfileId, currentDoctorId, receptionistAccountId,
                APPOINTMENT_START, "CONFIRMED");
        insertAppointment(pastAppointmentId, patientProfileId, currentDoctorId, receptionistAccountId,
                NOW.minusSeconds(60), "CONFIRMED");
        insertAppointment(cancelledAppointmentId, patientProfileId, currentDoctorId, receptionistAccountId,
                APPOINTMENT_START.plusSeconds(86400), "CANCELLED");
    }

    @AfterEach
    void cleanUp() {
        if (serviceId == null) return;
        jdbc.update("delete from appointments where id in (?, ?, ?)",
                confirmedAppointmentId, pastAppointmentId, cancelledAppointmentId);
        jdbc.update("delete from doctor_services where service_id = ?", serviceId);
        jdbc.update("delete from doctor_shifts where doctor_profile_id in (?, ?)",
                currentDoctorId, replacementDoctorId);
        jdbc.update("delete from doctor_profiles where id in (?, ?)", currentDoctorId, replacementDoctorId);
        jdbc.update("delete from patient_profiles where id in (?, ?)", patientProfileId, otherPatientProfileId);
        jdbc.update("delete from accounts where id in (?, ?, ?, ?, ?)", patientAccountId, otherPatientAccountId,
                receptionistAccountId, currentDoctorAccountId, replacementDoctorAccountId);
        jdbc.update("delete from clinic_services where id = ?", serviceId);
    }

    @Test
    void rescheduleAvailabilityReturnsOnlyCurrentDoctorsSlotsAndAllowsExistingInactiveService() throws Exception {
        mvc.perform(get(reschedulePath()).with(actor(patientAccountId, AccountType.PATIENT))
                        .param("from", CHANGE_DATE.toString()).param("to", CHANGE_DATE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[*].doctorId", hasItem(currentDoctorId.toString())))
                .andExpect(jsonPath("$.slots[*].doctorId", not(hasItem(replacementDoctorId.toString()))))
                .andExpect(jsonPath("$.slots[*].serviceId", hasItem(serviceId.toString())))
                .andExpect(jsonPath("$.slots[*].startTime", hasItem(APPOINTMENT_START.toString())))
                .andExpect(jsonPath("$.slots[*].doctorDisplayName", hasItem("T020 Current Doctor")))
                .andExpect(jsonPath("$..patientProfileId").doesNotExist())
                .andExpect(jsonPath("$..purpose").doesNotExist());

        mvc.perform(get(reschedulePath()).with(actor(receptionistAccountId, AccountType.RECEPTIONIST))
                        .param("from", CHANGE_DATE.toString()).param("to", CHANGE_DATE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[*].doctorId", hasItem(currentDoctorId.toString())));
    }

    @Test
    void rescheduleAvailabilityEnforcesProfileScopeRoleAndPatientChangeDeadline() throws Exception {
        mvc.perform(get(reschedulePath()).with(actor(otherPatientAccountId, AccountType.PATIENT))
                        .param("from", CHANGE_DATE.toString()).param("to", CHANGE_DATE.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("APPOINTMENT_NOT_FOUND"));

        mvc.perform(get(reschedulePath()).with(actor(UUID.randomUUID(), AccountType.ADMIN))
                        .param("from", CHANGE_DATE.toString()).param("to", CHANGE_DATE.toString()))
                .andExpect(status().isForbidden());

        mvc.perform(get(reschedulePath(pastAppointmentId)).with(actor(patientAccountId, AccountType.PATIENT))
                        .param("from", CHANGE_DATE.toString()).param("to", CHANGE_DATE.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("APPOINTMENT_CHANGE_TOO_LATE"));
    }

    @Test
    void reassignmentAvailabilityReturnsOnlyDifferentEligibleDoctorAtSameSlot() throws Exception {
        mvc.perform(get(reassignmentPath()).with(actor(receptionistAccountId, AccountType.RECEPTIONIST))
                        .param("from", CHANGE_DATE.toString()).param("to", CHANGE_DATE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots.length()").value(1))
                .andExpect(jsonPath("$.slots[*].doctorId", hasItem(replacementDoctorId.toString())))
                .andExpect(jsonPath("$.slots[*].doctorId", not(hasItem(currentDoctorId.toString()))))
                .andExpect(jsonPath("$.slots[*].startTime", hasItem(APPOINTMENT_START.toString())))
                .andExpect(jsonPath("$.slots[*].serviceId", hasItem(serviceId.toString())))
                .andExpect(jsonPath("$..patientProfileId").doesNotExist());
    }

    @Test
    void reassignmentAvailabilityIsReceptionistOnlyAndRejectsPastOrUnchangeableBookings() throws Exception {
        mvc.perform(get(reassignmentPath()).with(actor(patientAccountId, AccountType.PATIENT))
                        .param("from", CHANGE_DATE.toString()).param("to", CHANGE_DATE.toString()))
                .andExpect(status().isForbidden());

        mvc.perform(get(reassignmentPath(pastAppointmentId)).with(actor(receptionistAccountId, AccountType.RECEPTIONIST))
                        .param("from", CHANGE_DATE.toString()).param("to", CHANGE_DATE.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("BOOKING_TIME_IN_PAST"));

        mvc.perform(get(reassignmentPath(cancelledAppointmentId)).with(actor(receptionistAccountId, AccountType.RECEPTIONIST))
                        .param("from", CHANGE_DATE.toString()).param("to", CHANGE_DATE.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("APPOINTMENT_NOT_CHANGEABLE"));
    }

    private void insertAccount(UUID id, String type, String emailPrefix) {
        Instant createdAt = Instant.parse("2026-10-01T00:00:00Z");
        jdbc.update("insert into accounts (id, normalized_email, account_type, status, created_at, updated_at) "
                        + "values (?, ?, ?, 'ACTIVE', ?, ?)", id,
                emailPrefix + id + "@example.test", type, Timestamp.from(createdAt), Timestamp.from(createdAt));
    }

    private void insertPatient(UUID profileId, UUID accountId, String name) {
        jdbc.update("insert into patient_profiles (id, account_id, full_name, phone_canonical, phone_owner_type, "
                        + "date_of_birth, gender) values (?, ?, ?, ?, 'PATIENT', '1990-01-01', 'OTHER')",
                profileId, accountId, name, "+84900000000" + profileId.toString().substring(0, 2));
    }

    private void insertDoctor(UUID doctorId, UUID accountId, String name, boolean accepting, boolean inDirectory) {
        jdbc.update("insert into doctor_profiles (id, account_id, full_name, photo_url, work_contact, specialties, "
                        + "qualifications, biography, consultation_information, publicly_visible, accepting_new_bookings, in_active_directory) "
                        + "values (?, ?, ?, '', 'private-contact', '', '', '', '', false, ?, ?)",
                doctorId, accountId, name, accepting, inDirectory);
    }

    private void insertAppointment(UUID id, UUID patientId, UUID doctorId, UUID creatorId, Instant start, String status) {
        jdbc.update("insert into appointments (id, patient_profile_id, service_id, doctor_profile_id, status, "
                        + "booking_source, purpose, start_time, end_time, duration_minutes, buffer_minutes, created_by_account_id) "
                        + "values (?, ?, ?, ?, ?, 'RECEPTIONIST_ASSISTED', 'T020 integration fixture', ?, ?, 30, 5, ?)",
                id, patientId, serviceId, doctorId, status, Timestamp.from(start),
                Timestamp.from(start.plusSeconds(1800)), creatorId);
    }

    private Timestamp shiftStart() {
        return Timestamp.from(CHANGE_DATE.atTime(13, 30).atZone(CLINIC_ZONE).toInstant());
    }

    private Timestamp shiftEnd() {
        return Timestamp.from(CHANGE_DATE.atTime(15, 0).atZone(CLINIC_ZONE).toInstant());
    }

    private String reschedulePath() { return reschedulePath(confirmedAppointmentId); }
    private String reschedulePath(UUID id) { return "/api/v1/appointments/" + id + "/reschedule-availability"; }
    private String reassignmentPath() { return reassignmentPath(confirmedAppointmentId); }
    private String reassignmentPath(UUID id) { return "/api/v1/appointments/" + id + "/reassignment-availability"; }

    private RequestPostProcessor actor(UUID accountId, AccountType type) {
        var principal = new AuthenticatedAccount(accountId, fu.fa26.sep490.g58.otcms_be.common.security.AccountRole.valueOf(type.name()), UUID.randomUUID());
        return authentication(new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + type.name()))));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Primary
        @Bean Clock availabilityTestClock() { return Clock.fixed(NOW, ZoneId.of("UTC")); }
    }
}
