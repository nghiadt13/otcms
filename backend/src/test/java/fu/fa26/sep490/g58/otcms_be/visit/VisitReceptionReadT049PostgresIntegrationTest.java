package fu.fa26.sep490.g58.otcms_be.visit;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import fu.fa26.sep490.g58.otcms_be.visit.service.CreateWalkInReceptionQueueEntryCommand;
import fu.fa26.sep490.g58.otcms_be.visit.service.ExitWalkInReceptionQueueCommand;
import fu.fa26.sep490.g58.otcms_be.visit.service.ExitWalkInReceptionQueueService;
import fu.fa26.sep490.g58.otcms_be.visit.service.WalkInReceptionQueueService;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@LocalPostgresTest
class VisitReceptionReadT049PostgresIntegrationTest {
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired WalkInReceptionQueueService queue;
    @Autowired ExitWalkInReceptionQueueService queueExit;

    private UUID receptionistId;
    private UUID patientAAccountId;
    private UUID patientBAccountId;
    private UUID patientAProfileId;
    private UUID patientBProfileId;
    private UUID accountlessProfileId;
    private UUID firstVisitId;
    private UUID secondVisitId;
    private UUID accountlessVisitId;
    private UUID doctorAccountId;
    private UUID doctorProfileId;
    private UUID roomId;
    private UUID assignmentId;
    private LocalDate clinicDate;

    @BeforeEach
    void setUp() {
        removeAbandonedT049DoctorFixtures();
        receptionistId = account(AccountType.RECEPTIONIST);
        patientAAccountId = account(AccountType.PATIENT);
        patientBAccountId = account(AccountType.PATIENT);
        doctorAccountId = account(AccountType.DOCTOR);
        patientAProfileId = profile(patientAAccountId, "T049 Patient A");
        patientBProfileId = profile(patientBAccountId, "T049 Patient B");
        accountlessProfileId = profile(null, "T049 Walk-in");
        clinicDate = LocalDate.now(CLINIC_ZONE);

        firstVisitId = visit(patientAProfileId, atClinicTime(8, 0));
        secondVisitId = visit(patientBProfileId, atClinicTime(8, 5));
        accountlessVisitId = visit(accountlessProfileId, atClinicTime(8, 10));
        addQueueEntry(firstVisitId);
        addQueueEntry(secondVisitId);
        addQueueEntry(accountlessVisitId);
        assignRoom(firstVisitId);
    }

    @AfterEach
    void cleanUp() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        if (receptionistId == null) return;
        jdbc.execute("alter table walk_in_reception_queue_history disable trigger tr_walk_in_reception_queue_history_immutable");
        jdbc.execute("alter table walk_in_reception_queue_history disable trigger tr_walk_in_reception_queue_history_no_truncate");
        if (roomId != null) jdbc.update("delete from room_reservations where room_id = ?", roomId);
        if (assignmentId != null) jdbc.update("delete from visit_assignments where id = ?", assignmentId);
        if (roomId != null) jdbc.update("delete from rooms where id = ?", roomId);
        if (doctorProfileId != null) jdbc.update("delete from doctor_profiles where id = ?", doctorProfileId);
        List<UUID> visitIds = List.of(firstVisitId, secondVisitId, accountlessVisitId);
        for (UUID visitId : visitIds) {
            jdbc.update("delete from walk_in_reception_queue_history where queue_entry_id in "
                    + "(select id from walk_in_reception_queue_entries where visit_id = ?)", visitId);
            jdbc.update("delete from walk_in_reception_queue_entries where visit_id = ?", visitId);
            jdbc.update("delete from visits where id = ?", visitId);
        }
        jdbc.execute("alter table walk_in_reception_queue_history enable trigger tr_walk_in_reception_queue_history_immutable");
        jdbc.execute("alter table walk_in_reception_queue_history enable trigger tr_walk_in_reception_queue_history_no_truncate");
        if (patientAProfileId != null) jdbc.update("delete from patient_profiles where id = ?", patientAProfileId);
        if (patientBProfileId != null) jdbc.update("delete from patient_profiles where id = ?", patientBProfileId);
        if (accountlessProfileId != null) jdbc.update("delete from patient_profiles where id = ?", accountlessProfileId);
        for (UUID accountId : List.of(receptionistId, patientAAccountId, patientBAccountId, doctorAccountId)) {
            if (accountId != null) jdbc.update("delete from accounts where id = ?", accountId);
        }
    }

    @Test
    void receptionistGetsClinicDayFifoPagesAndOnlyCurrentRoomAssignment() throws Exception {
        mvc.perform(get("/api/v1/reception-queue").with(actor(receptionistId, AccountType.RECEPTIONIST))
                        .param("date", clinicDate.toString()).param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].visitId").value(firstVisitId.toString()))
                .andExpect(jsonPath("$.items[0].position").value(1))
                .andExpect(jsonPath("$.items[0].status").value("WAITING"))
                .andExpect(jsonPath("$.items[0].currentStage").value("EXAMINATION"))
                .andExpect(jsonPath("$.items[0].currentRoom").value("T049-R1"))
                .andExpect(jsonPath("$.items[1].visitId").value(secondVisitId.toString()))
                .andExpect(jsonPath("$.items[1].position").value(2))
                .andExpect(jsonPath("$.items[1].currentRoom").doesNotExist());

        mvc.perform(get("/api/v1/reception-queue").with(actor(receptionistId, AccountType.RECEPTIONIST))
                        .param("date", clinicDate.toString()).param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].visitId").value(accountlessVisitId.toString()))
                .andExpect(jsonPath("$.items[0].position").value(3));
    }

    @Test
    void patientSeesOnlyOwnProgressAndPositionOnlyWhileWalkInIsWaiting() throws Exception {
        mvc.perform(get("/api/v1/visits/{visitId}/progress", firstVisitId)
                        .with(actor(patientAAccountId, AccountType.PATIENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queuePosition").value(1))
                .andExpect(jsonPath("$.currentRoom").value("T049-R1"));

        mvc.perform(get("/api/v1/visits/{visitId}/progress", secondVisitId)
                        .with(actor(patientAAccountId, AccountType.PATIENT)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/visits/{visitId}/progress", accountlessVisitId)
                        .with(actor(patientAAccountId, AccountType.PATIENT)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/visits/{visitId}/progress", firstVisitId)
                        .with(actor(receptionistId, AccountType.RECEPTIONIST)))
                .andExpect(status().isForbidden());

        queueExit.exit(new ExitWalkInReceptionQueueCommand(firstVisitId, receptionistId,
                AccountRole.RECEPTIONIST, "CANCELLED", Instant.now(), "T049-exit-" + UUID.randomUUID()));
        mvc.perform(get("/api/v1/visits/{visitId}/progress", firstVisitId)
                        .with(actor(patientAAccountId, AccountType.PATIENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queuePosition").doesNotExist())
                .andExpect(jsonPath("$.currentStage").value("EXAMINATION"));
    }

    @Test
    void receptionistQueueRequiresReceptionistAndValidPagination() throws Exception {
        mvc.perform(get("/api/v1/reception-queue").with(actor(patientAAccountId, AccountType.PATIENT)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/reception-queue").with(actor(receptionistId, AccountType.RECEPTIONIST))
                        .param("date", clinicDate.toString()).param("page", "0").param("size", "101"))
                .andExpect(status().isBadRequest());
        assertTrue(jdbc.queryForObject("select count(*) from walk_in_reception_queue_entries "
                + "where visit_id in (?, ?, ?)", Integer.class, firstVisitId, secondVisitId, accountlessVisitId) == 3);
    }

    private UUID account(AccountType type) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into accounts (id, normalized_email, account_type, status, created_at, updated_at) "
                        + "values (?, ?, ?, 'ACTIVE', now(), now())",
                id, "t049-" + id + "@example.test", type.name());
        return id;
    }

    private void removeAbandonedT049DoctorFixtures() {
        List<UUID> staleAccountIds = jdbc.queryForList("select account_id from doctor_profiles "
                + "where full_name = 'T049 Doctor' and account_id in "
                + "(select id from accounts where normalized_email like 't049-%@example.test' and account_type = 'DOCTOR')",
                UUID.class);
        jdbc.update("delete from doctor_profiles where full_name = 'T049 Doctor' and account_id in "
                + "(select id from accounts where normalized_email like 't049-%@example.test' and account_type = 'DOCTOR')");
        for (UUID staleAccountId : staleAccountIds) {
            jdbc.update("delete from accounts where id = ? and normalized_email like 't049-%@example.test' "
                    + "and account_type = 'DOCTOR'", staleAccountId);
        }
    }

    private UUID profile(UUID accountId, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into patient_profiles (id, account_id, full_name, phone_canonical, phone_owner_type, "
                        + "date_of_birth, gender) values (?, ?, ?, ?, 'PATIENT', '1990-01-01', 'OTHER')",
                id, accountId, name, "+849" + id.toString().replace("-", "").substring(0, 9));
        return id;
    }

    private UUID visit(UUID patientProfileId, Instant arrivedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into visits (id, patient_profile_id, source, arrived_at, checked_in_at, "
                        + "created_by_account_id) values (?, ?, 'WALK_IN', ?, ?, ?)",
                id, patientProfileId, Timestamp.from(arrivedAt), Timestamp.from(arrivedAt), receptionistId);
        return id;
    }

    private void addQueueEntry(UUID visitId) {
        AuthenticatedAccount principal = new AuthenticatedAccount(receptionistId, AccountRole.RECEPTIONIST, UUID.randomUUID());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_RECEPTIONIST"))));
        queue.createWaitingEntry(new CreateWalkInReceptionQueueEntryCommand(visitId, principal,
                "T049-queue-" + UUID.randomUUID()));
    }

    private void assignRoom(UUID visitId) {
        doctorProfileId = UUID.randomUUID();
        roomId = UUID.randomUUID();
        assignmentId = UUID.randomUUID();
        Instant startsAt = atClinicTime(8, 0);
        jdbc.update("insert into doctor_profiles (id, account_id, full_name, photo_url, work_contact, specialties, "
                        + "qualifications, biography, consultation_information, publicly_visible, "
                        + "accepting_new_bookings, in_active_directory) values (?, ?, 'T049 Doctor', '', '', '', '', '', '', false, false, false)",
                doctorProfileId, doctorAccountId);
        jdbc.update("insert into rooms (id, room_code, display_label, active) values (?, 'T049-R1', 'T049 Room', true)", roomId);
        jdbc.update("insert into visit_assignments (id, visit_id, doctor_profile_id, starts_at, ends_at, status, "
                        + "assigned_at, step_code, expected_duration_minutes) values (?, ?, ?, ?, ?, 'IN_PROGRESS', ?, 'EXAMINATION', 30)",
                assignmentId, visitId, doctorProfileId, Timestamp.from(startsAt),
                Timestamp.from(startsAt.plusSeconds(1800)), Timestamp.from(startsAt));
        jdbc.update("insert into room_reservations (id, visit_id, assignment_id, room_id, step_code, capability_code, "
                        + "starts_at, expected_ends_at, reserved_until, cleanup_buffer_minutes, status, assigned_at) "
                        + "values (?, ?, ?, ?, 'EXAMINATION', 'GENERAL', ?, ?, ?, 0, 'IN_PROGRESS', ?)",
                UUID.randomUUID(), visitId, assignmentId, roomId, Timestamp.from(startsAt),
                Timestamp.from(startsAt.plusSeconds(1800)), Timestamp.from(startsAt.plusSeconds(1800)),
                Timestamp.from(startsAt));
    }

    private Instant atClinicTime(int hour, int minute) {
        return clinicDate.atTime(hour, minute).atZone(CLINIC_ZONE).toInstant();
    }

    private RequestPostProcessor actor(UUID accountId, AccountType type) {
        AccountRole role = AccountRole.valueOf(type.name());
        return authentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedAccount(accountId, role, UUID.randomUUID()), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + type.name()))));
    }
}
