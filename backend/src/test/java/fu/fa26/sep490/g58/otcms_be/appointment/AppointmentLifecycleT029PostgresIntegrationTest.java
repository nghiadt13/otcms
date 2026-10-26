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

@SpringBootTest(properties = "otcms.visit.care-step-room-capability.SCHEDULED_APPOINTMENT=NONE")
@AutoConfigureMockMvc
@LocalPostgresTest
@Import(AppointmentLifecycleT029PostgresIntegrationTest.FixedClockConfiguration.class)
class AppointmentLifecycleT029PostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-19T05:00:00Z");
    private static final String CORRELATION = "4e717a7d-2a8e-4f54-9633-4c2e1cf58ca2";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableTestClock testClock;
    private UUID patientAccount, receptionistAccount, doctorAccount, patientProfile, doctorProfile, service;
    private final List<UUID> appointments = new ArrayList<>();
    private final List<UUID> extraVisits = new ArrayList<>();

    @BeforeEach
    void setUp() {
        cleanStaleT029Fixtures();
        testClock.set(NOW);
        patientAccount = account("PATIENT"); receptionistAccount = account("RECEPTIONIST"); doctorAccount = account("DOCTOR");
        patientProfile = patient(patientAccount);
        service = UUID.randomUUID();
        jdbc.update("insert into clinic_services (id, name, duration_minutes, buffer_minutes, online_bookable, active) values (?, 'T029 lifecycle', 30, 5, true, true)", service);
        doctorProfile = doctor(doctorAccount);
        jdbc.update("insert into doctor_service_assignments (doctor_profile_id, service_id, active) values (?, ?, true)", doctorProfile, service);
    }

    @AfterEach
    void cleanUp() {
        cleanT029Fixtures(appointments, extraVisits,
                List.of(patientAccount, receptionistAccount, doctorAccount),
                List.of(patientProfile, doctorProfile), List.of(service));
    }

    private void cleanStaleT029Fixtures() {
        List<UUID> staleAccounts = jdbc.query("select id from accounts where normalized_email like 't029-%@example.test'",
                (rs, row) -> rs.getObject("id", UUID.class));
        List<UUID> staleServices = jdbc.query("select id from clinic_services where name = 'T029 lifecycle'",
                (rs, row) -> rs.getObject("id", UUID.class));
        List<UUID> staleAppointments = jdbc.query("select a.id from appointments a join clinic_services s on s.id = a.service_id where a.purpose = 'T029 purpose' and s.name = 'T029 lifecycle'",
                (rs, row) -> rs.getObject("id", UUID.class));
        List<UUID> staleVisits = jdbc.query("select v.id from clinic_visits v join accounts a on a.id = v.created_by_account_id where v.source = 'WALK_IN' and a.normalized_email like 't029-%@example.test'",
                (rs, row) -> rs.getObject("id", UUID.class));
        List<UUID> staleProfiles = new ArrayList<>();
        staleProfiles.addAll(jdbc.query("select p.id from patient_profiles p join accounts a on a.id = p.account_id where a.normalized_email like 't029-%@example.test'",
                (rs, row) -> rs.getObject("id", UUID.class)));
        staleProfiles.addAll(jdbc.query("select p.id from doctor_profiles p join accounts a on a.id = p.account_id where a.normalized_email like 't029-%@example.test'",
                (rs, row) -> rs.getObject("id", UUID.class)));
        cleanT029Fixtures(staleAppointments, staleVisits, staleAccounts, staleProfiles, staleServices);
    }

    private void cleanT029Fixtures(List<UUID> fixtureAppointments, List<UUID> fixtureVisits,
            List<UUID> fixtureAccounts, List<UUID> fixtureProfiles, List<UUID> fixtureServices) {
        String auditMode = jdbc.queryForObject("select tgenabled::text from pg_trigger where tgrelid = 'appointment_audits'::regclass and tgname = 'tr_appointment_audits_immutable'", String.class);
        String replayMode = jdbc.queryForObject("select tgenabled::text from pg_trigger where tgrelid = 'appointment_command_replays'::regclass and tgname = 'tr_appointment_command_replays_immutable'", String.class);
        String careAuditMode = jdbc.queryForObject("select tgenabled::text from pg_trigger where tgrelid = 'visit_care_assignment_audits'::regclass and tgname = 'tr_visit_care_assignment_audits_immutable'", String.class);
        jdbc.execute("alter table appointment_audits disable trigger tr_appointment_audits_immutable");
        jdbc.execute("alter table appointment_command_replays disable trigger tr_appointment_command_replays_immutable");
        jdbc.execute("alter table visit_care_assignment_audits disable trigger tr_visit_care_assignment_audits_immutable");
        try {
            jdbc.execute("drop trigger if exists tr_t029_reject_cancel_audit on appointment_audits");
            jdbc.execute("drop function if exists reject_t029_cancel_audit()");
            for (UUID id : fixtureAppointments) {
                List<UUID> visits = jdbc.query("select id from clinic_visits where originating_appointment_id = ?", (rs, row) -> rs.getObject("id", UUID.class), id);
                jdbc.update("delete from appointment_command_replays where appointment_id = ?", id);
                jdbc.update("delete from appointment_audits where appointment_id = ?", id);
                jdbc.update("update appointments set status = 'CANCELLED', visit_id = null where id = ?", id);
                for (UUID visit : visits) {
                    jdbc.update("delete from visit_clinical_examinations where visit_id = ?", visit);
                    jdbc.update("delete from visit_care_assignment_audits where visit_id = ?", visit);
                    jdbc.update("delete from visit_care_assignments where visit_id = ?", visit);
                    jdbc.update("delete from visit_initial_receptions where visit_id = ?", visit);
                }
                jdbc.update("delete from clinic_visits where originating_appointment_id = ?", id);
                jdbc.update("delete from appointments where id = ?", id);
            }
            for (UUID visit : fixtureVisits) {
                jdbc.update("delete from walk_in_reception_replays where visit_id = ?", visit);
                jdbc.update("delete from visit_clinical_examinations where visit_id = ?", visit);
                jdbc.update("delete from visit_care_assignment_audits where visit_id = ?", visit);
                jdbc.update("delete from visit_care_assignments where visit_id = ?", visit);
                jdbc.update("delete from visit_initial_receptions where visit_id = ?", visit);
                jdbc.update("delete from clinic_visits where id = ?", visit);
            }
            for (UUID profile : fixtureProfiles) {
                jdbc.update("delete from doctor_service_assignments where doctor_profile_id = ?", profile);
                jdbc.update("delete from doctor_profiles where id = ?", profile);
                jdbc.update("delete from patient_profiles where id = ?", profile);
            }
            for (UUID id : fixtureServices) jdbc.update("delete from clinic_services where id = ?", id);
            for (UUID id : fixtureAccounts) jdbc.update("delete from accounts where id = ?", id);
        } finally {
            restoreTrigger("appointment_audits", "tr_appointment_audits_immutable", auditMode);
            restoreTrigger("appointment_command_replays", "tr_appointment_command_replays_immutable", replayMode);
            restoreTrigger("visit_care_assignment_audits", "tr_visit_care_assignment_audits_immutable", careAuditMode);
        }
    }

    @Test
    void assignedDoctorStartsAndCompletesWhileVisitStaysOpenAndAppointmentIntervalIsRetained() throws Exception {
        UUID appointment = checkedInAppointment();
        Instant end = jdbc.queryForObject("select end_time from appointments where id = ?", Timestamp.class, appointment).toInstant();
        Instant occupiedUntil = jdbc.queryForObject("select occupied_until from appointments where id = ?", Timestamp.class, appointment).toInstant();
        mvc.perform(statusChange(appointment, doctorAccount, AccountType.DOCTOR, "IN_PROGRESS"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        UUID visit = jdbc.queryForObject("select visit_id from appointments where id = ?", UUID.class, appointment);
        assertEquals(1, jdbc.queryForObject("select count(*) from visit_care_assignments where visit_id = ? and doctor_profile_id = ? and status = 'IN_PROGRESS'", Integer.class, visit, doctorProfile));
        mvc.perform(statusChange(appointment, doctorAccount, AccountType.DOCTOR, "COMPLETED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        assertEquals("OPEN", jdbc.queryForObject("select status from clinic_visits where id = ?", String.class, visit));
        assertEquals("COMPLETED", jdbc.queryForObject("select status from visit_care_assignments where visit_id = ?", String.class, visit));
        assertEquals(end, jdbc.queryForObject("select end_time from appointments where id = ?", Timestamp.class, appointment).toInstant());
        assertEquals(occupiedUntil, jdbc.queryForObject("select occupied_until from appointments where id = ?", Timestamp.class, appointment).toInstant());
        assertEquals(1, jdbc.queryForObject("select count(*) from appointment_audits where appointment_id = ? and old_status = 'IN_PROGRESS' and new_status = 'COMPLETED'", Integer.class, appointment));
    }

    @Test
    void receptionistCanCancelCheckedInVisitOnlyBeforeCareStarts() throws Exception {
        UUID appointment = checkedInAppointment();
        UUID visit = jdbc.queryForObject("select visit_id from appointments where id = ?", UUID.class, appointment);
        mvc.perform(cancel(appointment)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        assertEquals("CLOSED", jdbc.queryForObject("select status from clinic_visits where id = ?", String.class, visit));
        assertEquals(1, jdbc.queryForObject("select count(*) from visit_initial_receptions where visit_id = ?", Integer.class, visit));
        assertEquals(1, jdbc.queryForObject("select count(*) from appointment_audits where appointment_id = ? and action = 'CANCELLED' and old_status = 'CHECKED_IN' and new_status = 'CANCELLED' and reason = 'T029 cancellation'", Integer.class, appointment));
    }

    @Test
    void doctorStartAndReceptionCancellationRaceResolvesAccordingToFirstCommittedState() throws Exception {
        UUID cancelledFirst = checkedInAppointment();
        mvc.perform(cancel(cancelledFirst)).andExpect(status().isOk());
        mvc.perform(statusChange(cancelledFirst, doctorAccount, AccountType.DOCTOR, "IN_PROGRESS"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("APPOINTMENT_NOT_CHANGEABLE"));

        UUID startedFirst = checkedInAppointment();
        mvc.perform(statusChange(startedFirst, doctorAccount, AccountType.DOCTOR, "IN_PROGRESS")).andExpect(status().isOk());
        mvc.perform(cancel(startedFirst)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("VISIT_CANCELLATION_NOT_ALLOWED"));
        assertEquals("IN_PROGRESS", jdbc.queryForObject("select status from appointments where id = ?", String.class, startedFirst));
    }

    @Test
    void concurrentCareStartAndCancellationCommitOnlyOneConsistentOutcome() throws Exception {
        UUID appointment = checkedInAppointment();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var care = pool.submit(() -> concurrentStatusChange(appointment, ready, start));
            var cancel = pool.submit(() -> concurrentCancel(appointment, ready, start));
            if (!ready.await(5, TimeUnit.SECONDS)) throw new AssertionError("T029 race setup timed out");
            start.countDown();
            int careCode = care.get(10, TimeUnit.SECONDS);
            int cancelCode = cancel.get(10, TimeUnit.SECONDS);
            assertEquals(1, (careCode == 200 ? 1 : 0) + (cancelCode == 200 ? 1 : 0));
            String appointmentStatus = jdbc.queryForObject("select status from appointments where id = ?", String.class, appointment);
            UUID visit = jdbc.queryForObject("select visit_id from appointments where id = ?", UUID.class, appointment);
            String visitStatus = jdbc.queryForObject("select status from clinic_visits where id = ?", String.class, visit);
            if ("CANCELLED".equals(appointmentStatus)) {
                assertEquals(409, careCode);
                assertEquals(200, cancelCode);
                assertEquals("CLOSED", visitStatus);
            } else {
                assertEquals("IN_PROGRESS", appointmentStatus);
                assertEquals(200, careCode);
                assertEquals(409, cancelCode);
                assertEquals("OPEN", visitStatus);
            }
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void existingClinicalActivityRejectsCancellationWithoutChangingEitherOwner() throws Exception {
        UUID appointment = checkedInAppointment();
        UUID visit = jdbc.queryForObject("select visit_id from appointments where id = ?", UUID.class, appointment);
        jdbc.update("insert into visit_clinical_examinations (visit_id, doctor_profile_id, completed, follow_up_indicated) values (?, ?, false, false)", visit, doctorProfile);
        mvc.perform(cancel(appointment)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("VISIT_CANCELLATION_NOT_ALLOWED"));
        assertEquals("CHECKED_IN", jdbc.queryForObject("select status from appointments where id = ?", String.class, appointment));
        assertEquals("OPEN", jdbc.queryForObject("select status from clinic_visits where id = ?", String.class, visit));
        assertEquals(0, jdbc.queryForObject("select count(*) from appointment_audits where appointment_id = ? and new_status = 'CANCELLED'", Integer.class, appointment));
    }

    @Test
    void receptionistCannotUseDoctorLifecycleTargetAndIllegalCompletionIsRejected() throws Exception {
        UUID appointment = checkedInAppointment();
        mvc.perform(statusChange(appointment, receptionistAccount, AccountType.RECEPTIONIST, "IN_PROGRESS"))
                .andExpect(status().isForbidden());
        mvc.perform(statusChange(appointment, doctorAccount, AccountType.DOCTOR, "COMPLETED"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("APPOINTMENT_NOT_CHANGEABLE"));
        assertEquals("CHECKED_IN", jdbc.queryForObject("select status from appointments where id = ?", String.class, appointment));
    }

    @Test
    void appointmentAuditFailureRollsBackVisitCloseAndCancellation() throws Exception {
        UUID appointment = checkedInAppointment();
        UUID visit = jdbc.queryForObject("select visit_id from appointments where id = ?", UUID.class, appointment);
        jdbc.execute("create function reject_t029_cancel_audit() returns trigger language plpgsql as $$ begin "
                + "if new.correlation_id = '" + CORRELATION + "' and new.new_status = 'CANCELLED' then "
                + "raise exception 'T029 injected appointment audit failure'; end if; return new; end $$");
        jdbc.execute("create trigger tr_t029_reject_cancel_audit before insert on appointment_audits "
                + "for each row execute function reject_t029_cancel_audit()");
        mvc.perform(cancel(appointment)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("DEPENDENCY_UNAVAILABLE"));
        assertEquals("CHECKED_IN", jdbc.queryForObject("select status from appointments where id = ?", String.class, appointment));
        assertEquals("OPEN", jdbc.queryForObject("select status from clinic_visits where id = ?", String.class, visit));
        assertEquals(0, jdbc.queryForObject("select count(*) from appointment_audits where appointment_id = ? and new_status = 'CANCELLED'", Integer.class, appointment));
    }

    @Test
    void anotherActiveCareAssignmentRejectsStartWithoutChangingAppointment() throws Exception {
        UUID appointment = checkedInAppointment();
        UUID visit = jdbc.queryForObject("select visit_id from appointments where id = ?", UUID.class, appointment);
        UUID otherVisit = UUID.randomUUID();
        extraVisits.add(otherVisit);
        jdbc.update("insert into clinic_visits (id, patient_profile_id, source, arrived_at, checked_in_at, created_by_account_id, status) values (?, ?, 'WALK_IN', ?, ?, ?, 'OPEN')",
                otherVisit, patientProfile, Timestamp.from(NOW), Timestamp.from(NOW), receptionistAccount);
        jdbc.update("insert into visit_initial_receptions (visit_id, receptionist_account_id, reception_type, arrived_at, checked_in_at, prior_appointment_id) values (?, ?, 'WALK_IN', ?, ?, null)",
                otherVisit, receptionistAccount, Timestamp.from(NOW), Timestamp.from(NOW));
        UUID assignment = UUID.randomUUID();
        jdbc.update("insert into visit_care_assignments (id, visit_id, doctor_profile_id, starts_at, ends_at, status) values (?, ?, ?, ?, ?, 'IN_PROGRESS')",
                assignment, otherVisit, doctorProfile, Timestamp.from(NOW.minusSeconds(60)), Timestamp.from(NOW.plusSeconds(600)));
        mvc.perform(statusChange(appointment, doctorAccount, AccountType.DOCTOR, "IN_PROGRESS"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("VISIT_CAPACITY_NOT_AVAILABLE"));
        assertEquals("CHECKED_IN", jdbc.queryForObject("select status from appointments where id = ?", String.class, appointment));
        assertEquals(0, jdbc.queryForObject("select count(*) from visit_care_assignments where visit_id = ?", Integer.class, visit));
        assertEquals(1, jdbc.queryForObject("select count(*) from visit_care_assignment_audits where visit_id = ? and assignment_id is null and actor_account_id = ? and action = 'CAPACITY_CONFLICT' and step_code = 'SCHEDULED_APPOINTMENT' and correlation_id = ?", Integer.class, visit, doctorAccount, CORRELATION));
        jdbc.update("delete from visit_care_assignments where id = ?", assignment);
        jdbc.update("delete from visit_initial_receptions where visit_id = ?", otherVisit);
        jdbc.update("delete from clinic_visits where id = ?", otherVisit);
    }

    private UUID checkedInAppointment() throws Exception {
        UUID appointment = UUID.randomUUID(); appointments.add(appointment);
        Instant start = NOW;
        jdbc.update("insert into appointments (id, patient_profile_id, service_id, doctor_profile_id, status, booking_source, purpose, start_time, end_time, duration_minutes, buffer_minutes, created_by_account_id) values (?, ?, ?, ?, 'CONFIRMED', 'PATIENT_ONLINE', 'T029 purpose', ?, ?, 30, 5, ?)",
                appointment, patientProfile, service, doctorProfile, Timestamp.from(start), Timestamp.from(start.plusSeconds(1800)), patientAccount);
        jdbc.update("insert into appointment_audits (appointment_id, actor_account_id, actor_type, action, new_status, new_doctor_profile_id, new_start_time, new_end_time, occurred_at, correlation_id) values (?, ?, 'PATIENT', 'CREATED', 'CONFIRMED', ?, ?, ?, ?, ?)",
                appointment, patientAccount, doctorProfile, Timestamp.from(start), Timestamp.from(start.plusSeconds(1800)), Timestamp.from(NOW), UUID.randomUUID().toString());
        mvc.perform(post("/api/v1/appointments/{id}/check-in", appointment).with(actor(receptionistAccount, AccountType.RECEPTIONIST))
                .with(csrf()).header("Idempotency-Key", "t029-checkin-" + appointment).header("X-Correlation-ID", CORRELATION)
                .contentType("application/json").content("{}"))
                .andExpect(status().isOk());
        return appointment;
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder statusChange(UUID appointment, UUID actor, AccountType type, String target) {
        return patch("/api/v1/appointments/{id}/status", appointment).with(actor(actor, type)).with(csrf())
                .header("X-Correlation-ID", CORRELATION).contentType("application/json")
                .content("{\"targetStatus\":\"" + target + "\"}");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder cancel(UUID appointment) {
        return post("/api/v1/appointments/{id}/cancel", appointment).with(actor(receptionistAccount, AccountType.RECEPTIONIST))
                .with(csrf()).header("X-Correlation-ID", CORRELATION).contentType("application/json")
                .content("{\"reason\":\"T029 cancellation\"}");
    }

    private int concurrentStatusChange(UUID appointment, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown(); if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("Race barrier timed out");
        return mvc.perform(statusChange(appointment, doctorAccount, AccountType.DOCTOR, "IN_PROGRESS")).andReturn().getResponse().getStatus();
    }
    private int concurrentCancel(UUID appointment, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown(); if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("Race barrier timed out");
        return mvc.perform(cancel(appointment)).andReturn().getResponse().getStatus();
    }

    private RequestPostProcessor actor(UUID id, AccountType type) {
        var principal = new AuthenticatedAccount(id, AccountRole.valueOf(type.name()), UUID.randomUUID());
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + type.name()))));
    }
    private UUID account(String type) {
        UUID id = UUID.randomUUID(); jdbc.update("insert into accounts (id, normalized_email, account_type, status, created_at, updated_at) values (?, ?, ?, 'ACTIVE', ?, ?)", id, "t029-" + type.toLowerCase() + "-" + id + "@example.test", type, Timestamp.from(NOW), Timestamp.from(NOW)); return id;
    }
    private UUID patient(UUID account) {
        UUID id = UUID.randomUUID(); jdbc.update("insert into patient_profiles (id, account_id, full_name, phone_canonical, phone_owner_type, date_of_birth, gender) values (?, ?, 'T029 Patient', ?, 'PATIENT', '1990-01-01', 'OTHER')", id, account, "+848" + UUID.randomUUID().toString().replace("-", "").substring(0, 9)); return id;
    }
    private UUID doctor(UUID account) {
        UUID id = UUID.randomUUID(); jdbc.update("insert into doctor_profiles (id, account_id, full_name, photo_url, work_contact, specialties, qualifications, biography, consultation_information, publicly_visible, accepting_new_bookings, in_active_directory) values (?, ?, 'T029 Doctor', '', 'private', '', '', '', '', true, true, true)", id, account); return id;
    }
    private void restoreTrigger(String table, String name, String mode) {
        if ("D".equals(mode)) jdbc.execute("alter table " + table + " disable trigger " + name);
        else if ("R".equals(mode)) jdbc.execute("alter table " + table + " enable replica trigger " + name);
        else if ("A".equals(mode)) jdbc.execute("alter table " + table + " enable always trigger " + name);
        else jdbc.execute("alter table " + table + " enable trigger " + name);
    }
    @TestConfiguration static class FixedClockConfiguration { @Bean @Primary MutableTestClock t029Clock() { return new MutableTestClock(NOW); } }
    static final class MutableTestClock extends Clock {
        private final AtomicReference<Instant> instant = new AtomicReference<>();
        MutableTestClock(Instant initial) { instant.set(initial); }
        void set(Instant value) { instant.set(value); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant.get(); }
    }
}
