package fu.fa26.sep490.g58.otcms_be.visit;

import static org.junit.jupiter.api.Assertions.*;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.service.AttendingDoctorTransferService;
import java.sql.Timestamp;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@LocalPostgresTest
class AttendingDoctorTransferPostgresIntegrationTest {
    private static final String SCHEMA = "p05_doctor_" + UUID.randomUUID().toString().replace("-", "");
    @DynamicPropertySource static void schema(DynamicPropertyRegistry properties) {
        properties.add("spring.flyway.schemas", () -> SCHEMA);
        properties.add("spring.flyway.default-schema", () -> SCHEMA);
        properties.add("spring.datasource.hikari.connection-init-sql", () -> "set search_path to " + SCHEMA + ", public");
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired AttendingDoctorTransferService transfers;
    @AfterAll static void dropSchema(@Autowired JdbcTemplate jdbc) { jdbc.execute("drop schema if exists " + SCHEMA + " cascade"); }

    @Test void onDutyDoctorReceivesExamEntryCareAssignmentAndNotification() {
        Fixture f = fixture("WAITING", true);
        var ticket = transfers.transfer(f.visit, f.newDoctor, "Doctor changed", f.actor(), "transfer-test");
        assertEquals(f.newDoctor, ticket.doctorProfileId());
        assertEquals(f.newRoom, ticket.roomId());
        assertEquals(f.newDoctor, jdbc.queryForObject("select doctor_profile_id from queue_entries where visit_id = ? and stage = 'EXAM'", UUID.class, f.visit));
        assertEquals(f.newRoom, jdbc.queryForObject("select room_id from queue_entries where visit_id = ? and stage = 'EXAM'", UUID.class, f.visit));
        assertEquals(f.newDoctor, jdbc.queryForObject("select doctor_profile_id from visit_assignments where visit_id = ?", UUID.class, f.visit));
        assertEquals(1, jdbc.queryForObject("select count(*) from notifications where recipient_account_id = ? and related_id = ? and type = 'VISIT_CHANGED'", Integer.class, f.newAccount, f.visit));
    }
    @Test void doctorWithoutShiftIsRejectedWithMsg41() {
        Fixture f = fixture("WAITING", false);
        assertEquals("MSG-41", assertThrows(VisitOperationException.class, () -> transfers.transfer(f.visit, f.newDoctor, "Doctor changed", f.actor(), "transfer-test")).code());
        assertEquals(f.oldDoctor, jdbc.queryForObject("select doctor_profile_id from queue_entries where visit_id = ? and stage = 'EXAM'", UUID.class, f.visit));
    }
    @Test void examinationInProgressCannotTransferDoctor() {
        Fixture f = fixture("IN_EXAMINATION", true);
        assertEquals("MSG-42", assertThrows(VisitOperationException.class, () -> transfers.transfer(f.visit, f.newDoctor, "Doctor changed", f.actor(), "transfer-test")).code());
        assertEquals(f.oldDoctor, jdbc.queryForObject("select doctor_profile_id from visit_assignments where visit_id = ?", UUID.class, f.visit));
    }
    private Fixture fixture(String status, boolean onDuty) {
        UUID receptionist = account("RECEPTIONIST"), oldAccount = account("DOCTOR"), newAccount = account("DOCTOR"), patientAccount = account("PATIENT");
        UUID patient = UUID.randomUUID(), oldDoctor = UUID.randomUUID(), newDoctor = UUID.randomUUID(), oldRoom = UUID.randomUUID(), newRoom = UUID.randomUUID(), visit = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("insert into patient_profiles (id, account_id, full_name, phone_canonical, phone_owner_type, date_of_birth, gender) values (?, ?, 'Transfer Patient', ?, 'PATIENT', '1990-01-01', 'FEMALE')", patient, patientAccount, "+849" + patient.toString().replace("-", "").substring(0, 9));
        for (UUID[] d : new UUID[][]{{oldDoctor, oldAccount}, {newDoctor, newAccount}}) jdbc.update("insert into staff_profiles (is_doctor, id, account_id, display_name, publicly_visible, accepting_new_bookings, in_active_directory) values (true, ?, ?, 'Transfer Doctor', true, true, true)", d[0], d[1]);
        for (UUID room : new UUID[]{oldRoom, newRoom}) jdbc.update("insert into rooms (id, room_code, display_label, active) values (?, ?, 'Transfer Room', true)", room, "T-" + room.toString().substring(0, 8));
        if (onDuty) jdbc.update("insert into work_shifts (id, staff_profile_id, starts_at, ends_at, room_id) values (?, ?, ?, ?, ?)", UUID.randomUUID(), newDoctor, Timestamp.from(now.minusSeconds(600)), Timestamp.from(now.plusSeconds(3600)), newRoom);
        jdbc.update("insert into visits (id, patient_profile_id, source, arrived_at, checked_in_at, created_by_account_id) values (?, ?, 'WALK_IN', ?, ?, ?)", visit, patient, Timestamp.from(now), Timestamp.from(now), receptionist);
        jdbc.update("insert into queue_entries (visit_id, stage, status, entered_at, doctor_profile_id, room_id, clinic_date, queue_number, queued_at, started_at) values (?, 'EXAM', ?, ?, ?, ?, ?, 1, ?, ?)", visit, status, Timestamp.from(now), oldDoctor, oldRoom, now.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate(), Timestamp.from(now), status.equals("IN_EXAMINATION") ? Timestamp.from(now) : null);
        jdbc.update("insert into visit_assignments (id, visit_id, doctor_profile_id, starts_at, ends_at, status) values (?, ?, ?, ?, ?, 'IN_PROGRESS')", UUID.randomUUID(), visit, oldDoctor, Timestamp.from(now), Timestamp.from(now.plusSeconds(1800)));
        return new Fixture(receptionist, oldDoctor, newDoctor, newAccount, newRoom, visit);
    }
    private UUID account(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into accounts (id, normalized_email, account_type, status, created_at, updated_at) values (?, ?, ?, 'ACTIVE', now(), now())", id, id + "@example.test", role);
        return id;
    }
    private record Fixture(UUID receptionist, UUID oldDoctor, UUID newDoctor, UUID newAccount, UUID newRoom, UUID visit) {
        AuthenticatedAccount actor() { return new AuthenticatedAccount(receptionist, AccountRole.RECEPTIONIST, UUID.randomUUID()); }
    }
}
