package fu.fa26.sep490.g58.otcms_be.visit;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitExamQueueService;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exam queue against the local PostgreSQL test database. Every fixture and write runs in the test
 * transaction, so nothing is committed and no cleanup is needed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@LocalPostgresTest
@Transactional
class VisitExamQueuePostgresIntegrationTest {
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    @Autowired VisitExamQueueService queue;

    private final Instant now = Instant.now();
    private final LocalDate clinicDate = LocalDate.now(CLINIC_ZONE);
    private UUID receptionistAccountId;
    private UUID adminAccountId;
    private UUID doctorAAccountId;
    private UUID doctorBAccountId;
    private UUID patientAccountId;
    private UUID otherPatientAccountId;
    private UUID patientProfileId;
    private UUID otherPatientProfileId;
    private UUID doctorAProfileId;
    private UUID doctorBProfileId;
    private UUID roomId;
    private UUID visit1;
    private UUID visit2;
    private UUID visit3;

    @BeforeEach
    void setUp() {
        receptionistAccountId = account(AccountType.RECEPTIONIST);
        adminAccountId = account(AccountType.ADMIN);
        doctorAAccountId = account(AccountType.DOCTOR);
        doctorBAccountId = account(AccountType.DOCTOR);
        patientAccountId = account(AccountType.PATIENT);
        otherPatientAccountId = account(AccountType.PATIENT);
        patientProfileId = patientProfile(patientAccountId, "EQ Patient");
        otherPatientProfileId = patientProfile(otherPatientAccountId, "EQ Other Patient");
        doctorAProfileId = doctorProfile(doctorAAccountId, "EQ Doctor A");
        doctorBProfileId = doctorProfile(doctorBAccountId, "EQ Doctor B");
        roomId = room();
        // Doctor A is on shift around the test start; doctor B has no shift at all.
        shift(doctorAProfileId, roomId, now.minus(Duration.ofHours(2)), now.plus(Duration.ofHours(2)));
        visit1 = walkInVisit(patientProfileId);
        visit2 = walkInVisit(patientProfileId);
        visit3 = walkInVisit(otherPatientProfileId);
    }

    @Test
    void queueNumbersFollowCheckInOrderPerDoctorAndOneDoctorExaminesOneVisitAtATime() {
        assertEquals(1, queue.enqueueWaiting(visit1, doctorAProfileId, null, now).queueNumber());
        assertEquals(2, queue.enqueueWaiting(visit2, doctorAProfileId, null, now.plusSeconds(60)).queueNumber());
        assertEquals(1, queue.enqueueWaiting(visit3, doctorBProfileId, null, now).queueNumber());

        queue.startExamination(visit1, doctorAProfileId, now);
        assertConflict(() -> queue.startExamination(visit2, doctorAProfileId, now));

        queue.markAwaitingConclusion(visit1, now);
        queue.startExamination(visit2, doctorAProfileId, now);
        queue.complete(visit2, doctorAProfileId, now);
        entityManager.flush();
        assertEquals("COMPLETED", jdbc.queryForObject(
                "select exam_status from visit_exam_queue where visit_id = ?", String.class, visit2));
    }

    @Test
    void walkInFeeAdmissionGivesTheNumberOnlyOnce() {
        queue.createPendingPayment(visit1, doctorAProfileId, null, now);
        assertNull(queue.find(visit1).orElseThrow().queueNumber());

        assertEquals(1, queue.admitAfterPayment(visit1, now).queueNumber());
        assertEquals(1, queue.admitAfterPayment(visit1, now).queueNumber());
        assertEquals(2, queue.enqueueWaiting(visit2, doctorAProfileId, null, now).queueNumber());
    }

    @Test
    void readApisServeEachAllowedRoleAndDenyTheRest() throws Exception {
        queue.enqueueWaiting(visit1, doctorAProfileId, null, now);

        mvc.perform(get("/api/v1/exam-queue").param("date", clinicDate.toString())
                        .with(actor(receptionistAccountId, AccountType.RECEPTIONIST)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].visitId").value(hasItem(visit1.toString())));
        mvc.perform(get("/api/v1/exam-queue").param("date", clinicDate.toString())
                        .with(actor(adminAccountId, AccountType.ADMIN)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/exam-queue").param("date", clinicDate.toString())
                        .with(actor(patientAccountId, AccountType.PATIENT)))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/doctor/exam-queue").with(actor(doctorAAccountId, AccountType.DOCTOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].visitId").value(visit1.toString()))
                .andExpect(jsonPath("$.items[0].queueNumber").value(1))
                .andExpect(jsonPath("$.items[0].patientName").value("EQ Patient"))
                .andExpect(jsonPath("$.items[0].dateOfBirth").value("1990-05-01"))
                .andExpect(jsonPath("$.items[0].gender").value("FEMALE"));

        mvc.perform(get("/api/v1/reception/on-shift-doctors")
                        .with(actor(receptionistAccountId, AccountType.RECEPTIONIST)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.doctorProfileId == '%s')].waitingCount"
                        .formatted(doctorAProfileId)).value(hasItem(1)));

        mvc.perform(get("/api/v1/visits/{visitId}/exam-queue-entry", visit1)
                        .with(actor(receptionistAccountId, AccountType.RECEPTIONIST)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueNumber").value(1));
        mvc.perform(get("/api/v1/visits/{visitId}/exam-queue-entry", visit1)
                        .with(actor(doctorAAccountId, AccountType.DOCTOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dateOfBirth").value("1990-05-01"))
                .andExpect(jsonPath("$.gender").value("FEMALE"));
        mvc.perform(get("/api/v1/visits/{visitId}/exam-queue-entry", visit1)
                        .with(actor(doctorBAccountId, AccountType.DOCTOR)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/visits/{visitId}/exam-queue-entry", visit1)
                        .with(actor(patientAccountId, AccountType.PATIENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientName").value("EQ Patient"))
                .andExpect(jsonPath("$.dateOfBirth").value("1990-05-01"))
                .andExpect(jsonPath("$.gender").value("FEMALE"));
        mvc.perform(get("/api/v1/visits/{visitId}/exam-queue-entry", visit1)
                        .with(actor(otherPatientAccountId, AccountType.PATIENT)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/visits/{visitId}/exam-queue-entry", UUID.randomUUID())
                        .with(actor(receptionistAccountId, AccountType.RECEPTIONIST)))
                .andExpect(status().isNotFound());
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
                + "values (?, ?, ?, 'ACTIVE', now(), now())", id, "exam-queue-" + id + "@example.test", type.name());
        return id;
    }

    private UUID patientProfile(UUID accountId, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into patient_profiles (id, account_id, full_name, phone_canonical, phone_owner_type, "
                + "date_of_birth, gender) values (?, ?, ?, ?, 'PATIENT', '1990-05-01', 'FEMALE')",
                id, accountId, name, "+849" + id.toString().replace("-", "").substring(0, 9));
        return id;
    }

    private UUID doctorProfile(UUID accountId, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into staff_profiles (is_doctor, id, account_id, display_name, photo_url, work_contact, specialties, "
                + "qualifications, biography, consultation_information, publicly_visible, "
                + "accepting_new_bookings, in_active_directory) values (true, ?, ?, ?, '', '', '', '', '', '', true, true, true)",
                id, accountId, name);
        return id;
    }

    private UUID room() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into rooms (id, room_code, display_label, active) values (?, ?, 'EQ Room 1', true)",
                id, "EQ-" + id.toString().substring(0, 8));
        return id;
    }

    private void shift(UUID doctorProfileId, UUID roomId, Instant startsAt, Instant endsAt) {
        jdbc.update("insert into work_shifts (id, staff_profile_id, starts_at, ends_at, room_id) "
                + "values (?, ?, ?, ?, ?)", UUID.randomUUID(), doctorProfileId, Timestamp.from(startsAt),
                Timestamp.from(endsAt), roomId);
    }

    private UUID walkInVisit(UUID patientProfileId) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into visits (id, patient_profile_id, source, arrived_at, checked_in_at, "
                + "created_by_account_id) values (?, ?, 'WALK_IN', ?, ?, ?)",
                id, patientProfileId, Timestamp.from(now), Timestamp.from(now), receptionistAccountId);
        return id;
    }

    private static void assertConflict(Executable action) {
        VisitOperationException failure = assertThrows(VisitOperationException.class, action);
        assertEquals("VISIT_EXAM_STATE_CONFLICT", failure.code());
    }
}
