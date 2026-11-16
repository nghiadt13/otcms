package fu.fa26.sep490.g58.otcms_be.appointment;

import fu.fa26.sep490.g58.otcms_be.appointment.service.impl.MarkNoShowAfterShiftJob;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Automatic no-show (BR-64) on the real database. The clock is 2026-11-11 12:00 clinic time (05:00Z). Shifts are in
 * UTC: doctor one and four work 01:00-04:00Z (08:00-11:00 clinic), doctor two works 01:00-06:00Z, doctor three has
 * no shift at all (the booking then falls back to the end of its clinic day).
 */
@SpringBootTest
@AutoConfigureMockMvc
@LocalPostgresTest
@Import(MarkNoShowAfterShiftJobPostgresIntegrationTest.FixedClockConfiguration.class)
class MarkNoShowAfterShiftJobPostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-11T05:00:00Z");
    private static final String CORRELATION = "5f0c1a7e-9d2b-4c3e-8a41-6b7d2e9f0a13";
    private static final String REASON = "Hết ca làm việc của bác sĩ, bệnh nhân không đến";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired MarkNoShowAfterShiftJob job;
    private final List<UUID> accounts = new ArrayList<>();
    private final List<UUID> doctorProfiles = new ArrayList<>();
    private UUID service, patientAccount, receptionistAccount, patientProfile;
    /** Shift ended at 04:00Z, not checked in: becomes NO_SHOW. */
    private UUID due;
    /** Shift runs until 06:00Z: still CONFIRMED. */
    private UUID running;
    /** Shift ended, but checked in: never touched. */
    private UUID checkedIn;
    /** No shift, previous clinic day: becomes NO_SHOW at that day's end. */
    private UUID pastDayNoShift;
    /** No shift, same clinic day: CONFIRMED until midnight. */
    private UUID sameDayNoShift;
    /** Shift ended and its audit insert is made to fail: stays CONFIRMED, does not stop the batch. */
    private UUID failing;
    /** Shift ended, same doctor as {@link #failing}: becomes NO_SHOW despite the failure. */
    private UUID dueAfterFailure;
    /** Started before the two-day look-back window: not scanned. */
    private UUID tooOld;

    @BeforeEach
    void setUp() throws Exception {
        patientAccount = account("PATIENT");
        receptionistAccount = account("RECEPTIONIST");
        patientProfile = patient(patientAccount);
        service = UUID.randomUUID();
        jdbc.update("insert into clinic_services (id, name, duration_minutes, buffer_minutes, online_bookable, active) "
                + "values (?, 'NS sweep', 30, 0, true, true)", service);
        UUID doctorOne = doctor("NS Doctor One");
        UUID doctorTwo = doctor("NS Doctor Two");
        UUID doctorThree = doctor("NS Doctor Three");
        UUID doctorFour = doctor("NS Doctor Four");
        shift(doctorOne, Instant.parse("2026-11-11T01:00:00Z"), Instant.parse("2026-11-11T04:00:00Z"));
        shift(doctorTwo, Instant.parse("2026-11-11T01:00:00Z"), Instant.parse("2026-11-11T06:00:00Z"));
        shift(doctorFour, Instant.parse("2026-11-11T01:00:00Z"), Instant.parse("2026-11-11T04:00:00Z"));
        due = booking(doctorOne, Instant.parse("2026-11-11T01:30:00Z"));
        running = booking(doctorTwo, Instant.parse("2026-11-11T03:00:00Z"));
        checkedIn = booking(doctorOne, Instant.parse("2026-11-11T03:00:00Z"));
        pastDayNoShift = booking(doctorThree, Instant.parse("2026-11-10T13:00:00Z"));
        sameDayNoShift = booking(doctorThree, Instant.parse("2026-11-11T03:00:00Z"));
        failing = booking(doctorFour, Instant.parse("2026-11-11T01:30:00Z"));
        dueAfterFailure = booking(doctorFour, Instant.parse("2026-11-11T02:30:00Z"));
        tooOld = booking(doctorThree, Instant.parse("2026-11-07T03:00:00Z"));
        mvc.perform(post("/api/v1/appointments/{id}/check-in", checkedIn).with(actor(receptionistAccount, AccountRole.RECEPTIONIST))
                        .with(csrf()).header("Idempotency-Key", "ns-checkin-" + checkedIn).header("X-Correlation-ID", CORRELATION)
                        .contentType("application/json").content("{\"verifiedPatientProfileId\":\"" + patientProfile + "\"}"))
                .andExpect(status().isOk());
    }

    @AfterEach
    void cleanUp() {
        if (service == null) return;
        String auditMode = triggerMode("audit_logs", "tr_audit_logs_immutable");


        jdbc.execute("alter table audit_logs disable trigger tr_audit_logs_immutable");
        try {
            jdbc.execute("drop trigger if exists tr_nosho_reject_audit on audit_logs");
            jdbc.execute("drop function if exists reject_nosho_sweep_audit()");
            List<UUID> visits = jdbc.query("select visit_id from appointments where service_id = ? and visit_id is not null",
                    (rs, row) -> rs.getObject("visit_id", UUID.class), service);
            jdbc.update("delete from audit_logs where source = 'appointment_audits' and entity_id in "
                    + "(select id from appointments where service_id = ?)", service);
            jdbc.update("update appointments set status = 'CANCELLED', visit_id = null where service_id = ?", service);
            for (UUID visit : visits) {
                jdbc.update("delete from queue_entries where stage = 'EXAM' and visit_id = ?", visit);
                jdbc.update("delete from audit_logs where source = 'visit_assignment_audits' and record_label = ?::text", visit);
                jdbc.update("delete from visit_assignments where visit_id = ?", visit);

                jdbc.update("delete from audit_logs where source = 'visit_audits' and record_label = ?::text", visit);
                jdbc.update("delete from visits where id = ?", visit);
            }
            jdbc.update("delete from appointments where service_id = ?", service);
            for (UUID profile : doctorProfiles) {
                jdbc.update("delete from queue_entries where stage = 'EXAM' and doctor_profile_id = ?", profile);
                jdbc.update("delete from work_shifts where staff_profile_id = ?", profile);
                jdbc.update("delete from doctor_services where doctor_profile_id = ?", profile);
                jdbc.update("delete from staff_profiles where id = ?", profile);
            }
            jdbc.update("delete from patient_profiles where id = ?", patientProfile);
            jdbc.update("delete from clinic_services where id = ?", service);
            for (UUID id : accounts) jdbc.update("delete from accounts where id = ?", id);
        } finally {
            restoreTrigger("audit_logs", "tr_audit_logs_immutable", auditMode);
        }
    }

    @Test
    void bookingWhoseShiftEndedBecomesNoShowWithSystemAuditAttributedToCreator() {
        job.sweepOnce();

        assertEquals("NO_SHOW", statusOf(due));
        assertEquals("NO_SHOW", statusOf(pastDayNoShift));
        assertEquals("NO_SHOW", statusOf(dueAfterFailure));
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_logs where source = 'appointment_audits' and entity_id = ? "
                + "and actor_type = 'SYSTEM' and action = 'STATUS_CHANGED' and before_state->>'status' = 'CONFIRMED' "
                + "and after_state->>'status' = 'NO_SHOW' and actor_account_id = ? and metadata->>'reason' = ? and correlation_id = ?", Integer.class,
                due, patientAccount, REASON, "no-show-sweep-" + due));
        assertNull(jdbc.queryForObject("select visit_id from appointments where id = ?", UUID.class, due));
    }

    @Test
    void bookingsStillInsideTheirShiftOrOutsideTheSweepWindowStayConfirmed() {
        job.sweepOnce();

        assertEquals("CONFIRMED", statusOf(running));
        assertEquals("CONFIRMED", statusOf(sameDayNoShift));
        assertEquals("CONFIRMED", statusOf(tooOld));
    }

    @Test
    void checkedInBookingIsNeverSweptEvenAfterItsShiftEnded() {
        job.sweepOnce();

        assertEquals("CHECKED_IN", statusOf(checkedIn));
        assertEquals(0, jdbc.queryForObject("select count(*) from audit_logs where source = 'appointment_audits' and entity_id = ? "
                + "and after_state->>'status' = 'NO_SHOW'", Integer.class, checkedIn));
    }

    @Test
    void failingBookingStaysConfirmedAndDoesNotStopTheBatch() {
        jdbc.execute("drop trigger if exists tr_nosho_reject_audit on audit_logs");
        jdbc.execute("drop function if exists reject_nosho_sweep_audit()");
        jdbc.execute("create function reject_nosho_sweep_audit() returns trigger language plpgsql as $$ begin "
                + "if new.entity_id = '" + failing + "' and new.after_state->>'status' = 'NO_SHOW' then "
                + "raise exception 'injected no-show audit failure'; end if; return new; end $$");
        jdbc.execute("create trigger tr_nosho_reject_audit before insert on audit_logs "
                + "for each row execute function reject_nosho_sweep_audit()");
        try {
            job.sweepOnce();
        } finally {
            jdbc.execute("drop trigger if exists tr_nosho_reject_audit on audit_logs");
            jdbc.execute("drop function if exists reject_nosho_sweep_audit()");
        }

        assertEquals("CONFIRMED", statusOf(failing));
        assertEquals(0, jdbc.queryForObject("select count(*) from audit_logs where source = 'appointment_audits' and entity_id = ? "
                + "and after_state->>'status' = 'NO_SHOW'", Integer.class, failing));
        assertEquals("NO_SHOW", statusOf(dueAfterFailure));
    }

    @Test
    void backgroundSweepStaysOffInTests() {
        job.scheduledSweep();

        assertEquals("CONFIRMED", statusOf(due));
    }

    private String statusOf(UUID appointment) {
        return jdbc.queryForObject("select status from appointments where id = ?", String.class, appointment);
    }

    private UUID account(String type) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into accounts (id, normalized_email, account_type, status, created_at, updated_at) "
                        + "values (?, ?, ?, 'ACTIVE', ?, ?)", id, "nosweep-" + type.toLowerCase() + "-" + id + "@example.test",
                type, Timestamp.from(NOW), Timestamp.from(NOW));
        accounts.add(id);
        return id;
    }

    private UUID patient(UUID account) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into patient_profiles (id, account_id, full_name, phone_canonical, phone_owner_type, "
                        + "date_of_birth, gender) values (?, ?, 'NS Patient', ?, 'PATIENT', '1990-01-01', 'OTHER')",
                id, account, "+848" + UUID.randomUUID().toString().replace("-", "").substring(0, 9));
        return id;
    }

    private UUID doctor(String name) {
        UUID account = account("DOCTOR");
        UUID profile = UUID.randomUUID();
        jdbc.update("insert into staff_profiles (is_doctor, id, account_id, display_name, photo_url, work_contact, specialties, "
                        + "qualifications, biography, consultation_information, publicly_visible, accepting_new_bookings, "
                        + "in_active_directory) values (true, ?, ?, ?, '', 'private', '', '', '', '', true, true, true)",
                profile, account, name);
        jdbc.update("insert into doctor_services (doctor_profile_id, service_id, active) values (?, ?, true)", profile, service);
        doctorProfiles.add(profile);
        return profile;
    }

    private void shift(UUID doctor, Instant from, Instant to) {
        jdbc.update("insert into work_shifts (id, staff_profile_id, starts_at, ends_at) values (?, ?, ?, ?)",
                UUID.randomUUID(), doctor, Timestamp.from(from), Timestamp.from(to));
    }

    private UUID booking(UUID doctor, Instant start) {
        UUID id = UUID.randomUUID();
        Instant end = start.plusSeconds(1800);
        jdbc.update("insert into appointments (id, patient_profile_id, service_id, doctor_profile_id, status, "
                        + "booking_source, purpose, start_time, end_time, duration_minutes, buffer_minutes, created_by_account_id) "
                        + "values (?, ?, ?, ?, 'CONFIRMED', 'PATIENT_ONLINE', 'NS purpose', ?, ?, 30, 0, ?)",
                id, patientProfile, service, doctor, Timestamp.from(start), Timestamp.from(end), patientAccount);
        jdbc.update("insert into audit_logs (source, category, action, record_type, entity_id, record_label, "
                + "actor_account_id, actor_type, after_state, occurred_at, correlation_id) values "
                + "('appointment_audits', 'APPOINTMENT', 'CREATED', 'APPOINTMENT', ?, ?, ?, 'PATIENT', "
                + "jsonb_build_object('status', 'CONFIRMED', 'doctorProfileId', cast(? as uuid), "
                + "'startTime', cast(? as timestamptz), 'endTime', cast(? as timestamptz)), ?, ?)",
                id, id.toString(), patientAccount, doctor, Timestamp.from(start), Timestamp.from(end), Timestamp.from(NOW),
                UUID.randomUUID().toString());
        return id;
    }

    private String triggerMode(String table, String trigger) {
        return jdbc.queryForObject("select tgenabled::text from pg_trigger where tgrelid = '" + table
                + "'::regclass and tgname = '" + trigger + "'", String.class);
    }

    private void restoreTrigger(String table, String name, String mode) {
        if ("D".equals(mode)) jdbc.execute("alter table " + table + " disable trigger " + name);
        else if ("R".equals(mode)) jdbc.execute("alter table " + table + " enable replica trigger " + name);
        else if ("A".equals(mode)) jdbc.execute("alter table " + table + " enable always trigger " + name);
        else jdbc.execute("alter table " + table + " enable trigger " + name);
    }

    private RequestPostProcessor actor(UUID id, AccountRole role) {
        var principal = new AuthenticatedAccount(id, role, UUID.randomUUID());
        return authentication(new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Primary @Bean Clock noShowSweepClock() { return Clock.fixed(NOW, ZoneId.of("UTC")); }
    }
}
