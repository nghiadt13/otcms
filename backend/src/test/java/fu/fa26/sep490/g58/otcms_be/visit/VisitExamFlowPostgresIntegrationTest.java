package fu.fa26.sep490.g58.otcms_be.visit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalImagingRequestService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.impl.ClinicalImagingRequestFingerprint;
import fu.fa26.sep490.g58.otcms_be.clinical.service.impl.ClinicalImagingRequestTransactionService;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitClinicalCareContextService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * The exam flow of plans/261111-0208-visit-flow-r3 against the local PostgreSQL test database, through the real HTTP
 * endpoints and triggers. Each scenario runs in one transaction that is rolled back, so nothing is committed.
 * Clock is fixed at 09:00 on 2026-11-11 (Asia/Ho_Chi_Minh): morning slots are already over, afternoon slots are ahead.
 */
@SpringBootTest
@AutoConfigureMockMvc
@LocalPostgresTest
@Transactional
@Import(VisitExamFlowPostgresIntegrationTest.FixedClockConfiguration.class)
class VisitExamFlowPostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-11T02:00:00Z");
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String CORRELATION = "3f0b7c52-1d8e-4a6b-9c1f-5e2d7a8b9c01";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    @Autowired VisitClinicalCareContextService clinicalCare;
    @Autowired ClinicalImagingRequestTransactionService imagingTransactions;

    private UUID receptionist;
    private UUID doctorAccount;
    private UUID doctor;
    private UUID freeExamination;
    private UUID imagingService;

    @BeforeEach
    void setUp() {
        receptionist = account(AccountType.RECEPTIONIST);
        doctorAccount = account(AccountType.DOCTOR);
        doctor = doctorProfile(doctorAccount, "EF Doctor");
        UUID room = room();
        jdbc.update("insert into doctor_shifts (id, doctor_profile_id, starts_at, ends_at, room_id) values (?, ?, ?, ?, ?)",
                UUID.randomUUID(), doctor, ts(NOW.minus(Duration.ofHours(9))), ts(NOW.plus(Duration.ofHours(8))), room);
        freeExamination = service("EF free consultation", "EXAMINATION", BigDecimal.ZERO, null);
        imagingService = service("EF knee MRI", "IMAGING", new BigDecimal("100"), "MRI");
        jdbc.update("insert into doctor_services (doctor_profile_id, service_id, active) values (?, ?, true)",
                doctor, freeExamination);
        jdbc.update("insert into doctor_services (doctor_profile_id, service_id, active) values (?, ?, true)",
                doctor, imagingService);
    }

    /** Scenario 1: two checked-in patients get numbers 1 and 2; the second cannot start while the first is examined. */
    @Test
    void secondPatientIsNotBlockedByTheRoomOrWindowAndCannotStartWhileFirstIsExamined() throws Exception {
        UUID patientA = patientProfile("EF Patient A");
        UUID patientB = patientProfile("EF Patient B");
        UUID appointmentA = appointment(patientA, at(10, 0), at(10, 30));
        UUID appointmentB = appointment(patientB, at(11, 0), at(11, 30));
        checkIn(appointmentA, patientA);
        checkIn(appointmentB, patientB);
        UUID visitA = visitOf(appointmentA);
        UUID visitB = visitOf(appointmentB);
        assertEquals(1, queueNumber(visitA));
        assertEquals(2, queueNumber(visitB));

        startAppointment(appointmentA);
        patchStatus(appointmentB, "IN_PROGRESS")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("VISIT_EXAM_STATE_CONFLICT"));

        assertEquals("IN_EXAMINATION", queueStatus(visitA));
        assertEquals("WAITING", queueStatus(visitB));
        assertEquals("CHECKED_IN", appointmentStatus(appointmentB));
    }

    /**
     * Scenario 2: ordering imaging moves A to AWAITING_CONCLUSION. A keeps its IN_PROGRESS care and clinical rights,
     * so B can start.
     */
    @Test
    void imagingOrderHandsTheQueueToTheNextPatientWhileFirstVisitKeepsCareAndClinicalWork() throws Exception {
        UUID patientA = patientProfile("EF Patient A");
        UUID patientB = patientProfile("EF Patient B");
        UUID appointmentA = appointment(patientA, at(10, 0), at(10, 30));
        UUID appointmentB = appointment(patientB, at(11, 0), at(11, 30));
        checkIn(appointmentA, patientA);
        checkIn(appointmentB, patientB);
        startAppointment(appointmentA);
        UUID visitA = visitOf(appointmentA);

        // The HTTP order path is ClinicalImagingRequestServiceImpl.submit, which runs NOT_SUPPORTED and opens its own
        // transaction; that one waits on the coordination row this test transaction already holds. So this calls the
        // transactional submit it delegates to, with the same validation and fingerprint.
        var command = new ClinicalImagingRequestService.ImagingRequestCommand(visitA, doctor, "ef-imaging-" + visitA,
                List.of(new ClinicalImagingRequestService.ImagingOrderRequest(imagingService, "Suspected injury",
                        "RIGHT_KNEE", "Weight-bearing")), true);
        var validated = ClinicalImagingRequestFingerprint.validated(command);
        imagingTransactions.submit(validated, ClinicalImagingRequestFingerprint.fingerprint(validated));

        assertEquals("AWAITING_CONCLUSION", queueStatus(visitA));
        assertEquals(1, count("select count(*) from visit_assignments where visit_id = ? and status = 'IN_PROGRESS'", visitA));
        assertTrue(clinicalCare.findClinicalCareContext(visitA).orElseThrow().clinicalWorkAllowed());

        startAppointment(appointmentB);
        assertEquals("IN_EXAMINATION", queueStatus(visitOf(appointmentB)));
    }

    /**
     * Scenario 3: the doctor cannot end the visit while evidence is missing (VISIT_COMPLETION_NOT_ALLOWED). When the
     * examination is complete, the visit, care, queue entry and appointment all end in one step.
     */
    @Test
    void doctorEndsScheduledVisitOnlyWithEvidenceAndThenClosesVisitQueueCareAndAppointment() throws Exception {
        UUID patient = patientProfile("EF Patient C");
        UUID appointment = appointment(patient, at(10, 0), at(10, 30));
        checkIn(appointment, patient);
        startAppointment(appointment);
        UUID visit = visitOf(appointment);
        examination(visit, "DRAFT");

        patchStatus(appointment, "COMPLETED")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("VISIT_COMPLETION_NOT_ALLOWED"));
        assertEquals("OPEN", visitStatus(visit));
        assertEquals("IN_EXAMINATION", queueStatus(visit));
        assertEquals("IN_PROGRESS", careStatus(visit));

        examination(visit, "COMPLETED");
        patchStatus(appointment, "COMPLETED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        assertEquals("CLOSED", visitStatus(visit));
        assertEquals("COMPLETED", queueStatus(visit));
        assertEquals("COMPLETED", careStatus(visit));
        assertEquals("COMPLETED", appointmentStatus(appointment));
    }

    /** Scenario 4: a walk-in is started by its doctor and ended through POST /completion (no appointment involved). */
    @Test
    void walkInIsStartedAndEndedByResponsibleDoctorThroughCompletionEndpoint() throws Exception {
        UUID patient = patientProfile("EF Walk-in Patient");
        UUID visit = walkIn(patient);
        assertEquals("WAITING", queueStatus(visit));
        assertEquals(1, queueNumber(visit));

        mvc.perform(post("/api/v1/visits/{visitId}/care-assignments", visit).with(csrf()).with(doctorActor())
                        .header("X-Correlation-ID", CORRELATION).contentType("application/json")
                        .content("{\"stepCode\":\"CONSULTATION\"}"))
                .andExpect(status().isCreated());
        assertEquals("IN_EXAMINATION", queueStatus(visit));

        completeWalkIn(visit)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("VISIT_COMPLETION_NOT_ALLOWED"));
        examination(visit, "COMPLETED");
        completeWalkIn(visit)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visitId").value(visit.toString()))
                .andExpect(jsonPath("$.examStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.closedAt").exists());

        assertEquals("CLOSED", visitStatus(visit));
        assertEquals("COMPLETED", queueStatus(visit));
        assertEquals("COMPLETED", careStatus(visit));
    }

    /** Scenario 5: the appointment ended at 08:30; starting at 09:00 still works, and the care lasts 30 minutes. */
    @Test
    void scheduledStartAfterTheAppointmentEndStillSucceedsAndCareLastsThirtyMinutes() throws Exception {
        UUID patient = patientProfile("EF Late Patient");
        UUID appointment = appointment(patient, at(8, 0), at(8, 30));
        checkIn(appointment, patient);

        startAppointment(appointment);

        UUID visit = visitOf(appointment);
        assertEquals("IN_EXAMINATION", queueStatus(visit));
        assertEquals("IN_PROGRESS", careStatus(visit));
        assertEquals(NOW.plus(Duration.ofMinutes(30)), careEnd(visit));
    }

    private ResultActions patchStatus(UUID appointment, String target) throws Exception {
        return mvc.perform(patch("/api/v1/appointments/{id}/status", appointment).with(csrf()).with(doctorActor())
                .header("X-Correlation-ID", CORRELATION).contentType("application/json")
                .content("{\"targetStatus\":\"" + target + "\"}"));
    }

    private void startAppointment(UUID appointment) throws Exception {
        patchStatus(appointment, "IN_PROGRESS")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    private void checkIn(UUID appointment, UUID patientProfile) throws Exception {
        mvc.perform(post("/api/v1/appointments/{id}/check-in", appointment)
                        .with(actor(receptionist, AccountType.RECEPTIONIST)).with(csrf())
                        .header("Idempotency-Key", "ef-checkin-" + appointment).header("X-Correlation-ID", CORRELATION)
                        .contentType("application/json")
                        .content("{\"verifiedPatientProfileId\":\"" + patientProfile + "\"}"))
                .andExpect(status().isOk());
    }

    private UUID walkIn(UUID patientProfile) throws Exception {
        mvc.perform(post("/api/v1/visits/walk-ins").with(csrf()).with(actor(receptionist, AccountType.RECEPTIONIST))
                        .header("Idempotency-Key", "ef-walk-in-" + patientProfile).header("X-Correlation-ID", CORRELATION)
                        .contentType("application/json")
                        .content("{\"patientProfileId\":\"" + patientProfile + "\",\"doctorProfileId\":\"" + doctor
                                + "\",\"serviceId\":\"" + freeExamination + "\"}"))
                .andExpect(status().isCreated());
        return jdbc.queryForObject("select id from visits where patient_profile_id = ? and source = 'WALK_IN'",
                UUID.class, patientProfile);
    }

    private ResultActions completeWalkIn(UUID visit) throws Exception {
        return mvc.perform(post("/api/v1/visits/{visitId}/completion", visit).with(csrf()).with(doctorActor())
                .header("X-Correlation-ID", CORRELATION));
    }

    private RequestPostProcessor doctorActor() {
        return actor(doctorAccount, AccountType.DOCTOR);
    }

    private RequestPostProcessor actor(UUID accountId, AccountType type) {
        AccountRole role = AccountRole.valueOf(type.name());
        return authentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedAccount(accountId, role, UUID.randomUUID()), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + type.name()))));
    }

    private UUID account(AccountType type) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into accounts (id, normalized_email, account_type, status, created_at, updated_at) "
                + "values (?, ?, ?, 'ACTIVE', ?, ?)", id, "ef-" + id + "@example.test", type.name(), ts(NOW), ts(NOW));
        return id;
    }

    private UUID patientProfile(String name) {
        UUID account = account(AccountType.PATIENT);
        UUID id = UUID.randomUUID();
        jdbc.update("insert into patient_profiles (id, account_id, full_name, phone_canonical, phone_owner_type, "
                + "date_of_birth, gender) values (?, ?, ?, ?, 'PATIENT', '1990-01-01', 'OTHER')",
                id, account, name, "+848" + id.toString().replace("-", "").substring(0, 9));
        return id;
    }

    private UUID doctorProfile(UUID account, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into doctor_profiles (id, account_id, full_name, photo_url, work_contact, specialties, "
                + "qualifications, biography, consultation_information, publicly_visible, "
                + "accepting_new_bookings, in_active_directory) values (?, ?, ?, '', '', '', '', '', '', true, true, true)",
                id, account, name);
        return id;
    }

    private UUID room() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into rooms (id, room_code, display_label, active) values (?, ?, 'EF Room', true)",
                id, "EF-" + id.toString().substring(0, 8));
        return id;
    }

    private UUID service(String name, String type, BigDecimal price, String modality) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into clinic_services (id, name, service_type, modality, duration_minutes, buffer_minutes, "
                + "price, online_bookable, active) values (?, ?, ?, ?, 30, 5, ?, true, true)",
                id, name, type, modality, price);
        return id;
    }

    /** A CONFIRMED appointment with the same shape as the lifecycle tests create (patient-booked, free service). */
    private UUID appointment(UUID patientProfile, Instant start, Instant end) {
        UUID id = UUID.randomUUID();
        UUID patientAccount = jdbc.queryForObject("select account_id from patient_profiles where id = ?", UUID.class,
                patientProfile);
        jdbc.update("insert into appointments (id, patient_profile_id, service_id, doctor_profile_id, status, "
                + "booking_source, purpose, start_time, end_time, duration_minutes, buffer_minutes, created_by_account_id) "
                + "values (?, ?, ?, ?, 'CONFIRMED', 'PATIENT_ONLINE', 'EF purpose', ?, ?, 30, 5, ?)",
                id, patientProfile, freeExamination, doctor, ts(start), ts(end), patientAccount);
        jdbc.update("insert into appointment_audits (appointment_id, actor_account_id, actor_type, action, new_status, "
                + "new_doctor_profile_id, new_start_time, new_end_time, occurred_at, correlation_id) "
                + "values (?, ?, 'PATIENT', 'CREATED', 'CONFIRMED', ?, ?, ?, ?, ?)",
                id, patientAccount, doctor, ts(start), ts(end), ts(NOW), UUID.randomUUID().toString());
        return id;
    }

    /**
     * Creates or moves the visit's examination (one per visit), as the doctor's form does. JDBC bypasses the
     * persistence context, so it is flushed and cleared first; otherwise a DRAFT read earlier stays cached.
     */
    private void examination(UUID visit, String status) {
        entityManager.flush();
        entityManager.clear();
        jdbc.update("insert into clinical_examinations (id, visit_id, reason_for_visit, symptoms, examination_findings, "
                + "status, created_at, updated_at, version) values (?, ?, 'EF reason', 'EF symptoms', 'EF findings', ?, ?, ?, 0) "
                + "on conflict (visit_id) do update set status = excluded.status, updated_at = excluded.updated_at",
                UUID.randomUUID(), visit, status, ts(NOW), ts(NOW));
    }

    private UUID visitOf(UUID appointment) {
        return jdbc.queryForObject("select visit_id from appointments where id = ?", UUID.class, appointment);
    }

    private String queueStatus(UUID visit) {
        return jdbc.queryForObject("select exam_status from visit_exam_queue where visit_id = ?", String.class, visit);
    }

    private int queueNumber(UUID visit) {
        return jdbc.queryForObject("select queue_number from visit_exam_queue where visit_id = ?", Integer.class, visit);
    }

    private String visitStatus(UUID visit) {
        return jdbc.queryForObject("select status from visits where id = ?", String.class, visit);
    }

    private String careStatus(UUID visit) {
        return jdbc.queryForObject("select status from visit_assignments where visit_id = ? "
                + "order by assigned_at desc, id desc limit 1", String.class, visit);
    }

    private Instant careEnd(UUID visit) {
        return jdbc.queryForObject("select ends_at from visit_assignments where visit_id = ? and status = 'IN_PROGRESS'",
                Timestamp.class, visit).toInstant();
    }

    private String appointmentStatus(UUID appointment) {
        return jdbc.queryForObject("select status from appointments where id = ?", String.class, appointment);
    }

    private int count(String sql, UUID visit) {
        return jdbc.queryForObject(sql, Integer.class, visit);
    }

    private static Instant at(int hour, int minute) {
        return LocalDate.of(2026, 11, 11).atTime(hour, minute).atZone(CLINIC_ZONE).toInstant();
    }

    private static Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        MutableTestClock examFlowClock() {
            return new MutableTestClock(NOW);
        }
    }

    static final class MutableTestClock extends Clock {
        private final AtomicReference<Instant> instant;

        MutableTestClock(Instant initial) {
            this.instant = new AtomicReference<>(initial);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }
}
