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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@LocalPostgresTest
@Import(CheckInAppointmentT027PostgresIntegrationTest.FixedClockConfiguration.class)
class CheckInAppointmentT027PostgresIntegrationTest {
    // 12:00 in clinic time (UTC+7) on 2026-10-14; the default appointment starts at 12:30 the same day.
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
        jdbc.update("insert into doctor_services (doctor_profile_id, service_id, active) values (?, ?, true)",
                doctorProfile, service);
        appointment = insertAppointment(NOW.plusSeconds(1800), "T027 same clinic day");
    }

    @AfterEach
    void cleanUp() {
        String auditTrigger = jdbc.queryForObject("select tgenabled::text from pg_trigger "
                + "where tgrelid = 'audit_logs'::regclass and tgname = 'tr_audit_logs_immutable'", String.class);
        jdbc.execute("alter table audit_logs disable trigger tr_audit_logs_immutable");
        try {
            jdbc.execute("drop trigger if exists tr_t027_reject_audit on audit_logs");
            jdbc.execute("drop function if exists reject_t027_checkin_audit()");
            for (UUID appointmentId : appointments) {
                jdbc.update("delete from audit_logs where source = 'appointment_audits' and entity_id = ?", appointmentId);
                jdbc.update("update appointments set status = 'CANCELLED', visit_id = null where id = ?", appointmentId);
                jdbc.update("delete from payment_attempts where invoice_id in "
                        + "(select id from invoices where appointment_id = ?)", appointmentId);
                jdbc.update("delete from invoice_items where invoice_id in "
                        + "(select id from invoices where appointment_id = ?)", appointmentId);
                jdbc.update("delete from invoices where appointment_id = ?", appointmentId);
                List<UUID> visits = jdbc.query("select id from visits where originating_appointment_id = ?",
                        (rs, row) -> rs.getObject("id", UUID.class), appointmentId);

                jdbc.update("delete from queue_entries where stage = 'EXAM' and visit_id in (select id from visits where originating_appointment_id = ?)", appointmentId);
                jdbc.update("delete from visits where originating_appointment_id = ?", appointmentId);
                jdbc.update("delete from appointments where id = ?", appointmentId);
            }
            if (doctorProfile != null) jdbc.update("delete from doctor_services where doctor_profile_id = ?", doctorProfile);
            if (service != null) jdbc.update("delete from clinic_services where id = ?", service);
            if (doctorProfile != null) jdbc.update("delete from queue_entries where stage = 'EXAM' and doctor_profile_id = ?", doctorProfile);
            if (doctorProfile != null) jdbc.update("delete from staff_profiles where id = ?", doctorProfile);
            if (patientProfile != null) jdbc.update("delete from patient_profiles where id = ?", patientProfile);
            for (UUID id : new UUID[]{patientAccount, receptionistAccount, doctorAccount}) {
                if (id != null) jdbc.update("delete from accounts where id = ?", id);
            }
        } finally {
            restoreTrigger("audit_logs", "tr_audit_logs_immutable", auditTrigger);
        }
    }

    @Test
    void duplicateCheckInWithSameKeyReturnsStoredVisit() throws Exception {
        var first = mvc.perform(checkIn(appointment, receptionistAccount, "p04-replay"))
                .andExpect(status().isOk()).andReturn();
        UUID visit = jdbc.queryForObject("select visit_id from appointments where id = ?", UUID.class, appointment);
        mvc.perform(checkIn(appointment, receptionistAccount, "p04-replay"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.visitId").value(visit.toString()))
                .andExpect(jsonPath("$.checkedInAt").value(NOW.toString()));
        assertEquals(1, jdbc.queryForObject("select count(*) from visits where originating_appointment_id = ?",
                Integer.class, appointment));
        assertEquals(visit.toString(), new tools.jackson.databind.ObjectMapper()
                .readTree(first.getResponse().getContentAsString()).get("visitId").asString());
    }

    @Test
    void sameKeyOnAnotherAppointmentIsConflict() throws Exception {
        UUID second = insertAppointment(NOW.plusSeconds(5400), "P04 second appointment");
        mvc.perform(checkIn(appointment, receptionistAccount, "p04-conflict")).andExpect(status().isOk());
        mvc.perform(checkIn(second, receptionistAccount, "p04-conflict"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_CONFLICT"));
        assertEquals(0, jdbc.queryForObject("select count(*) from visits where originating_appointment_id = ?",
                Integer.class, second));
    }

    @Test
    void patientIdentityProjectionIsPurposeLimitedAndReceptionistOnly() throws Exception {
        mvc.perform(get("/api/v1/patient-profiles/{id}/reception-verification", patientProfile)
                        .with(actor(receptionistAccount, AccountType.RECEPTIONIST)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.patientProfileId").value(patientProfile.toString()))
                .andExpect(jsonPath("$.fullName").value("T027 Patient"))
                .andExpect(jsonPath("$.dateOfBirth").value("1990-01-01"))
                .andExpect(jsonPath("$.phoneCanonical").doesNotExist())
                .andExpect(jsonPath("$.gender").doesNotExist());
        mvc.perform(get("/api/v1/patient-profiles/{id}/reception-verification", patientProfile)
                        .with(actor(patientAccount, AccountType.PATIENT)))
                .andExpect(status().isForbidden());
    }

    @Test
    void checksInOnTheClinicDayAndReplayAfterProgressionDoesNotDuplicateEvidence() throws Exception {
        Instant arrivedAt = NOW;
        jdbc.update("update appointments set arrival_recorded_by = ?, arrival_source = 'LEGACY_RECEPTIONIST', arrived_at = ?, "
                + "arrival_recorded_at = now() where id = ?", receptionistAccount, Timestamp.from(arrivedAt), appointment);

        mvc.perform(checkIn(appointment, receptionistAccount, "t027-success"))
                .andExpect(status().isOk()).andExpect(header().string("X-Correlation-ID", CORRELATION))
                .andExpect(jsonPath("$.appointmentId").value(appointment.toString()))
                .andExpect(jsonPath("$.checkedInAt").value(NOW.toString()))
                .andExpect(jsonPath("$.queueNumber").value(1))
                .andExpect(jsonPath("$.doctorName").value("T027 Doctor"));
        UUID visitId = jdbc.queryForObject("select visit_id from appointments where id = ?", UUID.class, appointment);
        assertEquals("CHECKED_IN", jdbc.queryForObject("select status from appointments where id = ?", String.class, appointment));
        assertEquals(patientProfile, jdbc.queryForObject("select patient_profile_id from visits where id = ?", UUID.class, visitId));
        assertEquals("SCHEDULED", jdbc.queryForObject("select source from visits where id = ?", String.class, visitId));
        assertEquals(arrivedAt, jdbc.queryForObject("select arrived_at from visits where id = ?", Timestamp.class, visitId).toInstant());
        assertEquals(1, jdbc.queryForObject("select count(*) from visits where id = ? and source = 'SCHEDULED' and created_by_account_id = ?", Integer.class, visitId, receptionistAccount));
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_logs where source = 'appointment_audits' and entity_id = ? "
                + "and action = 'STATUS_CHANGED' and before_state->>'status' = 'CONFIRMED' and after_state->>'status' = 'CHECKED_IN' "
                + "and (after_state->>'visitId')::uuid = ? and actor_account_id = ? and correlation_id = ? "
                + "and (metadata->>'verifiedPatientProfileId')::uuid = ?", Integer.class,
                appointment, visitId, receptionistAccount, CORRELATION, patientProfile));
        assertEquals(1, jdbc.queryForObject("select count(*) from appointments where id = ? and checkin_idempotency_key is not null "
                + "and checkin_idempotency_key = 't027-success' and visit_id = ?", Integer.class, appointment, visitId));
        assertEquals(1, jdbc.queryForObject("select count(*) from queue_entries where stage = 'EXAM' and visit_id = ? and doctor_profile_id = ? and status = 'WAITING' and queue_number = 1", Integer.class, visitId, doctorProfile));

        jdbc.update("update appointments set status = 'IN_PROGRESS' where id = ?", appointment);
        testClock.set(NOW.plusSeconds(7200));
        mvc.perform(checkIn(appointment, receptionistAccount, "t027-success"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.visitId").value(visitId.toString()))
                .andExpect(jsonPath("$.checkedInAt").value(NOW.toString()))
                .andExpect(jsonPath("$.queueNumber").value(1));
        assertEquals("IN_PROGRESS", jdbc.queryForObject("select status from appointments where id = ?", String.class, appointment));
        assertEquals(1, jdbc.queryForObject("select count(*) from visits where originating_appointment_id = ?", Integer.class, appointment));
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_logs where source = 'appointment_audits' and entity_id = ? and action = 'STATUS_CHANGED'", Integer.class, appointment));
        assertEquals(1, jdbc.queryForObject("select count(*) from appointments where id = ? and checkin_idempotency_key is not null", Integer.class, appointment));
        assertEquals(1, jdbc.queryForObject("select count(*) from queue_entries where stage = 'EXAM' and visit_id = ?", Integer.class, visitId));
    }

    @Test
    void lateSameDayCheckInIsAllowedWithoutMinuteWindowAndEntersTheExamQueue() throws Exception {
        // 12:45 clinic time: past the old +15 minute grace, still the appointment's clinic day.
        Instant lateArrival = NOW.plusSeconds(2700);
        jdbc.update("update appointments set arrival_recorded_by = ?, arrival_source = 'PATIENT_QR', arrived_at = ?, "
                + "arrival_recorded_at = now() where id = ?", patientAccount, Timestamp.from(lateArrival), appointment);
        testClock.set(lateArrival.plusSeconds(1));

        mvc.perform(checkIn(appointment, receptionistAccount, "t027-delayed-staff"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.checkedInAt").value(lateArrival.plusSeconds(1).toString()))
                .andExpect(jsonPath("$.queueNumber").value(1));

        UUID visitId = jdbc.queryForObject("select visit_id from appointments where id = ?", UUID.class, appointment);
        assertEquals(lateArrival, jdbc.queryForObject("select arrived_at from visits where id = ?", Timestamp.class, visitId).toInstant());
        assertEquals(0, jdbc.queryForObject("select count(*) from queue_entries where stage = 'RECEPTION' and visit_id = ?", Integer.class, visitId));
        assertEquals("WAITING", jdbc.queryForObject("select status from queue_entries where stage = 'EXAM' and visit_id = ?", String.class, visitId));
    }

    @Test
    void appointmentsWithTheSameDoctorGetConsecutiveQueueNumbersInCheckInOrder() throws Exception {
        // 13:30 clinic time: clear of the default appointment's occupied range (12:30 to 13:05) under the doctor exclusion.
        UUID second = insertAppointment(NOW.plusSeconds(5400), "T027 second with same doctor");

        mvc.perform(checkIn(second, receptionistAccount, "t027-queue-second"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.queueNumber").value(1));
        mvc.perform(checkIn(appointment, receptionistAccount, "t027-queue-first"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.queueNumber").value(2));

        assertEquals(List.of(1, 2), jdbc.queryForList("select queue_number from queue_entries where stage = 'EXAM' and doctor_profile_id = ? order by queue_number", Integer.class, doctorProfile));
    }

    @Test
    void examFeeMustBePaidBeforeTheVisitJoinsTheExamQueue() throws Exception {
        jdbc.update("update clinic_services set price = 250000 where id = ?", service);

        mvc.perform(checkIn(appointment, receptionistAccount, "t027-fee-unpaid"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EXAM_FEE_UNPAID"));
        assertEquals("CONFIRMED", jdbc.queryForObject("select status from appointments where id = ?", String.class, appointment));
        assertEquals(0, jdbc.queryForObject("select count(*) from visits where originating_appointment_id = ?", Integer.class, appointment));
        assertEquals(0, jdbc.queryForObject("select count(*) from appointments where id = ? and checkin_idempotency_key is not null", Integer.class, appointment));

        // The receptionist collects the fee at the counter with the existing billing endpoints, then checks in again.
        mvc.perform(post("/api/v1/appointments/{id}/invoices", appointment)
                        .with(actor(receptionistAccount, AccountType.RECEPTIONIST)).with(csrf()))
                .andExpect(status().isOk());
        UUID invoice = jdbc.queryForObject("select id from invoices where appointment_id = ?", UUID.class, appointment);
        mvc.perform(post("/api/v1/invoices/{id}/counter-payments", invoice)
                        .with(actor(receptionistAccount, AccountType.RECEPTIONIST)).with(csrf())
                        .header("Idempotency-Key", "t027-fee-counter").contentType("application/json")
                        .content("{\"amount\":250000,\"method\":\"CASH\"}"))
                .andExpect(status().isOk());

        mvc.perform(checkIn(appointment, receptionistAccount, "t027-fee-paid"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.queueNumber").value(1));
        assertEquals("CHECKED_IN", jdbc.queryForObject("select status from appointments where id = ?", String.class, appointment));
    }

    @Test
    void rejectsAnotherClinicDayWithoutWritesAndAcceptsSameDayEarlyCheckIn() throws Exception {
        appointment = insertAppointment(Instant.parse("2026-10-15T03:00:00Z"), "T027 next clinic day");
        mvc.perform(checkIn(appointment, receptionistAccount, "t027-next-day"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("CHECK_IN_TIME_NOT_ALLOWED"));
        assertEquals("CONFIRMED", jdbc.queryForObject("select status from appointments where id = ?", String.class, appointment));
        assertEquals(0, jdbc.queryForObject("select count(*) from visits where originating_appointment_id = ?", Integer.class, appointment));
        assertEquals(0, jdbc.queryForObject("select count(*) from appointments where id = ? and checkin_idempotency_key is not null", Integer.class, appointment));
        assertEquals(0, jdbc.queryForObject("select count(*) from audit_logs where source = 'appointment_audits' and entity_id = ? "
                + "and after_state->>'status' = 'CHECKED_IN'", Integer.class, appointment));

        // 07:00 clinic time for a 15:00 appointment of the same clinic day is accepted.
        appointment = insertAppointment(Instant.parse("2026-10-14T08:00:00Z"), "T027 same day afternoon");
        testClock.set(Instant.parse("2026-10-14T00:00:00Z"));
        mvc.perform(checkIn(appointment, receptionistAccount, "t027-early-morning"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkedInAt").value("2026-10-14T00:00:00Z"));
    }

    @Test
    void sameIdempotencyKeyWithChangedAttestedPatientConflicts() throws Exception {
        mvc.perform(checkIn(appointment, receptionistAccount, "t027-attestation-key"))
                .andExpect(status().isOk());
        mvc.perform(checkIn(appointment, receptionistAccount, "t027-attestation-key", UUID.randomUUID()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_CONFLICT"));
        assertEquals(1, jdbc.queryForObject("select count(*) from visits where originating_appointment_id = ?", Integer.class, appointment));
    }

    @Test
    void mismatchedPatientAttestationRejectsWithoutAnyCheckInWrites() throws Exception {
        mvc.perform(checkIn(appointment, receptionistAccount, "t027-mismatched-attestation", UUID.randomUUID()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("PATIENT_IDENTITY_MISMATCH"));

        assertEquals("CONFIRMED", jdbc.queryForObject("select status from appointments where id = ?", String.class, appointment));
        assertEquals(null, jdbc.queryForObject("select visit_id from appointments where id = ?", UUID.class, appointment));
        assertEquals(0, jdbc.queryForObject("select count(*) from visits where originating_appointment_id = ?", Integer.class, appointment));
        assertEquals(0, jdbc.queryForObject("select count(*) from visits v where v.originating_appointment_id = ?", Integer.class, appointment));
        assertEquals(0, jdbc.queryForObject("select count(*) from audit_logs where source = 'appointment_audits' and entity_id = ? "
                + "and after_state->>'status' = 'CHECKED_IN'", Integer.class, appointment));
        assertEquals(0, jdbc.queryForObject("select count(*) from appointments where id = ? and checkin_idempotency_key is not null", Integer.class, appointment));
        assertEquals(0, jdbc.queryForObject("select count(*) from queue_entries q join visits v on v.id = q.visit_id where q.stage = 'RECEPTION' and v.originating_appointment_id = ?", Integer.class, appointment));
        assertEquals(0, jdbc.queryForObject("select count(*) from queue_entries q join visits v on v.id = q.visit_id where q.stage = 'EXAM' and v.originating_appointment_id = ?", Integer.class, appointment));
    }

    @Test
    void rejectsNonReceptionistAndDifferentKeyAfterCheckInWithoutCreatingDuplicate() throws Exception {
        mvc.perform(checkIn(appointment, patientAccount, "t027-forbidden"))
                .andExpect(status().isForbidden());
        mvc.perform(checkIn(appointment, receptionistAccount, "t027-first"))
                .andExpect(status().isOk());
        mvc.perform(checkIn(appointment, receptionistAccount, "t027-other-key"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("APPOINTMENT_NOT_CHANGEABLE"));
        assertEquals(1, jdbc.queryForObject("select count(*) from visits where originating_appointment_id = ?", Integer.class, appointment));
        assertEquals(1, jdbc.queryForObject("select count(*) from appointments where id = ? and checkin_idempotency_key is not null", Integer.class, appointment));
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_logs where source = 'appointment_audits' and entity_id = ? and action = 'STATUS_CHANGED'", Integer.class, appointment));
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
            assertEquals(1, jdbc.queryForObject("select count(*) from visits where originating_appointment_id = ?", Integer.class, appointment));
            assertEquals(1, jdbc.queryForObject("select count(*) from visits v where v.originating_appointment_id = ?", Integer.class, appointment));
            assertEquals(1, jdbc.queryForObject("select count(*) from audit_logs where source = 'appointment_audits' and entity_id = ? "
                    + "and action = 'STATUS_CHANGED' and after_state->>'status' = 'CHECKED_IN'", Integer.class, appointment));
            assertEquals(1, jdbc.queryForObject("select count(*) from appointments where id = ? and checkin_idempotency_key is not null", Integer.class, appointment));
            assertEquals(1, jdbc.queryForObject("select count(*) from queue_entries q join visits v on v.id = q.visit_id where q.stage = 'EXAM' and v.originating_appointment_id = ?", Integer.class, appointment));
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
                + "begin if new.correlation_id = '" + CORRELATION + "' and new.after_state->>'status' = 'CHECKED_IN' "
                + "then raise exception 'T027 injected audit failure'; end if; return new; end $$");
        jdbc.execute("create trigger tr_t027_reject_audit before insert on audit_logs "
                + "for each row execute function reject_t027_checkin_audit()");

        mvc.perform(checkIn(appointment, receptionistAccount, "t027-rollback"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("DEPENDENCY_UNAVAILABLE"));
        assertEquals("CONFIRMED", jdbc.queryForObject("select status from appointments where id = ?", String.class, appointment));
        assertEquals(null, jdbc.queryForObject("select visit_id from appointments where id = ?", UUID.class, appointment));
        assertEquals(0, jdbc.queryForObject("select count(*) from visits where originating_appointment_id = ?", Integer.class, appointment));
        assertEquals(0, jdbc.queryForObject("select count(*) from visits v where v.originating_appointment_id = ?", Integer.class, appointment));
        assertEquals(0, jdbc.queryForObject("select count(*) from appointments where id = ? and checkin_idempotency_key is not null", Integer.class, appointment));
        assertEquals(0, jdbc.queryForObject("select count(*) from audit_logs where source = 'appointment_audits' and entity_id = ? "
                + "and after_state->>'status' = 'CHECKED_IN'", Integer.class, appointment));
        assertEquals(0, jdbc.queryForObject("select count(*) from queue_entries q join visits v on v.id = q.visit_id where q.stage = 'EXAM' and v.originating_appointment_id = ?", Integer.class, appointment));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder checkIn(
            UUID appointmentId, UUID actor, String key) {
        return checkIn(appointmentId, actor, key, patientProfile);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder checkIn(
            UUID appointmentId, UUID actor, String key, UUID attestedPatientProfile) {
        return post("/api/v1/appointments/{id}/check-in", appointmentId)
                .with(actor(actor, actor.equals(receptionistAccount) ? AccountType.RECEPTIONIST : AccountType.PATIENT))
                .with(csrf()).header("Idempotency-Key", key).header("X-Correlation-ID", CORRELATION)
                .contentType("application/json").content("{\"verifiedPatientProfileId\":\"" + attestedPatientProfile + "\"}");
    }

    private UUID insertAppointment(Instant start, String purpose) {
        UUID id = UUID.randomUUID();
        appointments.add(id);
        jdbc.update("insert into appointments (id, patient_profile_id, service_id, doctor_profile_id, status, "
                        + "booking_source, purpose, start_time, end_time, duration_minutes, buffer_minutes, created_by_account_id) "
                        + "values (?, ?, ?, ?, 'CONFIRMED', 'PATIENT_ONLINE', ?, ?, ?, 30, 5, ?)",
                id, patientProfile, service, doctorProfile, purpose, Timestamp.from(start), Timestamp.from(start.plusSeconds(1800)), patientAccount);
        jdbc.update("insert into audit_logs (source, category, action, record_type, entity_id, record_label, "
                        + "actor_account_id, actor_type, after_state, occurred_at, correlation_id) "
                        + "values ('appointment_audits', 'APPOINTMENT', 'CREATED', 'APPOINTMENT', ?, ?, ?, 'PATIENT', "
                        + "jsonb_build_object('status', 'CONFIRMED', 'doctorProfileId', cast(? as uuid), "
                        + "'startTime', cast(? as timestamptz), 'endTime', cast(? as timestamptz)), ?, ?)",
                id, id.toString(), patientAccount, doctorProfile, Timestamp.from(start), Timestamp.from(start.plusSeconds(1800)),
                Timestamp.from(NOW), UUID.randomUUID().toString());
        return id;
    }

    private UUID doctor(UUID account) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into staff_profiles (is_doctor, id, account_id, display_name, photo_url, work_contact, specialties, "
                        + "qualifications, biography, consultation_information, publicly_visible, accepting_new_bookings, in_active_directory) "
                        + "values (true, ?, ?, 'T027 Doctor', '', 'private', '', '', '', '', true, true, true)", id, account);
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
