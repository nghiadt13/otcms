package fu.fa26.sep490.g58.otcms_be.appointment;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@LocalPostgresTest
@Import(MarkAppointmentNoShowT028PostgresIntegrationTest.FixedClockConfiguration.class)
class MarkAppointmentNoShowT028PostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-18T05:00:00Z");
    private static final String CORRELATION = "2b7e9631-0472-43d1-b3ab-9804b82504a0";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableTestClock testClock;
    private UUID patientAccount;
    private UUID receptionistAccount;
    private UUID patientProfile;
    private UUID doctorAccount;
    private UUID doctorProfile;
    private UUID service;
    private final List<UUID> appointments = new ArrayList<>();

    @BeforeEach
    void setUp() {
        testClock.set(NOW);
        patientAccount = account("PATIENT");
        receptionistAccount = account("RECEPTIONIST");
        doctorAccount = account("DOCTOR");
        patientProfile = patient(patientAccount);
        service = UUID.randomUUID();
        jdbc.update("insert into clinic_services (id, name, duration_minutes, buffer_minutes, online_bookable, active) "
                + "values (?, 'T028 no-show', 30, 5, true, true)", service);
        doctorProfile = doctor(doctorAccount);
        jdbc.update("insert into doctor_service_assignments (doctor_profile_id, service_id, active) values (?, ?, true)",
                doctorProfile, service);
    }

    @AfterEach
    void cleanUp() {
        String auditTrigger = triggerMode("appointment_audits", "tr_appointment_audits_immutable");
        String replayTrigger = triggerMode("appointment_command_replays", "tr_appointment_command_replays_immutable");
        jdbc.execute("alter table appointment_audits disable trigger tr_appointment_audits_immutable");
        jdbc.execute("alter table appointment_command_replays disable trigger tr_appointment_command_replays_immutable");
        try {
            for (UUID id : appointments) {
                jdbc.update("delete from appointment_command_replays where appointment_id = ?", id);
                jdbc.update("delete from appointment_audits where appointment_id = ?", id);
                jdbc.update("update appointments set status = 'CANCELLED', visit_id = null where id = ?", id);
                jdbc.update("delete from recorded_appointment_arrivals where appointment_id = ?", id);
                List<UUID> visits = jdbc.query("select id from clinic_visits where originating_appointment_id = ?",
                        (rs, row) -> rs.getObject("id", UUID.class), id);
                for (UUID visit : visits) jdbc.update("delete from visit_initial_receptions where visit_id = ?", visit);
                jdbc.update("delete from clinic_visits where originating_appointment_id = ?", id);
                jdbc.update("delete from appointments where id = ?", id);
            }
            jdbc.update("delete from doctor_service_assignments where doctor_profile_id = ?", doctorProfile);
            jdbc.update("delete from clinic_services where id = ?", service);
            jdbc.update("delete from doctor_profiles where id = ?", doctorProfile);
            jdbc.update("delete from patient_profiles where id = ?", patientProfile);
            for (UUID id : new UUID[]{patientAccount, receptionistAccount, doctorAccount})
                jdbc.update("delete from accounts where id = ?", id);
        } finally {
            restoreTrigger("appointment_audits", "tr_appointment_audits_immutable", auditTrigger);
            restoreTrigger("appointment_command_replays", "tr_appointment_command_replays_immutable", replayTrigger);
        }
    }

    @Test
    void marksNoShowOnlyAfterGraceAndWritesActorReasonAndCorrelationAudit() throws Exception {
        UUID appointment = appointment(NOW.minusSeconds(3600));
        mvc.perform(markNoShow(appointment, receptionistAccount, AccountType.RECEPTIONIST, "absent"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NO_SHOW"));
        assertEquals("NO_SHOW", jdbc.queryForObject("select status from appointments where id = ?", String.class, appointment));
        assertEquals(1, jdbc.queryForObject("select count(*) from appointment_audits where appointment_id = ? "
                + "and old_status = 'CONFIRMED' and new_status = 'NO_SHOW' and actor_account_id = ? "
                + "and actor_type = 'RECEPTIONIST' and reason = 'absent' and correlation_id = ?",
                Integer.class, appointment, receptionistAccount, CORRELATION));
        assertEquals(0, jdbc.queryForObject("select count(*) from clinic_visits where originating_appointment_id = ?",
                Integer.class, appointment));
    }

    @Test
    void rejectsGraceBoundaryAndTimelyArrivalWithoutMutation() throws Exception {
        UUID boundary = appointment(NOW.minusSeconds(900));
        mvc.perform(markNoShow(boundary, receptionistAccount, AccountType.RECEPTIONIST, null))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("NO_SHOW_NOT_ALLOWED"));
        assertEquals("CONFIRMED", jdbc.queryForObject("select status from appointments where id = ?", String.class, boundary));

        UUID timely = appointment(NOW.minusSeconds(3600));
        jdbc.update("insert into recorded_appointment_arrivals (appointment_id, patient_profile_id, receptionist_account_id, arrived_at) "
                + "values (?, ?, ?, ?)", timely, patientProfile, receptionistAccount, Timestamp.from(NOW.minusSeconds(3000)));
        mvc.perform(markNoShow(timely, receptionistAccount, AccountType.RECEPTIONIST, null))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("NO_SHOW_NOT_ALLOWED"));
        assertEquals("CONFIRMED", jdbc.queryForObject("select status from appointments where id = ?", String.class, timely));
        assertEquals(0, jdbc.queryForObject("select count(*) from appointment_audits where appointment_id = ? and new_status = 'NO_SHOW'",
                Integer.class, timely));
    }

    @Test
    void serializesLateCheckInAndNoShowAndAllowsOnlyNoShowTransition() throws Exception {
        UUID appointment = appointment(NOW.minusSeconds(3600));
        var pool = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            var noShow = pool.submit(() -> race(() -> mvc.perform(markNoShow(appointment, receptionistAccount,
                    AccountType.RECEPTIONIST, null)).andReturn().getResponse().getStatus(), ready, start));
            var checkIn = pool.submit(() -> race(() -> mvc.perform(post("/api/v1/appointments/{id}/check-in", appointment)
                    .with(actor(receptionistAccount, AccountType.RECEPTIONIST)).with(csrf())
                    .header("Idempotency-Key", "t028-race").header("X-Correlation-ID", CORRELATION)
                    .contentType("application/json").content("{}")).andReturn().getResponse().getStatus(), ready, start));
            if (!ready.await(5, TimeUnit.SECONDS)) throw new AssertionError("Concurrent commands did not reach the barrier");
            start.countDown();
            int noShowStatus = noShow.get(20, TimeUnit.SECONDS);
            int checkInStatus = checkIn.get(20, TimeUnit.SECONDS);
            assertEquals(200, noShowStatus);
            if (checkInStatus != 409 && checkInStatus != 422) {
                throw new AssertionError("Expected late check-in to be rejected by state or grace policy, got " + checkInStatus);
            }
            assertEquals("NO_SHOW", jdbc.queryForObject("select status from appointments where id = ?", String.class, appointment));
            assertEquals(1, jdbc.queryForObject("select count(*) from appointment_audits where appointment_id = ? "
                    + "and action = 'STATUS_CHANGED' and new_status = 'NO_SHOW'", Integer.class, appointment));
            assertEquals(0, jdbc.queryForObject("select count(*) from clinic_visits where originating_appointment_id = ?",
                    Integer.class, appointment));
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void rejectsNonReceptionist() throws Exception {
        UUID appointment = appointment(NOW.minusSeconds(3600));
        mvc.perform(markNoShow(appointment, patientAccount, AccountType.PATIENT, null)).andExpect(status().isForbidden());
        assertEquals("CONFIRMED", jdbc.queryForObject("select status from appointments where id = ?", String.class, appointment));
    }

    private int race(ThrowingInt task, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("Concurrent command barrier timed out");
        return task.get();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder markNoShow(
            UUID id, UUID actor, AccountType type, String reason) {
        String body = reason == null ? "{\"targetStatus\":\"NO_SHOW\"}"
                : "{\"targetStatus\":\"NO_SHOW\",\"reason\":\"" + reason + "\"}";
        return patch("/api/v1/appointments/{id}/status", id).with(actor(actor, type)).with(csrf())
                .header("X-Correlation-ID", CORRELATION).contentType("application/json").content(body);
    }

    private UUID appointment(Instant start) {
        UUID id = UUID.randomUUID();
        appointments.add(id);
        jdbc.update("insert into appointments (id, patient_profile_id, service_id, doctor_profile_id, status, booking_source, "
                        + "purpose, start_time, end_time, duration_minutes, buffer_minutes, created_by_account_id) "
                        + "values (?, ?, ?, ?, 'CONFIRMED', 'PATIENT_ONLINE', 'T028 test', ?, ?, 30, 5, ?)",
                id, patientProfile, service, doctorProfile, Timestamp.from(start), Timestamp.from(start.plusSeconds(1800)), patientAccount);
        jdbc.update("insert into appointment_audits (appointment_id, actor_account_id, actor_type, action, old_status, "
                        + "new_status, old_doctor_profile_id, new_doctor_profile_id, new_start_time, new_end_time, occurred_at, correlation_id) "
                        + "values (?, ?, 'PATIENT', 'CREATED', null, 'CONFIRMED', null, ?, ?, ?, ?, ?)",
                id, patientAccount, doctorProfile, Timestamp.from(start), Timestamp.from(start.plusSeconds(1800)),
                Timestamp.from(NOW), UUID.randomUUID().toString());
        return id;
    }

    private UUID account(String type) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into accounts (id, normalized_email, account_type, status, created_at, updated_at) values (?, ?, ?, 'ACTIVE', ?, ?)",
                id, "t028-" + type + "-" + id + "@example.test", type, Timestamp.from(NOW), Timestamp.from(NOW));
        return id;
    }

    private UUID patient(UUID accountId) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into patient_profiles (id, account_id, full_name, phone_canonical, phone_owner_type, date_of_birth, gender) "
                        + "values (?, ?, 'T028 Patient', ?, 'PATIENT', '1990-01-01', 'OTHER')", id, accountId,
                "+848" + UUID.randomUUID().toString().replace("-", "").substring(0, 9));
        return id;
    }

    private UUID doctor(UUID accountId) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into doctor_profiles (id, account_id, full_name, photo_url, work_contact, specialties, qualifications, "
                        + "biography, consultation_information, publicly_visible, accepting_new_bookings, in_active_directory) "
                        + "values (?, ?, 'T028 Doctor', '', 'private', '', '', '', '', true, true, true)", id, accountId);
        return id;
    }

    private RequestPostProcessor actor(UUID id, AccountType type) {
        var principal = new AuthenticatedAccount(id, AccountRole.valueOf(type.name()), UUID.randomUUID());
        return authentication(new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + type.name()))));
    }

    private String triggerMode(String table, String trigger) {
        return jdbc.queryForObject("select tgenabled::text from pg_trigger where tgrelid = '" + table
                + "'::regclass and tgname = ?", String.class, trigger);
    }

    private void restoreTrigger(String table, String trigger, String mode) {
        switch (mode) {
            case "D" -> jdbc.execute("alter table " + table + " disable trigger " + trigger);
            case "R" -> jdbc.execute("alter table " + table + " enable replica trigger " + trigger);
            case "A" -> jdbc.execute("alter table " + table + " enable always trigger " + trigger);
            default -> jdbc.execute("alter table " + table + " enable trigger " + trigger);
        }
    }

    @FunctionalInterface interface ThrowingInt { int get() throws Exception; }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean @Primary MutableTestClock t028Clock() { return new MutableTestClock(NOW); }
    }

    static final class MutableTestClock extends Clock {
        private final AtomicReference<Instant> instant;
        MutableTestClock(Instant initial) { instant = new AtomicReference<>(initial); }
        void set(Instant value) { instant.set(value); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant.get(); }
    }
}
