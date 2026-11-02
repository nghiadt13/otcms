package fu.fa26.sep490.g58.otcms_be.visit;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitWorkflowService;
import fu.fa26.sep490.g58.otcms_be.visit.service.RecordedArrivalQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.exception.RecordedArrivalNotFoundException;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitEvidenceException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@LocalPostgresTest
class VisitT012PostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired VisitWorkflowService visits;
    @Autowired RecordedArrivalQueryService recordedArrivals;

    @AfterEach void cleanup() {
        jdbc.update("delete from appointment_arrivals where actor_account_id in (select id from accounts where normalized_email like 't012-%')");
        jdbc.update("delete from appointments where created_by_account_id in (select id from accounts where normalized_email like 't012-%')");
        jdbc.update("delete from visit_examinations where visit_id in (select id from visits where created_by_account_id in (select id from accounts where normalized_email like 't012-%'))");
        jdbc.update("delete from visit_assignments where visit_id in (select id from visits where created_by_account_id in (select id from accounts where normalized_email like 't012-%'))");
        jdbc.update("delete from visit_check_ins where receptionist_account_id in (select id from accounts where normalized_email like 't012-%')");
        jdbc.update("delete from visits where created_by_account_id in (select id from accounts where normalized_email like 't012-%')");
        jdbc.update("delete from doctor_services where doctor_profile_id in (select id from doctor_profiles where work_contact like 't012-%')");
        jdbc.update("delete from clinic_services where name = 'T012 service'");
        jdbc.update("delete from patient_profiles where full_name like 'T012 %'");
        jdbc.update("delete from doctor_profiles where work_contact like 't012-%'");
        jdbc.update("delete from accounts where normalized_email like 't012-%'");
    }

    @Test
    void cleanSchemaMigratesVisitReceptionClinicalAndReplayConstraints() {
        String schema = "t012_clean_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("create schema " + schema);
        try {
            Flyway flyway = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                    .locations("classpath:db/migration").target("18").load();
            assertEquals(20, flyway.migrate().migrationsExecuted);
            for (String table : new String[]{"clinic_visits", "recorded_appointment_arrivals", "visit_initial_receptions",
                    "walk_in_reception_replays", "visit_care_assignments", "visit_clinical_examinations"}) {
                assertEquals(1, jdbc.queryForObject("select count(*) from information_schema.tables where table_schema = ? and table_name = ?", Integer.class, schema, table));
            }
            assertIndex(schema, "uk_clinic_visits_originating_appointment");
            assertConstraint(schema, "uk_walk_in_reception_replay");
            assertConstraint(schema, "uk_visit_exam_doctor");
            for (String column : new String[]{"step_code", "expected_duration_minutes", "actual_ended_at"}) {
                assertEquals(1, jdbc.queryForObject("select count(*) from information_schema.columns where table_schema = ? and table_name = 'visit_care_assignments' and column_name = ?", Integer.class, schema, column));
            }
            assertEquals(1, jdbc.queryForObject("select count(*) from information_schema.tables where table_schema = ? and table_name = 'visit_care_assignment_audits'", Integer.class, schema));
            for (String table : new String[]{"visit_rooms", "visit_room_capabilities", "visit_room_reservations", "visit_room_reservation_audits"}) {
                assertEquals(1, jdbc.queryForObject("select count(*) from information_schema.tables where table_schema = ? and table_name = ?", Integer.class, schema, table));
            }
            assertConstraint(schema, "ck_visit_care_assignment_audit_action");
            assertEquals(32, jdbc.queryForObject("select character_maximum_length from information_schema.columns where table_schema = ? and table_name = 'visit_care_assignment_audits' and column_name = 'action'", Integer.class, schema));
            assertEquals(1, jdbc.queryForObject("select count(*) from pg_trigger t join pg_class c on c.oid = t.tgrelid join pg_namespace n on n.oid = c.relnamespace where n.nspname = ? and c.relname = 'visit_care_assignment_audits' and t.tgname = 'tr_visit_care_assignment_audits_immutable'", Integer.class, schema));
            assertConstraint(schema, "ex_visit_room_reservation_no_overlap");
            assertEquals(1, jdbc.queryForObject("select count(*) from pg_trigger t join pg_class c on c.oid = t.tgrelid join pg_namespace n on n.oid = c.relnamespace where n.nspname = ? and c.relname = 'visit_room_reservation_audits' and t.tgname = 'tr_visit_room_reservation_audits_immutable'", Integer.class, schema));
            assertEquals(1, jdbc.queryForObject("select count(*) from information_schema.tables where table_schema = ? and table_name = 'visit_room_management_audits'", Integer.class, schema));
            assertConstraint(schema, "ck_visit_room_management_audit_action");
            assertEquals(1, jdbc.queryForObject("select count(*) from pg_trigger t join pg_class c on c.oid = t.tgrelid join pg_namespace n on n.oid = c.relnamespace where n.nspname = ? and c.relname = 'visit_room_management_audits' and t.tgname = 'tr_visit_room_management_audits_immutable'", Integer.class, schema));
        } finally {
            jdbc.execute("drop schema if exists " + schema + " cascade");
        }
    }

    @Test
    void v018UpgradePreservesExistingVisitCareAndRoomRows() {
        String schema = "t031_v018_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("create schema " + schema);
        try {
            Flyway beforeRoom = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                    .locations("classpath:db/migration").target("16").load();
            assertEquals(18, beforeRoom.migrate().migrationsExecuted);

            UUID patientAccount = UUID.randomUUID();
            UUID doctorAccount = UUID.randomUUID();
            UUID receptionist = UUID.randomUUID();
            UUID patient = UUID.randomUUID();
            UUID doctor = UUID.randomUUID();
            UUID visit = UUID.randomUUID();
            UUID assignment = UUID.randomUUID();
            UUID roomId = UUID.randomUUID();
            Instant startsAt = Instant.parse("2026-10-19T10:00:00Z");
            for (Object[] account : new Object[][]{{patientAccount, "PATIENT"}, {doctorAccount, "DOCTOR"},
                    {receptionist, "RECEPTIONIST"}}) {
                jdbc.update("insert into " + schema + ".accounts (id, normalized_email, account_type, status, created_at, updated_at) values (?, ?, ?, 'ACTIVE', now(), now())",
                        account[0], account[0] + "@upgrade.test", account[1]);
            }
            jdbc.update("insert into " + schema + ".patient_profiles (id, account_id, full_name, phone_canonical, phone_owner_type, date_of_birth, gender) values (?, ?, 'Upgrade patient', '+84912345678', 'PATIENT', '1990-01-01', 'OTHER')",
                    patient, patientAccount);
            jdbc.update("insert into " + schema + ".doctor_profiles (id, account_id, full_name) values (?, ?, 'Upgrade doctor')",
                    doctor, doctorAccount);
            jdbc.update("insert into " + schema + ".clinic_visits (id, patient_profile_id, source, arrived_at, checked_in_at, created_by_account_id) values (?, ?, 'WALK_IN', ?, ?, ?)",
                    visit, patient, Timestamp.from(startsAt), Timestamp.from(startsAt), receptionist);
            jdbc.update("insert into " + schema + ".visit_care_assignments (id, visit_id, doctor_profile_id, starts_at, ends_at, status, step_code, expected_duration_minutes) values (?, ?, ?, ?, ?, 'COMPLETED', 'CONSULTATION', 30)",
                    assignment, visit, doctor, Timestamp.from(startsAt), Timestamp.from(startsAt.plusSeconds(1800)));
            Flyway withRooms = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                    .locations("classpath:db/migration").target("17").load();
            assertEquals(1, withRooms.migrate().migrationsExecuted);
            jdbc.update("insert into " + schema + ".visit_rooms (id, room_code, display_label, active) values (?, 'T031-UPGRADE-ROOM', 'Upgrade Room', true)", roomId);
            jdbc.update("insert into " + schema + ".visit_room_capabilities (room_id, capability_code) values (?, 'EXAM_ROOM')", roomId);

            Flyway withRoomManagement = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                    .locations("classpath:db/migration").target("18").load();
            assertEquals(1, withRoomManagement.migrate().migrationsExecuted);
            assertEquals(1, jdbc.queryForObject("select count(*) from " + schema + ".clinic_visits where id = ? and patient_profile_id = ?", Integer.class, visit, patient));
            assertEquals(1, jdbc.queryForObject("select count(*) from " + schema + ".visit_care_assignments where id = ? and visit_id = ? and step_code = 'CONSULTATION'", Integer.class, assignment, visit));
            assertEquals(1, jdbc.queryForObject("select count(*) from " + schema + ".visit_rooms where id = ? and room_code = 'T031-UPGRADE-ROOM'", Integer.class, roomId));
            assertEquals("018", jdbc.queryForObject("select version from " + schema + ".flyway_schema_history where success order by installed_rank desc limit 1", String.class));
        } finally {
            jdbc.execute("drop schema if exists " + schema + " cascade");
        }
    }

    @Test
    void followUpEvidenceRequiresSameDoctorCareCompletionAndIndication() {
        UUID patientAccountId = insertAccount(AccountType.PATIENT, "patient");
        UUID doctorAccountId = insertAccount(AccountType.DOCTOR, "doctor");
        UUID receptionistAccountId = insertAccount(AccountType.RECEPTIONIST, "receptionist");
        UUID patientId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();
        UUID visitId = UUID.randomUUID();
        UUID otherDoctorId = UUID.randomUUID();
        jdbc.update("insert into patient_profiles (id, account_id, full_name, phone_canonical, phone_owner_type, date_of_birth, gender) values (?, ?, ?, ?, 'PATIENT', '1990-01-01', 'OTHER')",
                patientId, patientAccountId, "T012 Patient", "+84900000001");
        jdbc.update("insert into doctor_profiles (id, account_id, full_name, work_contact) values (?, ?, ?, ?)",
                doctorId, doctorAccountId, "T012 Doctor", "t012-doctor");
        jdbc.update("insert into doctor_profiles (id, account_id, full_name, work_contact) values (?, ?, ?, ?)",
                otherDoctorId, insertAccount(AccountType.DOCTOR, "other-doctor"), "T012 Other Doctor", "t012-other-doctor");
        Instant arrived = Instant.parse("2026-10-13T10:00:00Z");
        jdbc.update("insert into visits (id, patient_profile_id, source, arrived_at, checked_in_at, created_by_account_id) values (?, ?, 'WALK_IN', ?, ?, ?)",
                visitId, patientId, Timestamp.from(arrived), Timestamp.from(arrived), receptionistAccountId);
        jdbc.update("insert into visit_assignments (visit_id, doctor_profile_id, starts_at, ends_at, status) values (?, ?, ?, ?, 'COMPLETED')",
                visitId, doctorId, Timestamp.from(arrived), Timestamp.from(arrived.plusSeconds(1800)));
        jdbc.update("insert into visit_examinations (visit_id, doctor_profile_id, completed, follow_up_indicated, completed_at) values (?, ?, true, true, ?)",
                visitId, doctorId, Timestamp.from(arrived.plusSeconds(1800)));

        VisitWorkflowService.FollowUpEvidence evidence = visits.requireFollowUpEvidence(visitId, doctorId);
        assertEquals(patientId, evidence.patientProfileId());
        assertTrue(evidence.doctorAssignedToCare());
        assertTrue(evidence.examinationCompleted());
        assertTrue(evidence.followUpIndicated());
        assertNull(evidence.originatingAppointmentId());

        VisitWorkflowService.FollowUpEvidence otherDoctorEvidence = visits.requireFollowUpEvidence(visitId, otherDoctorId);
        assertFalse(otherDoctorEvidence.doctorAssignedToCare());
        assertFalse(otherDoctorEvidence.examinationCompleted());
        assertFalse(otherDoctorEvidence.followUpIndicated());
        assertThrows(VisitEvidenceException.class, () -> visits.requireFollowUpEvidence(UUID.randomUUID(), doctorId));
    }

    @Test
    void recordedArrivalReaderRequiresAppointmentPatientAndInclusiveWindowMatch() {
        UUID receptionistId = insertAccount(AccountType.RECEPTIONIST, "arrival-receptionist");
        UUID patientAccountId = insertAccount(AccountType.PATIENT, "arrival-patient");
        UUID doctorAccountId = insertAccount(AccountType.DOCTOR, "arrival-doctor");
        UUID patientId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        UUID arrivalReference = receptionistId;
        Instant arrivedAt = Instant.parse("2026-11-02T10:00:00Z");

        jdbc.update("insert into patient_profiles (id, account_id, full_name, phone_canonical, phone_owner_type, date_of_birth, gender) values (?, ?, ?, ?, 'PATIENT', '1990-01-01', 'OTHER')",
                patientId, patientAccountId, "T012 Arrival Patient", "+84900000002");
        jdbc.update("insert into doctor_profiles (id, account_id, full_name, work_contact) values (?, ?, ?, ?)",
                doctorId, doctorAccountId, "T012 Arrival Doctor", "t012-arrival-doctor");
        jdbc.update("insert into clinic_services (id, name, duration_minutes, buffer_minutes, online_bookable, active) values (?, 'T012 service', 30, 0, true, true)", serviceId);
        jdbc.update("insert into doctor_services (doctor_profile_id, service_id) values (?, ?)", doctorId, serviceId);
        Instant startsAt = arrivedAt.plusSeconds(3600);
        jdbc.update("insert into appointments (id, patient_profile_id, service_id, doctor_profile_id, booking_source, purpose, start_time, end_time, duration_minutes, buffer_minutes, created_by_account_id) values (?, ?, ?, ?, 'PATIENT_ONLINE', 'T012 arrival evidence', ?, ?, 30, 0, ?)",
                appointmentId, patientId, serviceId, doctorId, Timestamp.from(startsAt), Timestamp.from(startsAt.plusSeconds(1800)), patientAccountId);
        jdbc.update("insert into appointment_arrivals (appointment_id, patient_profile_id, actor_account_id, source, arrived_at) values (?, ?, ?, 'LEGACY_RECEPTIONIST', ?)",
                appointmentId, patientId, receptionistId, Timestamp.from(arrivedAt));

        RecordedArrivalQueryService.RecordedArrival result = recordedArrivals.requireRecordedArrival(
                appointmentId, patientId, arrivedAt, arrivedAt);
        assertEquals(appointmentId, result.appointmentId());
        assertEquals(patientId, result.patientProfileId());
        assertEquals(arrivedAt, result.arrivedAt());
        assertEquals(arrivalReference, result.actorAccountId());
        assertThrows(RecordedArrivalNotFoundException.class, () -> recordedArrivals.requireRecordedArrival(
                appointmentId, UUID.randomUUID(), arrivedAt.minusSeconds(1), arrivedAt.plusSeconds(1)));
        assertThrows(RecordedArrivalNotFoundException.class, () -> recordedArrivals.requireRecordedArrival(
                appointmentId, patientId, arrivedAt.plusSeconds(1), arrivedAt.plusSeconds(2)));
        VisitEvidenceException invalidWindow = assertThrows(VisitEvidenceException.class,
                () -> recordedArrivals.requireRecordedArrival(appointmentId, patientId, arrivedAt.plusSeconds(1), arrivedAt));
        assertEquals("RECORDED_ARRIVAL_WINDOW_INVALID", invalidWindow.getMessage());
    }

    private void assertConstraint(String schema, String constraint) {
        assertEquals(1, jdbc.queryForObject("select count(*) from pg_constraint c join pg_class t on t.oid = c.conrelid join pg_namespace n on n.oid = t.relnamespace where n.nspname = ? and c.conname = ?", Integer.class, schema, constraint));
    }

    private void assertIndex(String schema, String index) {
        assertEquals(1, jdbc.queryForObject("select count(*) from pg_indexes where schemaname = ? and indexname = ?", Integer.class, schema, index));
    }

    private UUID insertAccount(AccountType type, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into accounts (id, normalized_email, account_type, status, created_at, updated_at) values (?, ?, ?, 'ACTIVE', now(), now())",
                id, "t012-" + role + "-" + id + "@example.test", type.name());
        return id;
    }
}
