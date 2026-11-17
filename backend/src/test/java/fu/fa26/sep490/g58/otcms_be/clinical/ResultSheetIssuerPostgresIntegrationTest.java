package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.junit.jupiter.api.Assertions.*;

import fu.fa26.sep490.g58.otcms_be.clinical.service.resultsheet.ResultSheetIssuer;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import fu.fa26.sep490.g58.otcms_be.visit.service.ManageVisitCareAssignmentService;
import fu.fa26.sep490.g58.otcms_be.visit.service.WalkInVisitLifecycleService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Exercises the actual completion transaction and its after-commit issuer on a dedicated schema. */
@SpringBootTest
@LocalPostgresTest
class ResultSheetIssuerPostgresIntegrationTest {
    private static final String SCHEMA = "p06_result_" + UUID.randomUUID().toString().replace("-", "");

    @DynamicPropertySource
    static void schema(DynamicPropertyRegistry properties) {
        properties.add("spring.flyway.schemas", () -> SCHEMA);
        properties.add("spring.flyway.default-schema", () -> SCHEMA);
        properties.add("spring.datasource.hikari.connection-init-sql", () -> "set search_path to " + SCHEMA + ", public");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired WalkInVisitLifecycleService lifecycle;
    @Autowired ResultSheetIssuer issuer;
    // Care assignment completion is separately covered by VisitExamFlowPostgresIntegrationTest.
    @MockitoBean ManageVisitCareAssignmentService careAssignments;

    @AfterAll static void dropSchema(@Autowired JdbcTemplate jdbc) {
        jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    }

    @Test void completingVisitIssuesOneReleasedPdfAndOneMessageEvenWhenRetried() {
        UUID receptionist = account("RECEPTIONIST"), doctorAccount = account("DOCTOR"), patientAccount = account("PATIENT");
        UUID doctor = UUID.randomUUID(), patient = UUID.randomUUID(), visit = UUID.randomUUID(), examination = UUID.randomUUID();
        Instant now = Instant.now().minusSeconds(120);
        Timestamp at = Timestamp.from(now);
        jdbc.update("insert into patient_profiles (id, account_id, full_name, phone_canonical, phone_owner_type, date_of_birth, gender) values (?, ?, ?, ?, 'PATIENT', '1990-01-01', 'FEMALE')",
                patient, patientAccount, "Nguyễn Thị Ánh", "+849" + patient.toString().replace("-", "").substring(0, 9));
        jdbc.update("insert into staff_profiles (is_doctor, id, account_id, display_name, publicly_visible, accepting_new_bookings, in_active_directory) values (true, ?, ?, ?, true, true, true)",
                doctor, doctorAccount, "Trần Thị Hương");
        jdbc.update("insert into visits (id, patient_profile_id, source, arrived_at, checked_in_at, created_by_account_id) values (?, ?, 'WALK_IN', ?, ?, ?)",
                visit, patient, at, at, receptionist);
        jdbc.update("insert into queue_entries (visit_id, stage, status, entered_at, doctor_profile_id, clinic_date, queue_number, queued_at, started_at) values (?, 'EXAM', 'IN_EXAMINATION', ?, ?, current_date, 1, ?, ?)",
                visit, at, doctor, at, at);
        jdbc.update("insert into clinical_examinations (id, visit_id, reason_for_visit, symptoms, examination_findings, status, created_at, updated_at, version) values (?, ?, ?, ?, ?, 'COMPLETED', ?, ?, 0)",
                examination, visit, "Đau gối", "Đau khi đi", "Sưng", at, at);
        jdbc.update("insert into clinical_examination_diagnoses (id, examination_id, code, type) values (?, ?, 'M25.5', 'PRIMARY')",
                UUID.randomUUID(), examination);

        lifecycle.completeWalkIn(visit, doctorAccount, AccountRole.DOCTOR, UUID.randomUUID().toString());

        assertEquals("CLOSED", jdbc.queryForObject("select status from visits where id = ?", String.class, visit));
        assertEquals(1, count("select count(*) from clinical_medical_documents where visit_id = ? and status = 'ACTIVE' and source = 'GENERATED' and version = 1 and released_at is not null", visit));
        UUID document = jdbc.queryForObject("select id from clinical_medical_documents where visit_id = ?", UUID.class, visit);
        assertTrue(jdbc.queryForObject("select octet_length(content) from clinical_medical_document_payloads where document_id = ?", Integer.class, document) > 0);
        assertEquals(1, count("select count(*) from notifications where related_id = ? and type = 'VISIT_RESULTS_PUBLISHED'", visit));
        String message = jdbc.queryForObject("select content from notifications where related_id = ? and type = 'VISIT_RESULTS_PUBLISHED'", String.class, visit);
        String expectedDate = java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu").withZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).format(now);
        assertEquals("Kết quả khám ngày " + expectedDate + " của Nguyễn Thị Ánh đã được công bố. Vui lòng đăng nhập để xem.", message);
        assertEquals(patientAccount, jdbc.queryForObject("select recipient_account_id from notifications where related_id = ? and type = 'VISIT_RESULTS_PUBLISHED'", UUID.class, visit));

        issuer.issue(visit, doctor);
        assertEquals(1, count("select count(*) from clinical_medical_documents where visit_id = ?", visit));
        assertEquals(1, count("select count(*) from notifications where related_id = ? and type = 'VISIT_RESULTS_PUBLISHED'", visit));
    }

    private int count(String sql, UUID id) { return jdbc.queryForObject(sql, Integer.class, id); }
    private UUID account(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into accounts (id, normalized_email, account_type, status, created_at, updated_at) values (?, ?, ?, 'ACTIVE', now(), now())", id, id + "@example.test", role);
        return id;
    }
}
