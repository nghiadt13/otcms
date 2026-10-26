package fu.fa26.sep490.g58.otcms_be.appointment;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@LocalPostgresTest
@Import(AppointmentBookingT022PostgresIntegrationTest.FixedClockConfiguration.class)
class AppointmentBookingT022PostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-14T05:00:00Z");
    private static final Instant SLOT = Instant.parse("2026-10-16T03:00:00Z");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    private UUID patientAccount;
    private UUID receptionistAccount;
    private UUID otherPatientAccount;
    private UUID doctorAccount;
    private UUID patientProfile;
    private UUID otherPatientProfile;
    private UUID unlinkedPatientProfile;
    private UUID doctorProfile;
    private UUID service;

    @BeforeEach
    void setUp() {
        patientAccount = account("PATIENT", "patient");
        receptionistAccount = account("RECEPTIONIST", "receptionist");
        otherPatientAccount = account("PATIENT", "other-patient");
        doctorAccount = account("DOCTOR", "doctor");
        patientProfile = patient(patientAccount, "T022 Patient");
        otherPatientProfile = patient(otherPatientAccount, "T022 Other Patient");
        unlinkedPatientProfile = patient(null, "T022 Walk-in");
        doctorProfile = UUID.randomUUID();
        service = UUID.randomUUID();
        jdbc.update("insert into clinic_services (id, name, duration_minutes, buffer_minutes, online_bookable, active) "
                + "values (?, 'T022 Booking', 30, 0, true, true)", service);
        jdbc.update("insert into doctor_profiles (id, account_id, full_name, photo_url, work_contact, specialties, "
                        + "qualifications, biography, consultation_information, publicly_visible, accepting_new_bookings, in_active_directory) "
                        + "values (?, ?, 'T022 Doctor', '', 'private', '', '', '', '', true, true, true)", doctorProfile, doctorAccount);
        jdbc.update("insert into doctor_services (doctor_profile_id, service_id, active) values (?, ?, true)",
                doctorProfile, service);
        jdbc.update("insert into doctor_shifts (id, doctor_profile_id, starts_at, ends_at) values (?, ?, ?, ?)",
                UUID.randomUUID(), doctorProfile, Timestamp.from(Instant.parse("2026-10-16T02:00:00Z")),
                Timestamp.from(Instant.parse("2026-10-16T05:00:00Z")));
    }

    @AfterEach
    void cleanUp() {
        if (service == null && patientAccount == null && receptionistAccount == null
                && otherPatientAccount == null && doctorAccount == null) return;
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            String originalAuditTriggerMode = jdbc.queryForObject("select tgenabled::text from pg_trigger "
                            + "where tgrelid = 'appointment_audits'::regclass and tgname = 'tr_appointment_audits_immutable'",
                    String.class);
            jdbc.execute("alter table appointment_audits disable trigger tr_appointment_audits_immutable");
            if (service != null) {
                jdbc.update("delete from appointment_audits where appointment_id in "
                                + "(select id from appointments where service_id = ? and created_by_account_id in (?, ?, ?))",
                        service, patientAccount, otherPatientAccount, receptionistAccount);
                jdbc.update("delete from appointments where service_id = ? and created_by_account_id in (?, ?, ?)",
                        service, patientAccount, otherPatientAccount, receptionistAccount);
                jdbc.update("delete from doctor_services where service_id = ?", service);
                jdbc.update("delete from clinic_services where id = ?", service);
            }
            if (doctorProfile != null) {
                jdbc.update("delete from doctor_shifts where doctor_profile_id = ?", doctorProfile);
                jdbc.update("delete from doctor_profiles where id = ?", doctorProfile);
            }
            if (patientProfile != null) jdbc.update("delete from patient_profiles where id = ?", patientProfile);
            if (otherPatientProfile != null) jdbc.update("delete from patient_profiles where id = ?", otherPatientProfile);
            if (unlinkedPatientProfile != null) jdbc.update("delete from patient_profiles where id = ?", unlinkedPatientProfile);
            if (patientAccount != null) jdbc.update("delete from accounts where id = ?", patientAccount);
            if (receptionistAccount != null) jdbc.update("delete from accounts where id = ?", receptionistAccount);
            if (otherPatientAccount != null) jdbc.update("delete from accounts where id = ?", otherPatientAccount);
            if (doctorAccount != null) jdbc.update("delete from accounts where id = ?", doctorAccount);
            switch (originalAuditTriggerMode) {
                case "D" -> jdbc.execute("alter table appointment_audits disable trigger tr_appointment_audits_immutable");
                case "R" -> jdbc.execute("alter table appointment_audits enable replica trigger tr_appointment_audits_immutable");
                case "A" -> jdbc.execute("alter table appointment_audits enable always trigger tr_appointment_audits_immutable");
                default -> jdbc.execute("alter table appointment_audits enable trigger tr_appointment_audits_immutable");
            }
        });
    }

    @Test
    void patientCreateDerivesSelfAndWritesAtomicCreatedAuditAndResponse() throws Exception {
        mvc.perform(post("/api/v1/appointments").with(actor(patientAccount, AccountType.PATIENT)).with(csrf())
                        .header("X-Correlation-ID", "d2b57a06-4b72-445b-a293-7b3d8c039e55")
                        .contentType("application/json")
                        .content(request(null, "Routine visit", null, null)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/appointments/")))
                .andExpect(header().string("X-Correlation-ID", "d2b57a06-4b72-445b-a293-7b3d8c039e55"))
                .andExpect(jsonPath("$.patientProfileId").value(patientProfile.toString()))
                .andExpect(jsonPath("$.bookingSource").value("PATIENT_ONLINE"))
                .andExpect(jsonPath("$.durationMinutes").value(30))
                .andExpect(jsonPath("$.bufferMinutes").value(0))
                .andExpect(jsonPath("$.occupiedUntil").exists());
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject(
                "select count(*) from appointment_audits where correlation_id = ? and action = 'CREATED' "
                        + "and actor_type = 'PATIENT' and new_status = 'CONFIRMED'", Integer.class,
                "d2b57a06-4b72-445b-a293-7b3d8c039e55"));
    }

    @Test
    void receptionistCanBookAccountlessProfileAndClientCannotForgeSourceOrCreator() throws Exception {
        mvc.perform(post("/api/v1/appointments").with(actor(receptionistAccount, AccountType.RECEPTIONIST)).with(csrf())
                        .contentType("application/json").content(request(unlinkedPatientProfile, "Assisted visit", null, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.patientProfileId").value(unlinkedPatientProfile.toString()))
                .andExpect(jsonPath("$.bookingSource").value("RECEPTIONIST_ASSISTED"));

        mvc.perform(post("/api/v1/appointments").with(actor(receptionistAccount, AccountType.RECEPTIONIST)).with(csrf())
                        .contentType("application/json").content(request(unlinkedPatientProfile, "Duplicate visit", null, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SLOT_UNAVAILABLE"));

        mvc.perform(post("/api/v1/appointments").with(actor(receptionistAccount, AccountType.RECEPTIONIST)).with(csrf())
                        .contentType("application/json").content(request(unlinkedPatientProfile,
                                "Assisted visit", "bookingSource", "DOCTOR_FOLLOW_UP")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void patientCannotBookAnotherProfileAndOtherAuthenticatedRolesAreForbidden() throws Exception {
        mvc.perform(post("/api/v1/appointments").with(actor(patientAccount, AccountType.PATIENT)).with(csrf())
                        .contentType("application/json").content(request(otherPatientProfile, "Routine visit", null, null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PATIENT_PROFILE_NOT_FOUND"));

        mvc.perform(post("/api/v1/appointments").with(actor(doctorAccount, AccountType.DOCTOR)).with(csrf())
                        .contentType("application/json").content(request(patientProfile, "Routine visit", null, null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void concurrentSameSlotCreatesAllowExactlyOneAppointment() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> postAtBarrier(start, patientAccount));
            var second = executor.submit(() -> postAtBarrier(start, otherPatientAccount));
            start.countDown();
            MvcResult firstResult = first.get(15, TimeUnit.SECONDS);
            MvcResult secondResult = second.get(15, TimeUnit.SECONDS);
            List<Integer> statuses = List.of(firstResult.getResponse().getStatus(), secondResult.getResponse().getStatus());
            org.junit.jupiter.api.Assertions.assertEquals(1, statuses.stream().filter(code -> code == 201).count());
            org.junit.jupiter.api.Assertions.assertEquals(1, statuses.stream().filter(code -> code == 409).count());
            org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject(
                    "select count(*) from appointments where service_id = ? and start_time = ? and status = 'CONFIRMED'",
                    Integer.class, service, Timestamp.from(SLOT)));
            org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject(
                    "select count(*) from appointment_audits aa join appointments a on a.id = aa.appointment_id "
                            + "where a.service_id = ? and a.start_time = ? and aa.action = 'CREATED'",
                    Integer.class, service, Timestamp.from(SLOT)));
        } finally {
            executor.shutdownNow();
            try {
                org.junit.jupiter.api.Assertions.assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS),
                        "Concurrent request workers should stop before fixture cleanup");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while stopping concurrent test workers", exception);
            }
        }
    }

    @Test
    void auditInsertFailureRollsBackAppointmentInsert() throws Exception {
        jdbc.execute("create function t022_reject_audit_insert() returns trigger language plpgsql as $$ "
                + "begin raise exception 'T022 forced audit insert failure'; end; $$");
        jdbc.execute("create trigger t022_reject_audit_insert before insert on appointment_audits "
                + "for each row execute function t022_reject_audit_insert()");
        MvcResult failure = null;
        jakarta.servlet.ServletException propagatedFailure = null;
        try {
            try {
                failure = mvc.perform(post("/api/v1/appointments").with(actor(patientAccount, AccountType.PATIENT)).with(csrf())
                                .contentType("application/json").content(request(null, "Routine visit", null, null)))
                        .andReturn();
            } catch (jakarta.servlet.ServletException expected) {
                // MockMvc may propagate an unhandled test-triggered database failure instead of rendering a 500.
                propagatedFailure = expected;
            }
        } finally {
            jdbc.execute("drop trigger t022_reject_audit_insert on appointment_audits");
            jdbc.execute("drop function t022_reject_audit_insert()");
        }
        if (failure != null) {
            org.junit.jupiter.api.Assertions.assertEquals(500, failure.getResponse().getStatus());
        } else {
            org.junit.jupiter.api.Assertions.assertNotNull(propagatedFailure,
                    "The forced audit insert failure must propagate or render HTTP 500");
            org.junit.jupiter.api.Assertions.assertTrue(exceptionContains(propagatedFailure, "T022 forced audit insert failure"),
                    "The propagated database error must be the forced audit insert failure");
        }
        org.junit.jupiter.api.Assertions.assertEquals(0, jdbc.queryForObject(
                "select count(*) from appointments where service_id = ? and created_by_account_id = ?",
                Integer.class, service, patientAccount));
        org.junit.jupiter.api.Assertions.assertEquals(0, jdbc.queryForObject(
                "select count(*) from appointment_audits where actor_account_id = ? and action = 'CREATED'",
                Integer.class, patientAccount));
    }

    private MvcResult postAtBarrier(CountDownLatch start, UUID accountId) {
        try {
            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent test start timed out");
            return mvc.perform(post("/api/v1/appointments").with(actor(accountId, AccountType.PATIENT)).with(csrf())
                            .contentType("application/json").content(request(null, "Routine visit", null, null)))
                    .andReturn();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private boolean exceptionContains(Throwable exception, String expectedMessage) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current.getMessage() != null && current.getMessage().contains(expectedMessage)) return true;
        }
        return false;
    }

    private String request(UUID targetProfile, String purpose, String extraField, String extraValue) {
        StringBuilder json = new StringBuilder("{");
        if (targetProfile != null) json.append("\"patientProfileId\":\"").append(targetProfile).append("\",");
        json.append("\"serviceId\":\"").append(service).append("\",")
                .append("\"doctorId\":\"").append(doctorProfile).append("\",")
                .append("\"startTime\":\"").append(SLOT).append("\",")
                .append("\"purpose\":\"").append(purpose).append("\"");
        if (extraField != null) json.append(",\"").append(extraField).append("\":\"").append(extraValue).append("\"");
        return json.append('}').toString();
    }

    private UUID account(String type, String suffix) {
        UUID id = UUID.randomUUID();
        Instant created = Instant.parse("2026-10-01T00:00:00Z");
        jdbc.update("insert into accounts (id, normalized_email, account_type, status, created_at, updated_at) "
                        + "values (?, ?, ?, 'ACTIVE', ?, ?)", id, "t022-" + suffix + "-" + id + "@example.test", type,
                Timestamp.from(created), Timestamp.from(created));
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
        @Primary @Bean Clock t022Clock() { return Clock.fixed(NOW, ZoneId.of("UTC")); }
    }
}
