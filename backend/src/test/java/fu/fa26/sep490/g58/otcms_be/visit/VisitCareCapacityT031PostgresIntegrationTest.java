package fu.fa26.sep490.g58.otcms_be.visit;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "otcms.visit.care-step-duration-minutes.CONSULTATION=30",
        "otcms.visit.care-step-room-capability.CONSULTATION=EXAM_ROOM",
        "otcms.visit.room-cleanup-buffer-minutes.EXAM_ROOM=5"
})
@AutoConfigureMockMvc
@LocalPostgresTest
@Import(VisitCareCapacityT031PostgresIntegrationTest.FixedClockConfiguration.class)
class VisitCareCapacityT031PostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-19T10:00:00Z");
    private static final String CORRELATION = "51a6414d-466c-47ec-8a50-868ed3529b71";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired fu.fa26.sep490.g58.otcms_be.visit.repository.VisitRoomReservationRepository roomReservations;

    private UUID doctorAccount;
    private UUID patientAccount;
    private UUID receptionist;
    private UUID adminAccount;
    private UUID otherDoctorAccount;
    private UUID doctor;
    private UUID otherDoctor;
    private UUID patient;
    private UUID visit;
    private UUID room;
    private UUID bookingService;
    private final List<UUID> visits = new ArrayList<>();

    @BeforeEach
    void setUp() {
        doctorAccount = account(AccountType.DOCTOR, "doctor");
        patientAccount = account(AccountType.PATIENT, "patient");
        receptionist = account(AccountType.RECEPTIONIST, "receptionist");
        adminAccount = account(AccountType.ADMIN, "admin");
        doctor = UUID.randomUUID();
        patient = UUID.randomUUID();
        visit = UUID.randomUUID();
        jdbc.update("insert into patient_profiles (id, account_id, full_name, phone_canonical, phone_owner_type, date_of_birth, gender) values (?, ?, 'T031 Patient', ?, 'PATIENT', '1990-01-01', 'OTHER')",
                patient, patientAccount, "+849" + UUID.randomUUID().toString().replace("-", "").substring(0, 9));
        jdbc.update("insert into staff_profiles (is_doctor, id, account_id, display_name, photo_url, work_contact, specialties, qualifications, biography, consultation_information, publicly_visible, accepting_new_bookings, in_active_directory) values (true, ?, ?, 'T031 Doctor', '', 'private', '', '', '', '', true, true, true)",
                doctor, doctorAccount);
        bookingService = UUID.randomUUID();
        // Price 0: a free service, so the walk-in fixture below is cancellable like a free walk-in.
        jdbc.update("insert into clinic_services (id, name, duration_minutes, buffer_minutes, online_bookable, active, price) "
                        + "values (?, 'T033 Cross Feature Race', 30, 0, true, true, 0)", bookingService);
        jdbc.update("insert into doctor_services (doctor_profile_id, service_id, active) values (?, ?, true)",
                doctor, bookingService);
        jdbc.update("insert into work_shifts (id, staff_profile_id, starts_at, ends_at) values (?, ?, ?, ?)",
                UUID.randomUUID(), doctor, Timestamp.from(NOW.minusSeconds(300)), Timestamp.from(NOW.plusSeconds(7200)));
        jdbc.update("insert into visits (id, patient_profile_id, source, arrived_at, checked_in_at, created_by_account_id) values (?, ?, 'WALK_IN', ?, ?, ?)",
                visit, patient, Timestamp.from(NOW.minusSeconds(60)), Timestamp.from(NOW.minusSeconds(60)), receptionist);
        new TransactionTemplate(transactionManager).execute(status -> {
            jdbc.queryForObject("select set_config('otcms.reception_actor_id', ?, true)", String.class,
                    receptionist.toString());
            jdbc.queryForObject("select set_config('otcms.reception_correlation_id', ?, true)", String.class,
                    CORRELATION);
            jdbc.update("insert into queue_entries (stage, status, visit_id, arrived_at, entered_at) values ('RECEPTION', 'WAITING', ?, ?, ?)", visit, Timestamp.from(NOW.minusSeconds(60)), Timestamp.from(NOW));
            return null;
        });
        // A free walk-in as the admission flow leaves it: WAITING with number 1 and the free service.
        jdbc.update("insert into queue_entries (stage, visit_id, doctor_profile_id, service_id, clinic_date, queue_number, status, entered_at, queued_at) values ('EXAM', ?, ?, ?, date '2026-10-19', 1, 'WAITING', ?, ?)", visit, doctor, bookingService,
                Timestamp.from(NOW.minusSeconds(60)), Timestamp.from(NOW.minusSeconds(60)));
        visits.add(visit);
        room = UUID.randomUUID();
        jdbc.update("insert into rooms (id, room_code, display_label, active) values (?, 'T031-EXAM-ROOM', 'T031 Exam Room', true)", room);
        jdbc.update("update rooms set capabilities = array['EXAM_ROOM'] where id = ?", room);
        otherDoctorAccount = account(AccountType.DOCTOR, "other-doctor");
        otherDoctor = UUID.randomUUID();
        jdbc.update("insert into staff_profiles (is_doctor, id, account_id, display_name, photo_url, work_contact, specialties, qualifications, biography, consultation_information, publicly_visible, accepting_new_bookings, in_active_directory) values (true, ?, ?, 'T031 Other Doctor', '', 'private', '', '', '', '', true, true, true)", otherDoctor, otherDoctorAccount);
    }

    @AfterEach
    void cleanUp() {


        String auditLogsImmutable = triggerMode("audit_logs", "tr_audit_logs_immutable");

        jdbc.execute("alter table audit_logs disable trigger tr_audit_logs_immutable");
        try {
            for (UUID visitId : visits) {
                jdbc.update("delete from audit_logs where source = 'walk_in_reception_queue_history' and entity_id = ?", visitId);
                jdbc.update("delete from queue_entries where stage = 'RECEPTION' and visit_id = ?", visitId);
                jdbc.update("delete from audit_logs where source = 'visit_assignment_audits' and record_label = ?::text", visitId);
                jdbc.update("delete from audit_logs where source = 'room_reservation_audits' and metadata->>'visitId' = ?::text", visitId);
                jdbc.update("delete from room_reservations where visit_id = ?", visitId);
                jdbc.update("delete from visit_assignments where visit_id = ?", visitId);

                jdbc.update("delete from audit_logs where source = 'visit_audits' and record_label = ?::text", visitId);
                jdbc.update("delete from queue_entries where stage = 'EXAM' and visit_id = ?", visitId);
                jdbc.update("delete from visits where id = ?", visitId);
            }
            if (bookingService != null) {
                jdbc.update("delete from audit_logs where source = 'appointment_audits' and entity_id in "
                        + "(select id from appointments where service_id = ?)", bookingService);
                jdbc.update("delete from appointments where service_id = ?", bookingService);
                jdbc.update("delete from doctor_services where service_id = ?", bookingService);
                jdbc.update("delete from clinic_services where id = ?", bookingService);
            }
            if (doctor != null) jdbc.update("delete from work_shifts where staff_profile_id = ?", doctor);
            if (doctor != null) jdbc.update("delete from queue_entries where stage = 'EXAM' and doctor_profile_id = ?", doctor);
            if (room != null) jdbc.update("delete from audit_logs where source = 'room_management_audits' and entity_id = ?", room);
            if (doctor != null) jdbc.update("delete from staff_profiles where id = ?", doctor);
            if (otherDoctor != null) jdbc.update("delete from staff_profiles where id = ?", otherDoctor);
            if (room != null) {

                jdbc.update("delete from rooms where id = ?", room);
            }
            if (patient != null) jdbc.update("delete from patient_profiles where id = ?", patient);
            for (UUID id : new UUID[]{doctorAccount, otherDoctorAccount, patientAccount, receptionist, adminAccount}) {
                if (id != null) jdbc.update("delete from accounts where id = ?", id);
            }
        } finally {
            restoreTrigger("audit_logs", "tr_audit_logs_immutable", auditLogsImmutable);
        }
    }

    @Test
    void repeatedCapacityConflictsDeduplicateAndMatchMigrationKey() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            roomReservations.appendConflict(visit, null, doctorAccount, "CONSULTATION", CORRELATION, NOW);
            roomReservations.appendConflict(visit, null, doctorAccount, "CONSULTATION", CORRELATION, NOW);
            // Legacy audit columns for actor and correlation were NOT NULL.
            assertThrows(NullPointerException.class, () -> roomReservations.appendConflict(visit, null, doctorAccount, "CONSULTATION", null, NOW));
            assertThrows(NullPointerException.class, () -> roomReservations.appendConflict(visit, null, null, "CONSULTATION", CORRELATION, NOW));
        });
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_logs where source='room_reservation_audits' and action='CAPACITY_CONFLICT' and entity_id=? and correlation_id=? and dedup_key = 'room-capacity:' || encode(sha256(convert_to(?::text || ':' || ?::text || ':' || ?, 'UTF8')), 'hex')", Integer.class,
                visit, CORRELATION, visit, doctorAccount, CORRELATION));
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_logs where source='room_reservation_audits' and action='CAPACITY_CONFLICT' and entity_id=?", Integer.class, visit));
    }

    @Test
    void appointmentBookingRaceNeverRefusesTheExaminationStart() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var booking = executor.submit(() -> runAtBarrier(ready, start, () -> mvc.perform(post("/api/v1/appointments")
                    .with(actor(receptionist, AccountRole.RECEPTIONIST)).with(csrf()).contentType("application/json")
                    .content("{\"patientProfileId\":\"" + patient + "\",\"serviceId\":\"" + bookingService
                            + "\",\"doctorId\":\"" + doctor + "\",\"startTime\":\""
                            + NOW.plusSeconds(900) + "\",\"purpose\":\"T033 capacity race\"}"))
                    .andReturn().getResponse().getStatus()));
            var care = executor.submit(() -> runAtBarrier(ready, start, () -> mvc.perform(post(
                    "/api/v1/visits/{visitId}/care-assignments", visit)
                    .with(actor(doctorAccount, AccountRole.DOCTOR)).with(csrf()).contentType("application/json")
                    .content("{\"stepCode\":\"CONSULTATION\"}"))
                    .andReturn().getResponse().getStatus()));

            org.junit.jupiter.api.Assertions.assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            int bookingStatus = booking.get(20, TimeUnit.SECONDS);
            int careStatus = care.get(20, TimeUnit.SECONDS);

            // Starting care checks no booking (BE-4): the start succeeds whichever request commits first.
            assertEquals(201, careStatus, "Examination start must not be refused by a booking; booking=" + bookingStatus);
            org.junit.jupiter.api.Assertions.assertTrue(bookingStatus == 201 || bookingStatus == 409,
                    "Booking may succeed or be refused, not fail: booking=" + bookingStatus);
            assertEquals(1, jdbc.queryForObject("select count(*) from visit_assignments where visit_id = ? and status = 'IN_PROGRESS'",
                    Integer.class, visit));
            assertEquals(0, jdbc.queryForObject("select count(*) from room_reservations where visit_id = ?",
                    Integer.class, visit), "Neither booking nor examination start reserves a room");
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private int runAtBarrier(CountDownLatch ready, CountDownLatch start, ThrowingStatus request) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("T033 race barrier timed out");
        return request.run();
    }

    @FunctionalInterface
    private interface ThrowingStatus { int run() throws Exception; }

    @Test
    void careStartReservesNoRoomButWritesImmutableAuditAndCompletesWithActualEnd() throws Exception {
        String body = "{\"stepCode\":\"CONSULTATION\"}";
        var response = mvc.perform(post("/api/v1/visits/{visitId}/care-assignments", visit)
                        .with(actor(doctorAccount, AccountRole.DOCTOR)).with(csrf())
                        .header("X-Correlation-ID", CORRELATION).contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(header().string("X-Correlation-ID", CORRELATION))
                .andExpect(jsonPath("$.stepCode").value("CONSULTATION"))
                .andExpect(jsonPath("$.expectedEnd").value(NOW.plusSeconds(1800).toString()))
                .andReturn();
        String content = response.getResponse().getContentAsString();
        UUID assignment = UUID.fromString(content.substring(content.indexOf("\"id\":\"") + 6,
                content.indexOf('"', content.indexOf("\"id\":\"") + 6)));
        assertEquals(1, jdbc.queryForObject("select count(*) from visit_assignments where id = ? and expected_duration_minutes = 30 and step_code = 'CONSULTATION' and status = 'IN_PROGRESS'", Integer.class, assignment));
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_logs where source = 'visit_assignment_audits' and entity_id = ? and action = 'STARTED' and actor_account_id = ? and correlation_id = ?", Integer.class, assignment, doctorAccount, CORRELATION));
        // A start reserves no room; rooms are only reserved by an extension (unchanged).
        assertEquals(0, jdbc.queryForObject("select count(*) from room_reservations where assignment_id = ?", Integer.class, assignment));
        assertEquals(0, jdbc.queryForObject("select count(*) from audit_logs where source = 'room_reservation_audits' and metadata->>'assignmentId' = ?::text", Integer.class, assignment));
        assertEquals(0, jdbc.queryForObject("select count(*) from queue_entries where stage = 'RECEPTION' and visit_id = ? and status = 'WAITING'", Integer.class, visit));
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_logs h where h.source = 'walk_in_reception_queue_history' and h.entity_id = ? and h.metadata->>'toStatus' = 'LEFT_RECEPTION' and h.action = 'CARE_STARTED' and h.actor_account_id = ? and h.correlation_id = ?", Integer.class,
                visit, doctorAccount, CORRELATION));

        // No extension here: an extension after a reservation-free start reserves from the old expected end, and
        // completing earlier than that breaks ck_visit_room_reservation_actual_end (pre-existing extend logic, see report).
        mvc.perform(post("/api/v1/visits/{visitId}/care-assignments/{assignmentId}/completion", visit, assignment)
                        .with(actor(doctorAccount, AccountRole.DOCTOR)).with(csrf()).header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.actualEndedAt").value(NOW.toString()));
        assertEquals(1, jdbc.queryForObject("select count(*) from visit_assignments where id = ? and actual_ended_at = ? and ends_at = ?", Integer.class, assignment, Timestamp.from(NOW), Timestamp.from(NOW.plusSeconds(1800))));
        assertEquals(0, jdbc.queryForObject("select count(*) from room_reservations where assignment_id = ?", Integer.class, assignment));
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("delete from audit_logs where source = 'visit_assignment_audits' and entity_id = ?", assignment));
    }

    @Test
    void receptionistCancellationClosesWalkInAndMovesQueueEntryToHistory() throws Exception {
        mvc.perform(post("/api/v1/visits/{visitId}/cancellation", visit)
                        .with(actor(receptionist, AccountRole.RECEPTIONIST)).with(csrf())
                        .header("X-Correlation-ID", CORRELATION))
                .andExpect(status().isNoContent());
        assertEquals("CLOSED", jdbc.queryForObject("select status from visits where id = ?", String.class, visit));
        assertEquals(0, jdbc.queryForObject("select count(*) from queue_entries where stage = 'RECEPTION' and visit_id = ? and status = 'WAITING'", Integer.class, visit));
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_logs h where h.source = 'walk_in_reception_queue_history' and h.entity_id = ? and h.action = 'CANCELLED' and h.actor_account_id = ?",
                Integer.class, visit, receptionist));
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_logs where source = 'visit_audits' and record_label = ?::text and action = 'WALK_IN_CANCELLED' and actor_account_id = ?", Integer.class, visit, receptionist));
    }

    @Test
    void receptionistClosureMovesWaitingWalkInQueueEntryToHistory() throws Exception {
        jdbc.update("insert into clinical_examinations(id,visit_id,status,created_at,updated_at,version) values (?,?,'COMPLETED',?,?,0)",
                UUID.randomUUID(), visit, Timestamp.from(NOW), Timestamp.from(NOW));
        mvc.perform(post("/api/v1/visits/{visitId}/closure", visit)
                        .with(actor(receptionist, AccountRole.RECEPTIONIST)).with(csrf())
                        .header("X-Correlation-ID", CORRELATION))
                .andExpect(status().isNoContent());
        assertEquals("CLOSED", jdbc.queryForObject("select status from visits where id = ?", String.class, visit));
        assertEquals(0, jdbc.queryForObject("select count(*) from queue_entries where stage = 'RECEPTION' and visit_id = ? and status = 'WAITING'", Integer.class, visit));
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_logs h where h.source = 'walk_in_reception_queue_history' and h.entity_id = ? and h.action = 'VISIT_CLOSED' and h.actor_account_id = ?",
                Integer.class, visit, receptionist));
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_logs where source = 'visit_audits' and record_label = ?::text and action = 'WALK_IN_CLOSED' and actor_account_id = ?", Integer.class, visit, receptionist));
    }

    @Test
    void cancellationIsRejectedAfterCareStarts() throws Exception {
        mvc.perform(post("/api/v1/visits/{visitId}/care-assignments", visit)
                        .with(actor(doctorAccount, AccountRole.DOCTOR)).with(csrf())
                        .header("X-Correlation-ID", CORRELATION).contentType("application/json")
                        .content("{\"stepCode\":\"CONSULTATION\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/visits/{visitId}/cancellation", visit)
                        .with(actor(receptionist, AccountRole.RECEPTIONIST)).with(csrf())
                        .header("X-Correlation-ID", CORRELATION))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("VISIT_CANCELLATION_NOT_ALLOWED"));
        assertEquals("OPEN", jdbc.queryForObject("select status from visits where id = ?", String.class, visit));
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_logs h where h.source = 'walk_in_reception_queue_history' and h.entity_id = ? and h.action = 'CARE_STARTED'", Integer.class, visit));
        // UC28 closure is Receptionist-only; Doctor denial precedes business-state checks.
        mvc.perform(post("/api/v1/visits/{visitId}/closure", visit)
                        .with(actor(doctorAccount, AccountRole.DOCTOR)).with(csrf())
                        .header("X-Correlation-ID", CORRELATION))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/visits/{visitId}/closure", visit)
                        .with(actor(receptionist, AccountRole.RECEPTIONIST)).with(csrf())
                        .header("X-Correlation-ID", CORRELATION))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("VISIT_CARE_NOT_CHANGEABLE"));
        assertEquals("OPEN", jdbc.queryForObject("select status from visits where id = ?", String.class, visit));
    }

    @Test
    void careStartQueueFailureRollsBackCareAndExamQueueWrites() throws Exception {
        installQueueExitFailureTrigger();
        try {
            mvc.perform(post("/api/v1/visits/{visitId}/care-assignments", visit)
                            .with(actor(doctorAccount, AccountRole.DOCTOR)).with(csrf())
                            .header("X-Correlation-ID", CORRELATION).contentType("application/json")
                            .content("{\"stepCode\":\"CONSULTATION\"}"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.error.code").value("DEPENDENCY_UNAVAILABLE"));
        } finally {
            dropQueueExitFailureTrigger();
        }
        assertEquals(0, jdbc.queryForObject("select count(*) from visit_assignments where visit_id = ?",
                Integer.class, visit));
        // The queue move to IN_EXAMINATION happens before the reception exit, so it rolls back with the care.
        assertEquals("WAITING", jdbc.queryForObject("select status from queue_entries where stage = 'EXAM' and visit_id = ?",
                String.class, visit));
        assertEquals(0, jdbc.queryForObject("select count(*) from audit_logs where source = 'visit_assignment_audits' and record_label = ?::text",
                Integer.class, visit));
        assertEquals(1, jdbc.queryForObject("select count(*) from queue_entries where stage = 'RECEPTION' and visit_id = ? and status = 'WAITING'", Integer.class, visit));
    }

    @Test
    void cancellationQueueFailureRollsBackVisitClosureAndAudit() throws Exception {
        installQueueExitFailureTrigger();
        try {
            mvc.perform(post("/api/v1/visits/{visitId}/cancellation", visit)
                            .with(actor(receptionist, AccountRole.RECEPTIONIST)).with(csrf())
                            .header("X-Correlation-ID", CORRELATION))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.error.code").value("DEPENDENCY_UNAVAILABLE"));
        } finally {
            dropQueueExitFailureTrigger();
        }
        assertEquals("OPEN", jdbc.queryForObject("select status from visits where id = ?", String.class, visit));
        assertEquals(0, jdbc.queryForObject("select count(*) from audit_logs where source = 'visit_audits' and record_label = ?::text and action = 'WALK_IN_CANCELLED'", Integer.class, visit));
        assertEquals(1, jdbc.queryForObject("select count(*) from queue_entries where stage = 'RECEPTION' and visit_id = ? and status = 'WAITING'", Integer.class, visit));
    }

    private void installQueueExitFailureTrigger() {
        jdbc.execute("create function reject_t048_queue_exit() returns trigger language plpgsql as $$ begin "
                + "raise exception 'T048 injected queue exit failure' using errcode = 'P0001'; end; $$");
        jdbc.execute("create trigger tr_t048_queue_exit_failure before update on queue_entries "
                + "for each row execute function reject_t048_queue_exit()");
    }

    private void dropQueueExitFailureTrigger() {
        jdbc.execute("drop trigger if exists tr_t048_queue_exit_failure on queue_entries");
        jdbc.execute("drop function if exists reject_t048_queue_exit()");
    }

    @Test
    void doctorExaminingAnotherVisitRefusesStartWithoutAssignmentOrAudit() throws Exception {
        UUID conflictingVisit = UUID.randomUUID();
        UUID conflictingAssignment = UUID.randomUUID();
        jdbc.update("insert into visits (id, patient_profile_id, source, arrived_at, checked_in_at, created_by_account_id) values (?, ?, 'WALK_IN', ?, ?, ?)",
                conflictingVisit, patient, Timestamp.from(NOW.minusSeconds(60)), Timestamp.from(NOW.minusSeconds(60)), receptionist);
        visits.add(conflictingVisit);
        jdbc.update("insert into visit_assignments (id, visit_id, doctor_profile_id, starts_at, ends_at, status, assigned_at, step_code, expected_duration_minutes) values (?, ?, ?, ?, ?, 'IN_PROGRESS', ?, 'CONSULTATION', 30)",
                conflictingAssignment, conflictingVisit, doctor, Timestamp.from(NOW), Timestamp.from(NOW.plusSeconds(900)), Timestamp.from(NOW));
        // The queue decides doctor conflicts: this doctor is already examining conflictingVisit (number 2).
        jdbc.update("insert into queue_entries (stage, visit_id, doctor_profile_id, clinic_date, queue_number, status, entered_at, queued_at, started_at) values ('EXAM', ?, ?, date '2026-10-19', 2, 'IN_EXAMINATION', ?, ?, ?)", conflictingVisit, doctor,
                Timestamp.from(NOW.minusSeconds(120)), Timestamp.from(NOW.minusSeconds(120)), Timestamp.from(NOW.minusSeconds(60)));
        mvc.perform(post("/api/v1/visits/{visitId}/care-assignments", visit)
                        .with(actor(doctorAccount, AccountRole.DOCTOR)).with(csrf())
                        .header("X-Correlation-ID", CORRELATION).contentType("application/json")
                        .content("{\"stepCode\":\"CONSULTATION\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("VISIT_EXAM_STATE_CONFLICT"));
        assertEquals(0, jdbc.queryForObject("select count(*) from visit_assignments where visit_id = ?", Integer.class, visit));
        assertEquals(0, jdbc.queryForObject("select count(*) from audit_logs where source = 'visit_assignment_audits' and record_label = ?::text", Integer.class, visit));
        assertEquals("WAITING", jdbc.queryForObject("select status from queue_entries where stage = 'EXAM' and visit_id = ?", String.class, visit));
    }

    @Test
    void fullRoomDoesNotRefuseAnExaminationAndReservesNoRoom() throws Exception {
        UUID occupiedVisit = UUID.randomUUID();
        UUID occupiedAssignment = UUID.randomUUID();
        UUID occupiedReservation = UUID.randomUUID();
        jdbc.update("insert into visits (id, patient_profile_id, source, arrived_at, checked_in_at, created_by_account_id) values (?, ?, 'WALK_IN', ?, ?, ?)",
                occupiedVisit, patient, Timestamp.from(NOW.minusSeconds(60)), Timestamp.from(NOW.minusSeconds(60)), receptionist);
        visits.add(occupiedVisit);
        jdbc.update("insert into visit_assignments (id, visit_id, doctor_profile_id, starts_at, ends_at, status, assigned_at, step_code, expected_duration_minutes) values (?, ?, ?, ?, ?, 'IN_PROGRESS', ?, 'CONSULTATION', 30)",
                occupiedAssignment, occupiedVisit, otherDoctor, Timestamp.from(NOW), Timestamp.from(NOW.plusSeconds(900)), Timestamp.from(NOW));
        jdbc.update("insert into room_reservations (id, visit_id, assignment_id, room_id, step_code, capability_code, starts_at, expected_ends_at, reserved_until, cleanup_buffer_minutes, status, assigned_at) values (?, ?, ?, ?, 'CONSULTATION', 'EXAM_ROOM', ?, ?, ?, 5, 'IN_PROGRESS', ?)",
                occupiedReservation, occupiedVisit, occupiedAssignment, room, Timestamp.from(NOW.minusSeconds(3600)),
                Timestamp.from(NOW.minusSeconds(1800)), Timestamp.from(NOW.minusSeconds(1500)), Timestamp.from(NOW.minusSeconds(3600)));

        mvc.perform(post("/api/v1/visits/{visitId}/care-assignments", visit)
                        .with(actor(doctorAccount, AccountRole.DOCTOR)).with(csrf())
                        .header("X-Correlation-ID", CORRELATION).contentType("application/json")
                        .content("{\"stepCode\":\"CONSULTATION\"}"))
                .andExpect(status().isCreated());

        // The occupied room has no effect: the start takes no room and writes no room reservation or capacity audit.
        assertEquals(1, jdbc.queryForObject("select count(*) from visit_assignments where visit_id = ? and status = 'IN_PROGRESS'", Integer.class, visit));
        assertEquals(0, jdbc.queryForObject("select count(*) from room_reservations where visit_id = ?", Integer.class, visit));
        assertEquals(0, jdbc.queryForObject("select count(*) from audit_logs where source = 'room_reservation_audits' and metadata->>'visitId' = ?::text and action = 'CAPACITY_CONFLICT'", Integer.class, visit));
    }

    @Test
    void concurrentStartsOfOneDoctorsTwoVisitsExamineExactlyOne() throws Exception {
        UUID otherVisit = UUID.randomUUID();
        jdbc.update("insert into visits (id, patient_profile_id, source, arrived_at, checked_in_at, created_by_account_id) values (?, ?, 'WALK_IN', ?, ?, ?)",
                otherVisit, patient, Timestamp.from(NOW.minusSeconds(60)), Timestamp.from(NOW.minusSeconds(60)), receptionist);
        // The second visit of the same doctor, waiting with number 2 and the free service.
        jdbc.update("insert into queue_entries (stage, visit_id, doctor_profile_id, service_id, clinic_date, queue_number, status, entered_at, queued_at) values ('EXAM', ?, ?, ?, date '2026-10-19', 2, 'WAITING', ?, ?)", otherVisit, doctor, bookingService,
                Timestamp.from(NOW.minusSeconds(30)), Timestamp.from(NOW.minusSeconds(30)));
        visits.add(otherVisit);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> concurrentCareStart(visit, doctorAccount, "t031-queue-race-a", ready, start));
            var second = pool.submit(() -> concurrentCareStart(otherVisit, doctorAccount, "t031-queue-race-b", ready, start));
            if (!ready.await(5, TimeUnit.SECONDS)) throw new AssertionError("T031 queue race setup timed out");
            start.countDown();
            int firstStatus = first.get(15, TimeUnit.SECONDS);
            int secondStatus = second.get(15, TimeUnit.SECONDS);
            assertEquals(List.of(201, 409), List.of(firstStatus, secondStatus).stream().sorted().toList());
            assertEquals(1, jdbc.queryForObject("select count(*) from queue_entries where stage = 'EXAM' and doctor_profile_id = ? and status = 'IN_EXAMINATION'", Integer.class, doctor));
            assertEquals(1, jdbc.queryForObject("select count(*) from visit_assignments where visit_id in (?, ?) and status = 'IN_PROGRESS'", Integer.class, visit, otherVisit));
        } finally {
            start.countDown();
        }
    }

    @Test
    void activeReservationBlocksCapabilityChangeAndDeactivationButAllowsLabelUpdate() throws Exception {
        mvc.perform(post("/api/v1/visits/{visitId}/care-assignments", visit)
                        .with(actor(doctorAccount, AccountRole.DOCTOR)).with(csrf())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()).contentType("application/json")
                        .content("{\"stepCode\":\"CONSULTATION\"}"))
                .andExpect(status().isCreated());
        // A start reserves no room any more, so an active reservation is seeded directly for this guard check.
        UUID assignment = jdbc.queryForObject("select id from visit_assignments where visit_id = ? and status = 'IN_PROGRESS'", UUID.class, visit);
        jdbc.update("insert into room_reservations (id, visit_id, assignment_id, room_id, step_code, capability_code, starts_at, expected_ends_at, reserved_until, cleanup_buffer_minutes, status, assigned_at) values (?, ?, ?, ?, 'CONSULTATION', 'EXAM_ROOM', ?, ?, ?, 5, 'IN_PROGRESS', ?)",
                UUID.randomUUID(), visit, assignment, room, Timestamp.from(NOW), Timestamp.from(NOW.plusSeconds(1800)),
                Timestamp.from(NOW.plusSeconds(2100)), Timestamp.from(NOW));

        mvc.perform(put("/api/v1/admin/visit-rooms/{roomId}", room)
                        .with(actor(adminAccount, AccountRole.ADMIN)).with(csrf())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()).contentType("application/json")
                        .content("{\"displayLabel\":\"Updated label\",\"capabilities\":[\"EXAM_ROOM\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.displayLabel").value("Updated label"));
        mvc.perform(put("/api/v1/admin/visit-rooms/{roomId}", room)
                        .with(actor(adminAccount, AccountRole.ADMIN)).with(csrf())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()).contentType("application/json")
                        .content("{\"displayLabel\":\"Updated label\",\"capabilities\":[\"PROCEDURE_ROOM\"]}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ROOM_HAS_ACTIVE_RESERVATIONS"));
        mvc.perform(delete("/api/v1/admin/visit-rooms/{roomId}", room)
                        .with(actor(adminAccount, AccountRole.ADMIN)).with(csrf())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ROOM_HAS_ACTIVE_RESERVATIONS"));
        assertEquals(1, jdbc.queryForObject("select count(*) from room_reservations where room_id = ? and status = 'IN_PROGRESS'", Integer.class, room));
    }

    @Test
    void careStartAndRoomDeactivationDoNotInterfereBecauseStartsReserveNoRoom() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var careStart = pool.submit(() -> concurrentCareStart(visit, doctorAccount, "t031-admin-start-race", ready, start));
            var deactivation = pool.submit(() -> concurrentRoomDeactivation(room, adminAccount, ready, start));
            if (!ready.await(5, TimeUnit.SECONDS)) throw new AssertionError("T031 room admin race setup timed out");
            start.countDown();
            int careStatus = careStart.get(15, TimeUnit.SECONDS);
            int adminStatus = deactivation.get(15, TimeUnit.SECONDS);
            // A start checks no room, so the deactivation is never blocked by it, whichever request commits first.
            assertEquals(201, careStatus);
            assertEquals(204, adminStatus);
            assertEquals(0, jdbc.queryForObject("select count(*) from rooms where id = ? and active", Integer.class, room));
            assertEquals(0, jdbc.queryForObject("select count(*) from room_reservations where visit_id = ?", Integer.class, visit));
        } finally {
            start.countDown();
        }
    }

    private int concurrentRoomDeactivation(UUID roomId, UUID accountId,
            CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("T031 room admin race start timed out");
        return mvc.perform(delete("/api/v1/admin/visit-rooms/{roomId}", roomId)
                        .with(actor(accountId, AccountRole.ADMIN)).with(csrf())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andReturn().getResponse().getStatus();
    }

    private int concurrentCareStart(UUID visitId, UUID accountId, String correlationId,
            CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("T031 room race start timed out");
        return mvc.perform(post("/api/v1/visits/{visitId}/care-assignments", visitId)
                        .with(actor(accountId, AccountRole.DOCTOR)).with(csrf())
                        .header("X-Correlation-ID", correlationId).contentType("application/json")
                        .content("{\"stepCode\":\"CONSULTATION\"}"))
                .andReturn().getResponse().getStatus();
    }

    private UUID account(AccountType type, String label) {
        UUID id = UUID.randomUUID();
        Instant created = Instant.parse("2026-10-01T00:00:00Z");
        jdbc.update("insert into accounts (id, normalized_email, account_type, status, created_at, updated_at) values (?, ?, ?, 'ACTIVE', ?, ?)",
                id, "t031-" + label + "-" + id + "@example.test", type.name(), Timestamp.from(created), Timestamp.from(created));
        return id;
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor actor(UUID account, AccountRole role) {
        return authentication(new UsernamePasswordAuthenticationToken(new AuthenticatedAccount(account, role, null), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    private void restoreTrigger(String table, String trigger, String mode) {
        String action = switch (mode) {
            case "D" -> "DISABLE";
            case "R" -> "ENABLE REPLICA";
            case "A" -> "ENABLE ALWAYS";
            default -> "ENABLE";
        };
        jdbc.execute("alter table " + table + " " + action + " trigger " + trigger);
    }

    private String triggerMode(String table, String trigger) {
        return jdbc.queryForObject("select tgenabled::text from pg_trigger where tgrelid = ?::regclass and tgname = ?",
                String.class, table, trigger);
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean @Primary Clock t031FixedClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }
}
