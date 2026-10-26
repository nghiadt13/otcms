package fu.fa26.sep490.g58.otcms_be.visit;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@LocalPostgresTest
@Import(CreateWalkInVisitT043PostgresIntegrationTest.FixedClockConfiguration.class)
class CreateWalkInVisitT043PostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-18T15:00:00Z");
    private static final String CORRELATION = "a4d61fa1-2d15-42d3-bf7b-e04e8901257d";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableTestClock testClock;

    private UUID receptionist;
    private UUID patientAccount;
    private UUID otherPatientAccount;
    private UUID patientProfile;
    private UUID otherPatientProfile;
    private UUID serviceId;
    private UUID doctorAccount;
    private UUID doctorProfile;
    private final List<UUID> visitIds = new ArrayList<>();
    private final List<UUID> appointmentIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        purgeStaleT043Fixtures();
        testClock.set(NOW);
        receptionist = account(AccountType.RECEPTIONIST, "receptionist");
        patientAccount = account(AccountType.PATIENT, "patient");
        otherPatientAccount = account(AccountType.PATIENT, "other-patient");
        doctorAccount = account(AccountType.DOCTOR, "doctor");
        patientProfile = patient(patientAccount, "T043 Patient");
        otherPatientProfile = patient(otherPatientAccount, "T043 Other Patient");

        serviceId = UUID.randomUUID();
        jdbc.update("insert into clinic_services (id, name, duration_minutes, buffer_minutes, online_bookable, active) "
                + "values (?, 'T043 Service', 30, 5, true, true)", serviceId);
        doctorProfile = UUID.randomUUID();
        jdbc.update("insert into doctor_profiles (id, account_id, full_name, photo_url, work_contact, specialties, "
                        + "qualifications, biography, consultation_information, publicly_visible, accepting_new_bookings, in_active_directory) "
                        + "values (?, ?, 'T043 Doctor', '', 'private', '', '', '', '', true, true, true)",
                doctorProfile, doctorAccount);
        jdbc.update("insert into doctor_services (doctor_profile_id, service_id, active) values (?, ?, true)",
                doctorProfile, serviceId);
    }

    @Test
    void concurrentWalkInReplayCreatesExactlyOneVisitAndReception() throws Exception {
        UUID sharedReceptionist = receptionist;
        UUID sharedPatient = patientProfile;
        String idempotencyKey = "t033-race-" + UUID.randomUUID();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            var first = workers.submit(() -> raceWalkIn(sharedReceptionist, sharedPatient, idempotencyKey, ready, start, failure));
            var second = workers.submit(() -> raceWalkIn(sharedReceptionist, sharedPatient, idempotencyKey, ready, start, failure));
            org.junit.jupiter.api.Assertions.assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
            if (failure.get() != null) throw new AssertionError("Concurrent WALK_IN replay failed", failure.get());
            assertEquals(1, jdbc.queryForObject("select count(*) from walk_in_requests "
                    + "where receptionist_account_id = ? and idempotency_key = ?", Integer.class,
                    sharedReceptionist, idempotencyKey));
            UUID createdVisit = jdbc.queryForObject("select visit_id from walk_in_requests "
                    + "where receptionist_account_id = ? and idempotency_key = ?", UUID.class,
                    sharedReceptionist, idempotencyKey);
            assertEquals(1, jdbc.queryForObject("select count(*) from visits where id = ? "
                    + "and patient_profile_id = ? and created_by_account_id = ? and arrived_at = ?", Integer.class,
                    createdVisit, sharedPatient, sharedReceptionist, Timestamp.from(NOW)));
            visitIds.add(createdVisit);
        } finally {
            start.countDown();
            workers.shutdownNow();
        }
    }

    private void raceWalkIn(UUID actor, UUID patient, String key, CountDownLatch ready, CountDownLatch start,
            AtomicReference<Throwable> failure) {
        ready.countDown();
        try {
            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Barrier timed out");
            mvc.perform(post("/api/v1/visits/walk-ins").with(csrf()).with(actor(actor, AccountType.RECEPTIONIST))
                    .header("Idempotency-Key", key).contentType("application/json")
                    .content("{\"patientProfileId\":\"" + patient + "\"}"))
                    .andExpect(status().isCreated());
        } catch (Throwable error) {
            failure.compareAndSet(null, error);
        }
    }

    @AfterEach
    void cleanUp() {
        jdbc.execute("drop trigger if exists tr_t043_fail_audit on visit_audits");
        jdbc.execute("drop function if exists reject_t043_audit_insert()");
        String triggerMode = jdbc.queryForObject("select tgenabled::text from pg_trigger "
                + "where tgrelid = 'visit_audits'::regclass and tgname = 'tr_visit_audits_immutable'", String.class);
        String appointmentAuditTriggerMode = null;
        if (!appointmentIds.isEmpty()) {
            appointmentAuditTriggerMode = jdbc.queryForObject("select tgenabled::text from pg_trigger "
                    + "where tgrelid = 'appointment_audits'::regclass and tgname = 'tr_appointment_audits_immutable'",
                    String.class);
            jdbc.execute("alter table appointment_audits disable trigger tr_appointment_audits_immutable");
        }
        jdbc.execute("alter table visit_audits disable trigger tr_visit_audits_immutable");
        jdbc.execute("alter table visit_audits disable trigger tr_visit_audits_no_truncate");
        try {
            for (UUID visitId : visitIds) {
                jdbc.update("delete from walk_in_requests where visit_id = ?", visitId);
                jdbc.update("delete from visit_check_ins where visit_id = ?", visitId);
                jdbc.update("delete from visit_audits where visit_id = ?", visitId);
                jdbc.update("delete from visits where id = ?", visitId);
            }
            for (UUID appointmentId : appointmentIds) {
                jdbc.update("delete from appointment_audits where appointment_id = ?", appointmentId);
                jdbc.update("delete from appointments where id = ?", appointmentId);
            }
            if (doctorProfile != null) jdbc.update("delete from doctor_services where doctor_profile_id = ?", doctorProfile);
            if (serviceId != null) jdbc.update("delete from clinic_services where id = ?", serviceId);
            if (doctorProfile != null) jdbc.update("delete from doctor_profiles where id = ?", doctorProfile);
            if (patientProfile != null) jdbc.update("delete from patient_profiles where id = ?", patientProfile);
            if (otherPatientProfile != null) jdbc.update("delete from patient_profiles where id = ?", otherPatientProfile);
            for (UUID id : new UUID[]{patientAccount, otherPatientAccount, doctorAccount, receptionist}) {
                if (id != null) jdbc.update("delete from accounts where id = ?", id);
            }
        } finally {
            restoreTrigger("tr_visit_audits_immutable", triggerMode);
            jdbc.execute("alter table visit_audits enable trigger tr_visit_audits_no_truncate");
            if (appointmentAuditTriggerMode != null) {
                restoreAppointmentAuditTrigger(appointmentAuditTriggerMode);
            }
        }
    }

    @Test
    void createsWalkInAndFourEvidenceRowsAtomicallyWithServerArrivalTime() throws Exception {
        mvc.perform(create(patientProfile, null, "t043-create", receptionist, AccountType.RECEPTIONIST))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Correlation-ID", CORRELATION))
                .andExpect(jsonPath("$.visitId").isNotEmpty())
                .andExpect(jsonPath("$.patientProfileId").value(patientProfile.toString()))
                .andExpect(jsonPath("$.priorAppointmentId").doesNotExist())
                .andExpect(jsonPath("$.arrivedAt").value(NOW.toString()))
                .andReturn();
        // The ID is read from the persisted replay row so assertions cover the owner write path.
        UUID visitId = jdbc.queryForObject("select visit_id from walk_in_requests where receptionist_account_id = ? "
                + "and idempotency_key = 't043-create'", UUID.class, receptionist);
        visitIds.add(visitId);
        assertEquals("WALK_IN", jdbc.queryForObject("select source from visits where id = ?", String.class, visitId));
        assertEquals("OPEN", jdbc.queryForObject("select status from visits where id = ?", String.class, visitId));
        assertEquals(patientProfile, jdbc.queryForObject("select patient_profile_id from visits where id = ?", UUID.class, visitId));
        assertEquals(NOW, jdbc.queryForObject("select arrived_at from visits where id = ?", Timestamp.class, visitId).toInstant());
        assertEquals(NOW, jdbc.queryForObject("select checked_in_at from visits where id = ?", Timestamp.class, visitId).toInstant());
        assertEquals(1, jdbc.queryForObject("select count(*) from visit_check_ins where visit_id = ? and reception_type = 'WALK_IN'", Integer.class, visitId));
        assertEquals(1, jdbc.queryForObject("select count(*) from visit_audits where visit_id = ? and actor_account_id = ? "
                + "and action = 'WALK_IN_CREATED' and occurred_at = ? and correlation_id = ?", Integer.class,
                visitId, receptionist, Timestamp.from(NOW), CORRELATION));
        assertEquals(1, jdbc.queryForObject("select count(*) from walk_in_requests where visit_id = ?", Integer.class, visitId));
        assertEquals(0, jdbc.queryForObject("select count(*) from appointments where visit_id = ?", Integer.class, visitId));
    }

    @Test
    void exactRequestReplayReturnsTheSameCreationResponseWithoutDuplicateRows() throws Exception {
        String payload = "{\"patientProfileId\":\"" + patientProfile + "\"}";
        var first = mvc.perform(createRequest(payload, "t043-replay", receptionist, AccountType.RECEPTIONIST))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var replay = mvc.perform(createRequest(payload, "t043-replay", receptionist, AccountType.RECEPTIONIST))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertEquals(first, replay);
        UUID visitId = jdbc.queryForObject("select visit_id from walk_in_requests where receptionist_account_id = ? "
                + "and idempotency_key = 't043-replay'", UUID.class, receptionist);
        visitIds.add(visitId);
        assertEquals(1, jdbc.queryForObject("select count(*) from visits where id = ?", Integer.class, visitId));
        assertEquals(1, jdbc.queryForObject("select count(*) from visit_check_ins where visit_id = ?", Integer.class, visitId));
        assertEquals(1, jdbc.queryForObject("select count(*) from visit_audits where visit_id = ?", Integer.class, visitId));
    }

    @Test
    void rejectsSameKeyWithDifferentRequestAndLeavesFirstVisitUnchanged() throws Exception {
        mvc.perform(create(patientProfile, null, "t043-conflict", receptionist, AccountType.RECEPTIONIST))
                .andExpect(status().isCreated());
        UUID visitId = jdbc.queryForObject("select visit_id from walk_in_requests where receptionist_account_id = ? "
                + "and idempotency_key = 't043-conflict'", UUID.class, receptionist);
        visitIds.add(visitId);
        mvc.perform(create(otherPatientProfile, null, "t043-conflict", receptionist, AccountType.RECEPTIONIST))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_CONFLICT"));
        assertEquals(1, jdbc.queryForObject("select count(*) from visits where created_by_account_id = ?", Integer.class, receptionist));
    }

    @Test
    void deniesNonReceptionistAndMapsMissingPatientToScopedNotFound() throws Exception {
        mvc.perform(create(patientProfile, null, "t043-denied", patientAccount, AccountType.PATIENT))
                .andExpect(status().isForbidden());
        mvc.perform(create(UUID.randomUUID(), null, "t043-missing", receptionist, AccountType.RECEPTIONIST))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        assertEquals(0, jdbc.queryForObject("select count(*) from visits where created_by_account_id = ?", Integer.class, receptionist));
        assertEquals(0, jdbc.queryForObject("select count(*) from walk_in_requests where receptionist_account_id = ?", Integer.class, receptionist));
    }

    @Test
    void priorAppointmentMustBelongToTheSuppliedPatient() throws Exception {
        UUID otherPatientAppointment = insertAppointment(otherPatientProfile, otherPatientAccount);
        mvc.perform(create(patientProfile, otherPatientAppointment, "t043-wrong-prior", receptionist,
                        AccountType.RECEPTIONIST))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        assertEquals("CONFIRMED", jdbc.queryForObject("select status from appointments where id = ?", String.class,
                otherPatientAppointment));
        assertEquals(0, jdbc.queryForObject("select count(*) from visits where created_by_account_id = ?", Integer.class, receptionist));
        assertEquals(0, jdbc.queryForObject("select count(*) from walk_in_requests where receptionist_account_id = ?", Integer.class, receptionist));
    }

    @Test
    void auditRowsRejectUpdateDeleteAndTruncate() throws Exception {
        mvc.perform(create(patientProfile, null, "t043-immutable", receptionist, AccountType.RECEPTIONIST))
                .andExpect(status().isCreated());
        UUID visitId = jdbc.queryForObject("select visit_id from walk_in_requests where receptionist_account_id = ? "
                + "and idempotency_key = 't043-immutable'", UUID.class, receptionist);
        visitIds.add(visitId);
        assertThrows(DataAccessException.class, () -> jdbc.update("update visit_audits set action = 'CHANGED' where visit_id = ?", visitId));
        assertThrows(DataAccessException.class, () -> jdbc.update("delete from visit_audits where visit_id = ?", visitId));
        assertThrows(DataAccessException.class, () -> jdbc.execute("truncate visit_audits"));
        assertEquals(1, jdbc.queryForObject("select count(*) from visit_audits where visit_id = ?", Integer.class, visitId));
    }

    @Test
    void auditFailureRollsBackVisitReceptionAndReplay() throws Exception {
        jdbc.execute("create function reject_t043_audit_insert() returns trigger language plpgsql as $$ begin "
                + "raise exception 'T043 injected audit failure' using errcode = 'P0001'; end; $$");
        jdbc.execute("create trigger tr_t043_fail_audit before insert on visit_audits for each row "
                + "execute function reject_t043_audit_insert()");
        mvc.perform(create(patientProfile, null, "t043-rollback", receptionist, AccountType.RECEPTIONIST))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("DEPENDENCY_UNAVAILABLE"));
        assertEquals(0, jdbc.queryForObject("select count(*) from visits where created_by_account_id = ?", Integer.class, receptionist));
        assertEquals(0, jdbc.queryForObject("select count(*) from visit_check_ins where receptionist_account_id = ?", Integer.class, receptionist));
        assertEquals(0, jdbc.queryForObject("select count(*) from visit_audits where actor_account_id = ?", Integer.class, receptionist));
        assertEquals(0, jdbc.queryForObject("select count(*) from walk_in_requests where receptionist_account_id = ?", Integer.class, receptionist));
    }

    private MockHttpServletRequestBuilder create(UUID profileId, UUID priorAppointmentId, String key, UUID actor,
            AccountType role) {
        String payload = "{\"patientProfileId\":\"" + profileId + "\""
                + (priorAppointmentId == null ? "" : ",\"priorAppointmentId\":\"" + priorAppointmentId + "\"") + "}";
        return createRequest(payload, key, actor, role);
    }

    private MockHttpServletRequestBuilder createRequest(String payload, String key, UUID actor, AccountType role) {
        return post("/api/v1/visits/walk-ins").with(actor(actor, role)).with(csrf())
                .header("Idempotency-Key", key).header("X-Correlation-ID", CORRELATION)
                .contentType("application/json").content(payload);
    }

    private UUID insertAppointment(UUID patientId, UUID patientOwnerAccountId) {
        UUID id = UUID.randomUUID();
        appointmentIds.add(id);
        Instant start = NOW.plusSeconds(3600);
        jdbc.update("insert into appointments (id, patient_profile_id, service_id, doctor_profile_id, status, "
                        + "booking_source, purpose, start_time, end_time, duration_minutes, buffer_minutes, created_by_account_id) "
                        + "values (?, ?, ?, ?, 'CONFIRMED', 'PATIENT_ONLINE', 'T043 prior appointment', ?, ?, 30, 5, ?)",
                id, patientId, serviceId, doctorProfile, Timestamp.from(start), Timestamp.from(start.plusSeconds(1800)),
                patientOwnerAccountId);
        jdbc.update("insert into appointment_audits (appointment_id, actor_account_id, actor_type, action, old_status, "
                        + "new_status, old_doctor_profile_id, new_doctor_profile_id, new_start_time, new_end_time, occurred_at, correlation_id) "
                        + "values (?, ?, 'PATIENT', 'CREATED', null, 'CONFIRMED', null, ?, ?, ?, ?, ?)",
                id, patientOwnerAccountId, doctorProfile, Timestamp.from(start), Timestamp.from(start.plusSeconds(1800)),
                Timestamp.from(NOW), UUID.randomUUID().toString());
        return id;
    }

    private UUID account(AccountType type, String suffix) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into accounts (id, normalized_email, account_type, status, created_at, updated_at) "
                        + "values (?, ?, ?, 'ACTIVE', ?, ?)", id,
                "t043-" + suffix + "-" + id + "@example.test", type.name(), Timestamp.from(NOW), Timestamp.from(NOW));
        return id;
    }

    private UUID patient(UUID accountId, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into patient_profiles (id, account_id, full_name, phone_canonical, phone_owner_type, "
                        + "date_of_birth, gender) values (?, ?, ?, ?, 'PATIENT', '1990-01-01', 'OTHER')",
                id, accountId, name, "+848" + UUID.randomUUID().toString().replace("-", "").substring(0, 9));
        return id;
    }

    private RequestPostProcessor actor(UUID id, AccountType type) {
        var principal = new AuthenticatedAccount(id,
                fu.fa26.sep490.g58.otcms_be.common.security.AccountRole.valueOf(type.name()), UUID.randomUUID());
        return authentication(new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + type.name()))));
    }

    private void restoreTrigger(String trigger, String mode) {
        switch (mode) {
            case "D" -> jdbc.execute("alter table visit_audits disable trigger " + trigger);
            case "R" -> jdbc.execute("alter table visit_audits enable replica trigger " + trigger);
            case "A" -> jdbc.execute("alter table visit_audits enable always trigger " + trigger);
            default -> jdbc.execute("alter table visit_audits enable trigger " + trigger);
        }
    }

    private void restoreAppointmentAuditTrigger(String mode) {
        switch (mode) {
            case "D" -> jdbc.execute("alter table appointment_audits disable trigger tr_appointment_audits_immutable");
            case "R" -> jdbc.execute("alter table appointment_audits enable replica trigger tr_appointment_audits_immutable");
            case "A" -> jdbc.execute("alter table appointment_audits enable always trigger tr_appointment_audits_immutable");
            default -> jdbc.execute("alter table appointment_audits enable trigger tr_appointment_audits_immutable");
        }
    }

    private void purgeStaleT043Fixtures() {
        String visitAuditMode = jdbc.queryForObject("select tgenabled::text from pg_trigger "
                + "where tgrelid = 'visit_audits'::regclass and tgname = 'tr_visit_audits_immutable'", String.class);
        String appointmentAuditMode = jdbc.queryForObject("select tgenabled::text from pg_trigger "
                + "where tgrelid = 'appointment_audits'::regclass and tgname = 'tr_appointment_audits_immutable'",
                String.class);
        jdbc.execute("alter table visit_audits disable trigger tr_visit_audits_immutable");
        jdbc.execute("alter table appointment_audits disable trigger tr_appointment_audits_immutable");
        try {
            String oldAccounts = "select id from accounts where normalized_email like 't043-%@example.test'";
            String oldVisits = "select id from visits where created_by_account_id in (" + oldAccounts + ")";
            String oldAppointments = "select id from appointments where service_id in "
                    + "(select id from clinic_services where name = 'T043 Service') or created_by_account_id in ("
                    + oldAccounts + ")";
            jdbc.update("delete from walk_in_requests where visit_id in (" + oldVisits
                    + ") or receptionist_account_id in (" + oldAccounts + ")");
            jdbc.update("delete from visit_check_ins where visit_id in (" + oldVisits + ")");
            jdbc.update("delete from visit_audits where visit_id in (" + oldVisits + ")");
            jdbc.update("delete from visits where id in (" + oldVisits + ")");
            jdbc.update("delete from appointment_audits where appointment_id in (" + oldAppointments + ")");
            jdbc.update("delete from appointments where id in (" + oldAppointments + ")");
            jdbc.update("delete from doctor_services where doctor_profile_id in "
                    + "(select id from doctor_profiles where account_id in (" + oldAccounts + "))");
            jdbc.update("delete from clinic_services where name = 'T043 Service'");
            jdbc.update("delete from doctor_profiles where account_id in (" + oldAccounts + ")");
            jdbc.update("delete from patient_profiles where account_id in (" + oldAccounts + ")");
            jdbc.update("delete from accounts where id in (" + oldAccounts + ")");
        } finally {
            restoreTrigger("tr_visit_audits_immutable", visitAuditMode);
            restoreAppointmentAuditTrigger(appointmentAuditMode);
        }
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean @Primary MutableTestClock t043Clock() { return new MutableTestClock(NOW); }
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
