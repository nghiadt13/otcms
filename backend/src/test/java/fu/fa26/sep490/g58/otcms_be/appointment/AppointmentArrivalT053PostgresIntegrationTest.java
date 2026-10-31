package fu.fa26.sep490.g58.otcms_be.appointment;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.sql.Timestamp;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
import javax.sql.DataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@LocalPostgresTest
@TestPropertySource(properties = {
        "app.arrival-qr.clinic-id=t053-test-clinic",
        "app.arrival-qr.hmac-secret=local-test-only-secret-with-at-least-32-bytes",
        "app.arrival-qr.ttl-seconds=60",
        "app.arrival-qr.refresh-seconds=30"
})
@Import(AppointmentArrivalT053PostgresIntegrationTest.FixedClockConfiguration.class)
class AppointmentArrivalT053PostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-26T10:00:00Z");
    private static final String CORRELATION = "t053-arrival-test";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired MutableTestClock testClock;
    private UUID patientAccount;
    private UUID otherPatientAccount;
    private UUID receptionistAccount;
    private UUID doctorAccount;
    private UUID patientProfile;
    private UUID otherPatientProfile;
    private UUID doctorProfile;
    private UUID service;
    private final List<UUID> appointments = new ArrayList<>();

    @BeforeEach
    void setUp() {
        testClock.set(NOW);
        patientAccount = account(AccountType.PATIENT, "patient");
        otherPatientAccount = account(AccountType.PATIENT, "other-patient");
        receptionistAccount = account(AccountType.RECEPTIONIST, "receptionist");
        doctorAccount = account(AccountType.DOCTOR, "doctor");
        patientProfile = patient(patientAccount, "Patient");
        otherPatientProfile = patient(otherPatientAccount, "Other Patient");
        service = UUID.randomUUID();
        jdbc.update("insert into clinic_services (id, name, duration_minutes, buffer_minutes, online_bookable, active) "
                + "values (?, 'T053 Arrival', 30, 5, true, true)", service);
        doctorProfile = doctor(doctorAccount);
        jdbc.update("insert into doctor_services (doctor_profile_id, service_id, active) values (?, ?, true)", doctorProfile, service);
    }

    @AfterEach
    void cleanUp() {
        String auditTrigger = triggerMode("appointment_audits", "tr_appointment_audits_immutable");
        String checkInTrigger = triggerMode("appointment_checkin_requests", "tr_appointment_command_replays_immutable");
        jdbc.execute("alter table appointment_audits disable trigger tr_appointment_audits_immutable");
        jdbc.execute("alter table appointment_checkin_requests disable trigger tr_appointment_command_replays_immutable");
        try {
            for (UUID id : appointments) {
                jdbc.update("delete from appointment_checkin_requests where appointment_id = ?", id);
                jdbc.update("delete from appointment_audits where appointment_id = ?", id);
                jdbc.update("update appointments set status = 'CANCELLED', visit_id = null where id = ?", id);
                jdbc.update("delete from appointment_arrivals where appointment_id = ?", id);
                List<UUID> visits = jdbc.query("select id from visits where originating_appointment_id = ?",
                        (rs, row) -> rs.getObject("id", UUID.class), id);
                for (UUID visit : visits) jdbc.update("delete from visit_check_ins where visit_id = ?", visit);
                jdbc.update("delete from visits where originating_appointment_id = ?", id);
                jdbc.update("delete from appointments where id = ?", id);
            }
            jdbc.update("delete from doctor_services where doctor_profile_id = ?", doctorProfile);
            jdbc.update("delete from clinic_services where id = ?", service);
            jdbc.update("delete from doctor_profiles where id = ?", doctorProfile);
            jdbc.update("delete from patient_profiles where id in (?, ?)", patientProfile, otherPatientProfile);
            jdbc.update("delete from accounts where id in (?, ?, ?, ?)", patientAccount, otherPatientAccount,
                    receptionistAccount, doctorAccount);
        } finally {
            restoreTrigger("appointment_audits", "tr_appointment_audits_immutable", auditTrigger);
            restoreTrigger("appointment_checkin_requests", "tr_appointment_command_replays_immutable", checkInTrigger);
        }
    }

    @Test
    void issuesReceptionistOnlyNoStoreProofAndRejectsTamperWrongClinicFutureAndExpiry() throws Exception {
        var proof = mvc.perform(get("/api/v1/arrival-proofs/current").with(actor(receptionistAccount, AccountType.RECEPTIONIST)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.refreshAfterSeconds").value(30))
                .andExpect(jsonPath("$.expiresAt").value(NOW.plusSeconds(60).toString()))
                .andReturn().getResponse().getContentAsString();
        String token = json(proof, "proof");
        assertNotEquals("", token);
        mvc.perform(get("/api/v1/arrival-proofs/current").with(actor(patientAccount, AccountType.PATIENT)))
                .andExpect(status().isForbidden());

        UUID target = appointment(NOW.plusSeconds(600), patientProfile);
        String[] tamperedParts = token.split("\\.");
        tamperedParts[3] = Long.toString(Long.parseLong(tamperedParts[3]) + 1);
        mvc.perform(recordQr(target, patientAccount, String.join(".", tamperedParts)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("ARRIVAL_PROOF_INVALID"));
        assertNoArrival(target);
        String wrongClinic = token.replace("dDA1My10ZXN0LWNsaW5pYw", "b3RoZXItY2xpbmlj");
        mvc.perform(recordQr(target, patientAccount, wrongClinic))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("ARRIVAL_PROOF_INVALID"));
        assertNoArrival(target);

        testClock.set(NOW.plusSeconds(60));
        mvc.perform(recordQr(target, patientAccount, token))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("ARRIVAL_PROOF_INVALID"));
        assertNoArrival(target);
        testClock.set(NOW.minusSeconds(1));
        mvc.perform(recordQr(target, patientAccount, token))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("ARRIVAL_PROOF_INVALID"));
        assertNoArrival(target);
    }

    @Test
    void patientOwnershipIsEnforcedAndValidRepeatReturnsOriginalWithoutQueueWrites() throws Exception {
        UUID target = appointment(NOW.plusSeconds(600), patientProfile);
        UUID otherOwned = appointment(NOW.plusSeconds(7200), patientProfile);
        String token = issueProof();
        mvc.perform(recordQr(target, otherPatientAccount, token)).andExpect(status().isForbidden());
        assertNoArrival(target);

        var first = mvc.perform(recordQr(target, patientAccount, token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("PATIENT_QR"))
                .andExpect(jsonPath("$.state").value("PENDING"))
                .andExpect(jsonPath("$.arrivedAt").value(NOW.toString())).andReturn();
        testClock.set(NOW.plusSeconds(10));
        mvc.perform(recordQr(target, patientAccount, issueProof())).andExpect(status().isOk())
                .andExpect(jsonPath("$.arrivedAt").value(NOW.toString()));
        assertEquals(1, arrivalCount(target));
        assertEquals(patientAccount, jdbc.queryForObject("select actor_account_id from appointment_arrivals where appointment_id = ?",
                UUID.class, target));
        assertEquals("PATIENT_QR", jdbc.queryForObject("select source from appointment_arrivals where appointment_id = ?",
                String.class, target));
        assertEquals(0, jdbc.queryForObject("select count(*) from visits where originating_appointment_id in (?, ?)",
                Integer.class, target, otherOwned));
        assertEquals(0, jdbc.queryForObject("select count(*) from visit_check_ins where visit_id in "
                + "(select id from visits where originating_appointment_id in (?, ?))", Integer.class, target, otherOwned));
        assertNotEquals("", first.getResponse().getContentAsString());
    }

    @Test
    void manualReceptionistFallbackIsAuditedIdempotentAndRejectsPatient() throws Exception {
        UUID target = appointment(NOW.plusSeconds(3600), patientProfile);
        String body = "{\"reason\":\"Helped patient scan QR\"}";
        mvc.perform(post("/api/v1/appointments/{id}/arrivals/manual", target)
                        .with(actor(patientAccount, AccountType.PATIENT)).with(testCsrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        assertNoArrival(target);

        mvc.perform(manual(target, receptionistAccount, "  Helped patient scan QR  "))
                .andExpect(status().isOk()).andExpect(jsonPath("$.source").value("RECEPTIONIST"))
                .andExpect(jsonPath("$.arrivedAt").value(NOW.toString()));
        testClock.set(NOW.plusSeconds(20));
        mvc.perform(manual(target, receptionistAccount, "different duplicate reason"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.arrivedAt").value(NOW.toString()));
        assertEquals(1, arrivalCount(target));
        assertEquals(receptionistAccount, jdbc.queryForObject("select actor_account_id from appointment_arrivals where appointment_id = ?",
                UUID.class, target));
        assertEquals("Helped patient scan QR", jdbc.queryForObject("select reason from appointment_arrivals where appointment_id = ?",
                String.class, target));
    }

    @Test
    void worklistIsReceptionistOnlyDateStateFilteredAndPaginated() throws Exception {
        UUID timely = appointment(NOW.minusSeconds(120), patientProfile);
        UUID late = appointment(NOW.minusSeconds(3600), otherPatientProfile);
        mvc.perform(manual(late, receptionistAccount, "Patient arrived after grace")).andExpect(status().isOk());
        testClock.set(NOW.plusSeconds(10));
        mvc.perform(recordQr(timely, patientAccount, issueProof())).andExpect(status().isOk());

        mvc.perform(get("/api/v1/appointment-arrivals").param("date", "2026-10-26").param("state", "PENDING")
                        .param("page", "0").param("size", "1").with(actor(receptionistAccount, AccountType.RECEPTIONIST)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].appointmentId").value(timely.toString()))
                .andExpect(jsonPath("$.items[0].state").value("PENDING"));
        mvc.perform(get("/api/v1/appointment-arrivals").param("date", "2026-10-26").param("state", "LATE")
                        .with(actor(receptionistAccount, AccountType.RECEPTIONIST)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].appointmentId").value(late.toString()))
                .andExpect(jsonPath("$.items[0].state").value("LATE"));
        mvc.perform(get("/api/v1/appointment-arrivals").with(actor(patientAccount, AccountType.PATIENT)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/appointment-arrivals").param("size", "101")
                        .with(actor(receptionistAccount, AccountType.RECEPTIONIST)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void timelyArrivalClaimCommittedBeforeNoShowBlocksNoShowAndLateClaimCannotReopenNoShow() throws Exception {
        UUID timely = appointment(NOW.minusSeconds(600), patientProfile);
        mvc.perform(recordQr(timely, patientAccount, issueProof())).andExpect(status().isOk());
        UUID timelyManual = appointment(NOW.plusSeconds(1800), patientProfile);
        mvc.perform(manual(timelyManual, receptionistAccount, "Patient arrived during check-in window"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.source").value("RECEPTIONIST"))
                .andExpect(jsonPath("$.arrivedAt").value(NOW.toString()));
        testClock.set(NOW.plusSeconds(7200));
        mvc.perform(markNoShow(timely)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("NO_SHOW_NOT_ALLOWED"));
        mvc.perform(markNoShow(timelyManual)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("NO_SHOW_NOT_ALLOWED"));
        assertEquals("CONFIRMED", jdbc.queryForObject("select status from appointments where id = ?", String.class, timely));
        assertEquals("CONFIRMED", jdbc.queryForObject("select status from appointments where id = ?",
                String.class, timelyManual));
        assertEquals(0, jdbc.queryForObject("select count(*) from visits where originating_appointment_id in (?, ?)",
                Integer.class, timely, timelyManual));
        assertEquals(0, jdbc.queryForObject("select count(*) from walk_in_reception_queue_entries q "
                + "join visits v on v.id = q.visit_id where v.originating_appointment_id in (?, ?)",
                Integer.class, timely, timelyManual));

        UUID noShow = appointment(NOW.minusSeconds(3600), patientProfile);
        mvc.perform(markNoShow(noShow)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NO_SHOW"));
        mvc.perform(recordQr(noShow, patientAccount, issueProof())).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("APPOINTMENT_NOT_CHANGEABLE"));
        assertNoArrival(noShow);
    }

    @Test
    void timelyQrArrivalHoldingSharedGuardSerializesAheadOfNoShowAfterGrace() throws Exception {
        UUID target = appointment(NOW.minusSeconds(600), patientProfile);
        String proof = issueProof();
        var pool = Executors.newFixedThreadPool(2);
        try (Connection blocker = dataSource.getConnection()) {
            blocker.setAutoCommit(false);
            try (PreparedStatement lock = blocker.prepareStatement("select id from appointments where id = ? for update")) {
                lock.setObject(1, target);
                try (ResultSet ignored = lock.executeQuery()) { }
            }
            try {
                var arrival = pool.submit(() -> mvc.perform(recordQr(target, patientAccount, proof))
                        .andReturn().getResponse().getStatus());
                awaitDatabaseWait("%from appointments where id = %for update%", 1);
                var noShow = pool.submit(() -> mvc.perform(markNoShow(target))
                        .andReturn().getResponse().getStatus());
                awaitDatabaseWait("%transaction_guard%for update%", 1);
                testClock.set(NOW.plusSeconds(7200));
                blocker.commit();

                assertEquals(200, arrival.get(20, TimeUnit.SECONDS));
                assertEquals(422, noShow.get(20, TimeUnit.SECONDS));
                assertEquals("CONFIRMED", jdbc.queryForObject("select status from appointments where id = ?", String.class, target));
                assertEquals(1, arrivalCount(target));
                assertEquals(NOW, jdbc.queryForObject("select arrived_at from appointment_arrivals where appointment_id = ?",
                        Timestamp.class, target).toInstant());
            } finally {
                blocker.rollback();
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void receptionistCanRecordLateArrivalButItDoesNotBlockNoShow() throws Exception {
        UUID target = appointment(NOW.minusSeconds(3600), patientProfile);
        testClock.set(NOW.plusSeconds(7200));
        mvc.perform(manual(target, receptionistAccount, "Patient reached reception late"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("LATE"));
        mvc.perform(markNoShow(target)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NO_SHOW"));
        assertEquals(1, arrivalCount(target));
    }

    private void awaitDatabaseWait(String queryPattern, int minimumWaiters) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbc.queryForObject("select count(*) from pg_stat_activity "
                    + "where wait_event_type = 'Lock' and query ilike ?", Integer.class, queryPattern);
            if (waiting != null && waiting >= minimumWaiters) return;
            Thread.sleep(25);
        }
        throw new AssertionError("Timed out waiting for PostgreSQL lock wait: " + queryPattern);
    }

    private String issueProof() throws Exception {
        String content = mvc.perform(get("/api/v1/arrival-proofs/current").with(actor(receptionistAccount, AccountType.RECEPTIONIST)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json(content, "proof");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder recordQr(UUID id, UUID actor, String proof) {
        return post("/api/v1/appointments/{id}/arrivals", id).with(actor(actor, AccountType.PATIENT)).with(testCsrf())
                .header("X-Correlation-ID", CORRELATION).contentType("application/json")
                .content("{\"proof\":\"" + proof + "\"}");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder manual(UUID id, UUID actor, String reason) {
        return post("/api/v1/appointments/{id}/arrivals/manual", id)
                .with(actor(actor, AccountType.RECEPTIONIST)).with(testCsrf()).header("X-Correlation-ID", CORRELATION)
                .contentType("application/json").content("{\"reason\":\"" + reason + "\"}");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder markNoShow(UUID id) {
        return patch("/api/v1/appointments/{id}/status", id).with(actor(receptionistAccount, AccountType.RECEPTIONIST))
                .with(testCsrf()).header("X-Correlation-ID", CORRELATION).contentType("application/json")
                .content("{\"targetStatus\":\"NO_SHOW\"}");
    }

    private UUID appointment(Instant start, UUID owner) {
        UUID id = UUID.randomUUID();
        appointments.add(id);
        UUID ownerAccount = owner.equals(patientProfile) ? patientAccount : otherPatientAccount;
        jdbc.update("insert into appointments (id, patient_profile_id, service_id, doctor_profile_id, status, booking_source, "
                        + "purpose, start_time, end_time, duration_minutes, buffer_minutes, created_by_account_id) "
                        + "values (?, ?, ?, ?, 'CONFIRMED', 'PATIENT_ONLINE', 'T053 arrival', ?, ?, 30, 5, ?)",
                id, owner, service, doctorProfile, Timestamp.from(start), Timestamp.from(start.plusSeconds(1800)), ownerAccount);
        jdbc.update("insert into appointment_audits (appointment_id, actor_account_id, actor_type, action, old_status, "
                        + "new_status, old_doctor_profile_id, new_doctor_profile_id, new_start_time, new_end_time, occurred_at, correlation_id) "
                        + "values (?, ?, 'PATIENT', 'CREATED', null, 'CONFIRMED', null, ?, ?, ?, ?, ?)",
                id, ownerAccount, doctorProfile, Timestamp.from(start), Timestamp.from(start.plusSeconds(1800)),
                Timestamp.from(NOW), UUID.randomUUID().toString());
        return id;
    }

    private UUID account(AccountType type, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into accounts (id, normalized_email, account_type, status, created_at, updated_at) "
                        + "values (?, ?, ?, 'ACTIVE', ?, ?)", id, "t053-" + name + "-" + id + "@example.test", type.name(),
                Timestamp.from(NOW), Timestamp.from(NOW));
        return id;
    }

    private UUID patient(UUID accountId, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into patient_profiles (id, account_id, full_name, phone_canonical, phone_owner_type, date_of_birth, gender) "
                        + "values (?, ?, ?, ?, 'PATIENT', '1990-01-01', 'OTHER')", id, accountId, "T053 " + name,
                "+848" + UUID.randomUUID().toString().replace("-", "").substring(0, 9));
        return id;
    }

    private UUID doctor(UUID accountId) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into doctor_profiles (id, account_id, full_name, photo_url, work_contact, specialties, qualifications, "
                        + "biography, consultation_information, publicly_visible, accepting_new_bookings, in_active_directory) "
                        + "values (?, ?, 'T053 Doctor', '', 'private', '', '', '', '', true, true, true)", id, accountId);
        return id;
    }

    private RequestPostProcessor actor(UUID id, AccountType type) {
        var principal = new AuthenticatedAccount(id, AccountRole.valueOf(type.name()), UUID.randomUUID());
        return authentication(new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + type.name()))));
    }

    private RequestPostProcessor testCsrf() {
        return request -> {
            request.setCookies(new jakarta.servlet.http.Cookie("XSRF-TOKEN", "t053-csrf"));
            request.addHeader("X-CSRF-TOKEN", "t053-csrf");
            return request;
        };
    }

    private int arrivalCount(UUID id) {
        return jdbc.queryForObject("select count(*) from appointment_arrivals where appointment_id = ?", Integer.class, id);
    }

    private void assertNoArrival(UUID id) { assertEquals(0, arrivalCount(id)); }

    private String triggerMode(String table, String trigger) {
        return jdbc.queryForObject("select tgenabled::text from pg_trigger where tgrelid = ?::regclass and tgname = ?",
                String.class, table, trigger);
    }

    private void restoreTrigger(String table, String trigger, String mode) {
        switch (mode) {
            case "D" -> jdbc.execute("alter table " + table + " disable trigger " + trigger);
            case "R" -> jdbc.execute("alter table " + table + " enable replica trigger " + trigger);
            case "A" -> jdbc.execute("alter table " + table + " enable always trigger " + trigger);
            default -> jdbc.execute("alter table " + table + " enable trigger " + trigger);
        }
    }

    private static String json(String body, String property) throws Exception {
        return new tools.jackson.databind.ObjectMapper().readTree(body).get(property).asString();
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean @Primary MutableTestClock t053Clock() { return new MutableTestClock(); }
    }

    static final class MutableTestClock extends Clock {
        private final AtomicReference<Instant> instant = new AtomicReference<>(NOW);
        void set(Instant value) { instant.set(value); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant.get(); }
    }
}
