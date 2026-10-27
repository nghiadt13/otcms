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
import java.time.ZoneId;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
@Import(CheckInAppointmentT027PostgresIntegrationTest.FixedClockConfiguration.class)
class CheckInAppointmentT027PostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-14T05:00:00Z");
    private static final String CORRELATION = "ad36bc05-3d4c-4b48-8a8e-a5fa9a133d27";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableTestClock testClock;
    private UUID patientAccount;
    private UUID receptionistAccount;
    private UUID patientProfile;
    private UUID doctorAccount;
    private UUID doctorProfile;
    private UUID service;
    private UUID appointment;
    private final List<UUID> appointments = new ArrayList<>();

    @BeforeEach
    void setUp() {
        testClock.set(NOW);
        patientAccount = account("PATIENT", "patient");
        receptionistAccount = account("RECEPTIONIST", "receptionist");
        doctorAccount = account("DOCTOR", "doctor");
        patientProfile = patient(patientAccount);
        service = UUID.randomUUID();
        jdbc.update("insert into clinic_services (id, name, duration_minutes, buffer_minutes, online_bookable, active) "
                + "values (?, 'T027 Check-in', 30, 5, true, true)", service);
        doctorProfile = doctor(doctorAccount);
        jdbc.update("insert into doctor_service_assignments (doctor_profile_id, service_id, active) values (?, ?, true)",
                doctorProfile, service);
        appointment = insertAppointment(NOW.plusSeconds(1800), "T027 inclusive early boundary");
    }

    @AfterEach
    void cleanUp() {
        String auditTrigger = jdbc.queryForObject("select tgenabled::text from pg_trigger "
                + "where tgrelid = 'appointment_audits'::regclass and tgname = 'tr_appointment_audits_immutable'", String.class);
        String replayTrigger = jdbc.queryForObject("select tgenabled::text from pg_trigger "
                + "where tgrelid = 'appointment_command_replays'::regclass and tgname = 'tr_appointment_command_replays_immutable'", String.class);
        jdbc.execute("alter table appointment_audits disable trigger tr_appointment_audits_immutable");
        jdbc.execute("alter table appointment_command_replays disable trigger tr_appointment_command_replays_immutable");
        try {
            jdbc.execute("drop trigger if exists tr_t027_reject_audit on appointment_audits");
            jdbc.execute("drop function if exists reject_t027_checkin_audit()");
            for (UUID appointmentId : appointments) {
                jdbc.update("delete from appointment_command_replays where appointment_id = ?", appointmentId);
                jdbc.update("delete from appointment_audits where appointment_id = ?", appointmentId);
                jdbc.update("update appointments set status = 'CANCELLED', visit_id = null where id = ?", appointmentId);
                jdbc.update("delete from recorded_appointment_arrivals where appointment_id = ?", appointmentId);
                List<UUID> visits = jdbc.query("select id from clinic_visits where originating_appointment_id = ?",
                        (rs, row) -> rs.getObject("id", UUID.class), appointmentId);
                for (UUID visit : visits) jdbc.update("delete from visit_initial_receptions where visit_id = ?", visit);
                jdbc.update("delete from clinic_visits where originating_appointment_id = ?", appointmentId);
                jdbc.update("delete from appointments where id = ?", appointmentId);
            }
            if (doctorProfile != null) jdbc.update("delete from doctor_service_assignments where doctor_profile_id = ?", doctorProfile);
            if (service != null) jdbc.update("delete from clinic_services where id = ?", service);
            if (doctorProfile != null) jdbc.update("delete from doctor_profiles where id = ?", doctorProfile);
            if (patientProfile != null) jdbc.update("delete from patient_profiles where id = ?", patientProfile);
            for (UUID id : new UUID[]{patientAccount, receptionistAccount, doctorAccount}) {
                if (id != null) jdbc.update("delete from accounts where id = ?", id);
            }
        } finally {
            restoreTrigger("appointment_audits", "tr_appointment_audits_immutable", auditTrigger);
            restoreTrigger("appointment_command_replays", "tr_appointment_command_replays_immutable", replayTrigger);
        }
    }

    @Test
    void checksInAtInclusiveEarlyBoundaryAndReplayAfterProgressionDoesNotDuplicateEvidence() throws Exception {
        Instant arrivedAt = NOW.minusSeconds(60);
        jdbc.update("insert into recorded_appointment_arrivals (appointment_id, patient_profile_id, receptionist_account_id, arrived_at) "
                + "values (?, ?, ?, ?)", appointment, patientProfile, receptionistAccount, Timestamp.from(arrivedAt));

        mvc.perform(checkIn(appointment, receptionistAccount, "t027-success"))
                .andExpect(status().isOk()).andExpect(header().string("X-Correlation-ID", CORRELATION))
                .andExpect(jsonPath("$.appointmentId").value(appointment.toString()))
                .andExpect(jsonPath("$.checkedInAt").value(NOW.toString()));
        UUID visitId = jdbc.queryForObject("select visit_id from appointments where id = ?", UUID.class, appointment);
        assertEquals("CHECKED_IN", jdbc.queryForObject("select status from appointments where id = ?", String.class, appointment));
        assertEquals(patientProfile, jdbc.queryForObject("select patient_profile_id from clinic_visits where id = ?", UUID.class, visitId));
        assertEquals("SCHEDULED", jdbc.queryForObject("select source from clinic_visits where id = ?", String.class, visitId));
        assertEquals(arrivedAt, jdbc.queryForObject("select arrived_at from clinic_visits where id = ?", Timestamp.class, visitId).toInstant());
        assertEquals(1, jdbc.queryForObject("select count(*) from visit_initial_receptions where visit_id = ? "
                + "and reception_type = 'APPOINTMENT_CHECK_IN' and receptionist_account_id = ?", Integer.class, visitId, receptionistAccount));
        assertEquals(1, jdbc.queryForObject("select count(*) from appointment_audits where appointment_id = ? "
                + "and action = 'STATUS_CHANGED' and old_status = 'CONFIRMED' and new_status = 'CHECKED_IN' "
                + "and linked_visit_id = ? and actor_account_id = ? and correlation_id = ?", Integer.class,
                appointment, visitId, receptionistAccount, CORRELATION));
        assertEquals(1, jdbc.queryForObject("select count(*) from appointment_command_replays where appointment_id = ? "
                + "and idempotency_key = 't027-success' and result_visit_id = ?", Integer.class, appointment, visitId));

        jdbc.update("update appointments set status = 'IN_PROGRESS' where id = ?", appointment);
        testClock.set(NOW.plusSeconds(7200));
        mvc.perform(checkIn(appointment, receptionistAccount, "t027-success"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.visitId").value(visitId.toString()))
                .andExpect(jsonPath("$.checkedInAt").value(NOW.toString()));
        assertEquals("IN_PROGRESS", jdbc.queryForObject("select status from appointments where id = ?", String.class, appointment));
        assertEquals(1, jdbc.queryForObject("select count(*) from clinic_visits where originating_appointment_id = ?", Integer.class, appointment));
        assertEquals(1, jdbc.queryForObject("select count(*) from appointment_audits where appointment_id = ? and action = 'STATUS_CHANGED'", Integer.class, appointment));
        assertEquals(1, jdbc.queryForObject("select count(*) from appointment_command_replays where appointment_id = ?", Integer.class, appointment));
    }

    @Test
    void acceptsInclusiveLateBoundaryAndRejectsOutsideWindowWithoutWrites() throws Exception {
        appointment = insertAppointment(NOW.minusSeconds(900), "T027 inclusive late boundary");
        testClock.set(NOW.minusSeconds(2701));
        mvc.perform(checkIn(appointment, receptionistAccount, "t027-outside"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("CHECK_IN_TIME_NOT_ALLOWED"));
        assertEquals("CONFIRMED", jdbc.queryForObject("select status from appointments where id = ?", String.class, appointment));
        assertEquals(0, jdbc.queryForObject("select count(*) from clinic_visits where originating_appointment_id = ?", Integer.class, appointment));
        assertEquals(0, jdbc.queryForObject("select count(*) from appointment_command_replays where appointment_id = ?", Integer.class, appointment));

        testClock.set(NOW);
        mvc.perform(checkIn(appointment, receptionistAccount, "t027-late"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkedInAt").value(NOW.toString()));
    }

    @Test
    void rejectsNonReceptionistAndDifferentKeyAfterCheckInWithoutCreatingDuplicate() throws Exception {
        mvc.perform(checkIn(appointment, patientAccount, "t027-forbidden"))
                .andExpect(status().isForbidden());
        mvc.perform(checkIn(appointment, receptionistAccount, "t027-first"))
                .andExpect(status().isOk());
        mvc.perform(checkIn(appointment, receptionistAccount, "t027-other-key"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("APPOINTMENT_NOT_CHANGEABLE"));
        assertEquals(1, jdbc.queryForObject("select count(*) from clinic_visits where originating_appointment_id = ?", Integer.class, appointment));
        assertEquals(1, jdbc.queryForObject("select count(*) from appointment_command_replays where appointment_id = ?", Integer.class, appointment));
        assertEquals(1, jdbc.queryForObject("select count(*) from appointment_audits where appointment_id = ? and action = 'STATUS_CHANGED'", Integer.class, appointment));
    }

    @Test
    void differentKeysRacingCreateOnlyOneVisitReceptionAuditAndReplay() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            var first = pool.submit(() -> concurrentCheckIn("t027-race-a", ready, start));
            var second = pool.submit(() -> concurrentCheckIn("t027-race-b", ready, start));
            if (!ready.await(5, TimeUnit.SECONDS)) throw new AssertionError("Concurrent requests did not reach the barrier");
            start.countDown();
            var statuses = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            assertEquals(1, statuses.stream().filter(status -> status == 200).count());
            assertEquals(1, statuses.stream().filter(status -> status == 409).count());
            assertEquals(1, jdbc.queryForObject("select count(*) from clinic_visits where originating_appointment_id = ?", Integer.class, appointment));
            assertEquals(1, jdbc.queryForObject("select count(*) from visit_initial_receptions r join clinic_visits v on v.id = r.visit_id "
                    + "where v.originating_appointment_id = ?", Integer.class, appointment));
            assertEquals(1, jdbc.queryForObject("select count(*) from appointment_audits where appointment_id = ? "
                    + "and action = 'STATUS_CHANGED' and new_status = 'CHECKED_IN'", Integer.class, appointment));
            assertEquals(1, jdbc.queryForObject("select count(*) from appointment_command_replays where appointment_id = ?", Integer.class, appointment));
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    private int concurrentCheckIn(String key, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("Concurrent request barrier timed out");
        return mvc.perform(checkIn(appointment, receptionistAccount, key)).andReturn().getResponse().getStatus();
    }

    @Test
    void mandatoryAuditFailureRollsBackVisitReceptionAppointmentAndReplay() throws Exception {
        jdbc.execute("create function reject_t027_checkin_audit() returns trigger language plpgsql as $$ "
                + "begin if new.correlation_id = '" + CORRELATION + "' and new.new_status = 'CHECKED_IN' "
                + "then raise exception 'T027 injected audit failure'; end if; return new; end $$");
        jdbc.execute("create trigger tr_t027_reject_audit before insert on appointment_audits "
                + "for each row execute function reject_t027_checkin_audit()");

        mvc.perform(checkIn(appointment, receptionistAccount, "t027-rollback"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("DEPENDENCY_UNAVAILABLE"));
        assertEquals("CONFIRMED", jdbc.queryForObject("select status from appointments where id = ?", String.class, appointment));
        assertEquals(null, jdbc.queryForObject("select visit_id from appointments where id = ?", UUID.class, appointment));
        assertEquals(0, jdbc.queryForObject("select count(*) from clinic_visits where originating_appointment_id = ?", Integer.class, appointment));
        assertEquals(0, jdbc.queryForObject("select count(*) from visit_initial_receptions r join clinic_visits v on v.id = r.visit_id "
                + "where v.originating_appointment_id = ?", Integer.class, appointment));
        assertEquals(0, jdbc.queryForObject("select count(*) from appointment_command_replays where appointment_id = ?", Integer.class, appointment));
        assertEquals(0, jdbc.queryForObject("select count(*) from appointment_audits where appointment_id = ? "
                + "and new_status = 'CHECKED_IN'", Integer.class, appointment));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder checkIn(
            UUID appointmentId, UUID actor, String key) {
        return post("/api/v1/appointments/{id}/check-in", appointmentId)
                .with(actor(actor, actor.equals(receptionistAccount) ? AccountType.RECEPTIONIST : AccountType.PATIENT))
                .with(csrf()).header("Idempotency-Key", key).header("X-Correlation-ID", CORRELATION)
                .contentType("application/json").content("{}");
    }

    private UUID insertAppointment(Instant start, String purpose) {
        UUID id = UUID.randomUUID();
        appointments.add(id);
        jdbc.update("insert into appointments (id, patient_profile_id, service_id, doctor_profile_id, status, "
                        + "booking_source, purpose, start_time, end_time, duration_minutes, buffer_minutes, created_by_account_id) "
                        + "values (?, ?, ?, ?, 'CONFIRMED', 'PATIENT_ONLINE', ?, ?, ?, 30, 5, ?)",
                id, patientProfile, service, doctorProfile, purpose, Timestamp.from(start), Timestamp.from(start.plusSeconds(1800)), patientAccount);
        jdbc.update("insert into appointment_audits (appointment_id, actor_account_id, actor_type, action, old_status, "
                        + "new_status, old_doctor_profile_id, new_doctor_profile_id, new_start_time, new_end_time, occurred_at, correlation_id) "
                        + "values (?, ?, 'PATIENT', 'CREATED', null, 'CONFIRMED', null, ?, ?, ?, ?, ?)",
                id, patientAccount, doctorProfile, Timestamp.from(start), Timestamp.from(start.plusSeconds(1800)),
                Timestamp.from(NOW), UUID.randomUUID().toString());
        return id;
    }

    private UUID doctor(UUID account) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into doctor_profiles (id, account_id, full_name, photo_url, work_contact, specialties, "
                        + "qualifications, biography, consultation_information, publicly_visible, accepting_new_bookings, in_active_directory) "
                        + "values (?, ?, 'T027 Doctor', '', 'private', '', '', '', '', true, true, true)", id, account);
        return id;
    }

    private UUID account(String type, String suffix) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into accounts (id, normalized_email, account_type, status, created_at, updated_at) "
                        + "values (?, ?, ?, 'ACTIVE', ?, ?)", id, "t027-" + suffix + "-" + id + "@example.test", type,
                Timestamp.from(NOW), Timestamp.from(NOW));
        return id;
    }

    private UUID patient(UUID accountId) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into patient_profiles (id, account_id, full_name, phone_canonical, phone_owner_type, "
                        + "date_of_birth, gender) values (?, ?, 'T027 Patient', ?, 'PATIENT', '1990-01-01', 'OTHER')",
                id, accountId, "+848" + UUID.randomUUID().toString().replace("-", "").substring(0, 9));
        return id;
    }

    private RequestPostProcessor actor(UUID id, AccountType type) {
        var principal = new AuthenticatedAccount(id, fu.fa26.sep490.g58.otcms_be.common.security.AccountRole.valueOf(type.name()), UUID.randomUUID());
        return authentication(new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + type.name()))));
    }

    private void restoreTrigger(String table, String name, String mode) {
        switch (mode) {
            case "D" -> jdbc.execute("alter table " + table + " disable trigger " + name);
            case "R" -> jdbc.execute("alter table " + table + " enable replica trigger " + name);
            case "A" -> jdbc.execute("alter table " + table + " enable always trigger " + name);
            default -> jdbc.execute("alter table " + table + " enable trigger " + name);
        }
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean @Primary MutableTestClock t027Clock() { return new MutableTestClock(NOW); }
    }

    static final class MutableTestClock extends Clock {
        private final AtomicReference<Instant> instant;

        MutableTestClock(Instant initial) { this.instant = new AtomicReference<>(initial); }
        void set(Instant value) { instant.set(value); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant.get(); }
    }
}
