package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.junit.jupiter.api.Assertions.*;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.model.DiagnosisType;
import fu.fa26.sep490.g58.otcms_be.clinical.model.dto.ClinicalDtos.AdviceRequest;
import fu.fa26.sep490.g58.otcms_be.clinical.model.dto.ClinicalDtos.AmendmentRequest;
import fu.fa26.sep490.g58.otcms_be.clinical.model.dto.ClinicalDtos.DiagnosisRequest;
import fu.fa26.sep490.g58.otcms_be.clinical.model.dto.ClinicalDtos.ExaminationRequest;
import fu.fa26.sep490.g58.otcms_be.clinical.model.dto.ClinicalDtos.PrescriptionAmendment;
import fu.fa26.sep490.g58.otcms_be.clinical.model.dto.PrescriptionDtos.ItemRequest;
import fu.fa26.sep490.g58.otcms_be.clinical.service.resultsheet.ResultAmendmentService;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import fu.fa26.sep490.g58.otcms_be.visit.service.ManageVisitCareAssignmentService;
import fu.fa26.sep490.g58.otcms_be.visit.service.WalkInVisitLifecycleService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** UC-38 on a real schema: complete a visit (v1 issued), then amend it through the public service. */
@SpringBootTest
@LocalPostgresTest
class ResultAmendmentPostgresIntegrationTest {
    private static final String SCHEMA = "p06_amend_" + UUID.randomUUID().toString().replace("-", "");

    @DynamicPropertySource
    static void schema(DynamicPropertyRegistry properties) {
        properties.add("spring.flyway.schemas", () -> SCHEMA);
        properties.add("spring.flyway.default-schema", () -> SCHEMA);
        properties.add("spring.datasource.hikari.connection-init-sql", () -> "set search_path to " + SCHEMA + ", public");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired WalkInVisitLifecycleService lifecycle;
    @Autowired ResultAmendmentService amendments;
    @MockitoBean ManageVisitCareAssignmentService careAssignments;

    @AfterAll static void dropSchema(@Autowired JdbcTemplate jdbc) {
        jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    }

    record Fixture(UUID visit, UUID patient, UUID patientAccount, UUID doctor, UUID doctorAccount, UUID examination) {
        AuthenticatedAccount doctorActor() { return new AuthenticatedAccount(doctorAccount, AccountRole.DOCTOR, null); }
    }

    @Test void amendingCreatesVersionTwoReplacesVersionOneAuditsOldValuesAndNotifiesPatient() {
        Fixture f = closedVisit(false);
        var request = new AmendmentRequest(
                new ExaminationRequest("Đau gối", "Đau khi đi", "Tràn dịch khớp", "Gối", "LEFT", "Ngã",
                        List.of(new DiagnosisRequest("m23.2", DiagnosisType.PRIMARY)), "Sưng", List.of("HOME_CARE")),
                new AdviceRequest("Chườm lạnh", null, null, null, "Tái khám sau 2 tuần", true), null,
                "  Nhập nhầm chẩn đoán lúc khám  ");

        var result = amendments.amend(f.doctorActor(), f.visit(), request);

        assertEquals(2, result.version());
        assertEquals(1, count("select count(*) from clinical_medical_documents where visit_id = ? and status = 'ACTIVE' and version = 2 and released_at is not null", f.visit()));
        assertEquals(1, count("select count(*) from clinical_medical_documents where visit_id = ? and status = 'REPLACED' and version = 1", f.visit()));
        assertEquals(result.documentId(), jdbc.queryForObject("select id from clinical_medical_documents where visit_id = ? and status = 'ACTIVE'", UUID.class, f.visit()));
        assertTrue(jdbc.queryForObject("select octet_length(content) from clinical_medical_document_payloads where document_id = ?", Integer.class, result.documentId()) > 0);
        // The stored data was changed.
        assertEquals("Tràn dịch khớp", jdbc.queryForObject("select examination_findings from clinical_examinations where id = ?", String.class, f.examination()));
        assertEquals(List.of("M23.2"), jdbc.queryForList("select code from clinical_examination_diagnoses where examination_id = ?", String.class, f.examination()));
        assertEquals("Chườm lạnh", jdbc.queryForObject("select home_care_advice from clinical_examinations where id = ?", String.class, f.examination()));
        // Audit holds the reason and the previous values.
        assertEquals(1, count("select count(*) from audit_logs where entity_id = ? and action = 'RESULT_AMENDED' and correction_reason = 'Nhập nhầm chẩn đoán lúc khám'", f.visit()));
        String before = jdbc.queryForObject("select before_state::text from audit_logs where entity_id = ? and action = 'RESULT_AMENDED'", String.class, f.visit());
        assertTrue(before.contains("Sưng") && before.contains("M25.5"), before);
        // MSG-79 for the patient, in addition to the MSG-78 sent with v1.
        assertEquals(1, count("select count(*) from notifications where related_id = ? and type = 'RESULT_SHEET_NEW_VERSION' and recipient_account_id = '" + f.patientAccount() + "'", f.visit()));
        String message = jdbc.queryForObject("select content from notifications where related_id = ? and type = 'RESULT_SHEET_NEW_VERSION'", String.class, f.visit());
        assertTrue(message.startsWith("Phiếu kết quả khám ngày ") && message.endsWith("đã có phiên bản mới. Vui lòng xem lại."), message);

        // A second amendment continues the chain: v3 ACTIVE, v1 and v2 REPLACED.
        var second = amendments.amend(f.doctorActor(), f.visit(), new AmendmentRequest(null,
                new AdviceRequest("Chườm nóng", null, null, null, null, null), null, "Đổi lời dặn"));
        assertEquals(3, second.version());
        assertEquals(2, count("select count(*) from clinical_medical_documents where visit_id = ? and status = 'REPLACED'", f.visit()));
        assertEquals(1, count("select count(*) from clinical_medical_documents where visit_id = ? and status = 'ACTIVE'", f.visit()));
    }

    @Test void amendingAnIssuedPrescriptionReplacesItemsAndKeepsOldItemsInAudit() {
        Fixture f = closedVisit(true);
        amendments.amend(f.doctorActor(), f.visit(), new AmendmentRequest(null, null,
                new PrescriptionAmendment(List.of(new ItemRequest("Paracetamol", "500mg", "2 lần/ngày", "Sau ăn"))), "Sai liều"));
        assertEquals(List.of("Paracetamol"), jdbc.queryForList("select i.medicine from clinical_prescription_items i join clinical_prescriptions p on p.id = i.prescription_id where p.visit_id = ?", String.class, f.visit()));
        String before = jdbc.queryForObject("select before_state::text from audit_logs where entity_id = ? and action = 'RESULT_AMENDED'", String.class, f.visit());
        assertTrue(before.contains("Ibuprofen"), before);
    }

    @Test void onlyTheAttendingDoctorMayAmendAndNothingChangesOtherwise() {
        Fixture f = closedVisit(false);
        UUID otherAccount = account("DOCTOR");
        jdbc.update("insert into staff_profiles (is_doctor, id, account_id, display_name, publicly_visible, accepting_new_bookings, in_active_directory) values (true, ?, ?, 'Bác sĩ khác', true, true, true)",
                UUID.randomUUID(), otherAccount);
        var request = new AmendmentRequest(null, new AdviceRequest("Nghỉ ngơi", null, null, null, null, null), null, "Lý do");
        assertCode("MSG-40", () -> amendments.amend(new AuthenticatedAccount(otherAccount, AccountRole.DOCTOR, null), f.visit(), request));
        assertCode("MSG-40", () -> amendments.amend(new AuthenticatedAccount(f.patientAccount(), AccountRole.PATIENT, null), f.visit(), request));
        assertUntouched(f);
    }

    @Test void visitThatIsNotClosedCannotBeAmended() {
        Fixture f = closedVisit(false);
        jdbc.update("update visits set status = 'INCOMPLETE', incomplete_at = now() where id = ?", f.visit());
        assertCode("MSG-42", () -> amendments.amend(f.doctorActor(), f.visit(), new AmendmentRequest(null,
                new AdviceRequest("Nghỉ ngơi", null, null, null, null, null), null, "Lý do")));
        assertUntouched(f);
    }

    @Test void reasonIsRequiredAndAtLeastOneSectionMustChange() {
        Fixture f = closedVisit(false);
        var advice = new AdviceRequest("Nghỉ ngơi", null, null, null, null, null);
        assertCode("MSG-39", () -> amendments.amend(f.doctorActor(), f.visit(), new AmendmentRequest(null, advice, null, null)));
        assertCode("MSG-39", () -> amendments.amend(f.doctorActor(), f.visit(), new AmendmentRequest(null, advice, null, "   ")));
        assertCode("MSG-39", () -> amendments.amend(f.doctorActor(), f.visit(), new AmendmentRequest(null, null, null, "Lý do")));
        // Invalid ICD-10 code: validated like completion, nothing is written.
        assertCode("MSG-39", () -> amendments.amend(f.doctorActor(), f.visit(), new AmendmentRequest(
                new ExaminationRequest("a", "b", "c", null, null, null, List.of(new DiagnosisRequest("ZZZ", DiagnosisType.PRIMARY)), null, null), null, null, "Lý do")));
        assertUntouched(f);
    }

    @Test void currentValuesAreAvailableOnlyToTheAttendingDoctorOfAClosedVisit() {
        Fixture f = closedVisit(true);
        var current = amendments.current(f.doctorActor(), f.visit());
        assertEquals("Sưng", current.examination().examinationFindings());
        assertEquals("M25.5", current.examination().diagnoses().getFirst().code());
        assertEquals("Ibuprofen", current.prescriptionItems().getFirst().medicine());
        assertEquals(1L, current.resultSheetVersion());
        assertCode("MSG-40", () -> amendments.current(new AuthenticatedAccount(f.patientAccount(), AccountRole.PATIENT, null), f.visit()));
        jdbc.update("update visits set status = 'INCOMPLETE', incomplete_at = now() where id = ?", f.visit());
        assertCode("MSG-42", () -> amendments.current(f.doctorActor(), f.visit()));
    }

    private void assertUntouched(Fixture f) {
        assertEquals(1, count("select count(*) from clinical_medical_documents where visit_id = ?", f.visit()));
        assertEquals(1, count("select count(*) from clinical_medical_documents where visit_id = ? and status = 'ACTIVE' and version = 1", f.visit()));
        assertEquals(0, count("select count(*) from audit_logs where entity_id = ? and action = 'RESULT_AMENDED'", f.visit()));
        assertEquals(0, count("select count(*) from notifications where related_id = ? and type = 'RESULT_SHEET_NEW_VERSION'", f.visit()));
        assertEquals("Sưng", jdbc.queryForObject("select examination_findings from clinical_examinations where id = ?", String.class, f.examination()));
    }

    private static void assertCode(String code, Runnable action) {
        assertEquals(code, assertThrows(ClinicalOperationException.class, action::run).code());
    }

    private Fixture closedVisit(boolean withPrescription) {
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
        jdbc.update("update clinical_examinations set injury_body_site = 'Gối', injury_side = 'LEFT', injury_mechanism = 'Ngã', injury_description = 'Sưng', treatment_directions = ARRAY['HOME_CARE'] where id = ?", examination);
        jdbc.update("insert into clinical_examination_diagnoses (id, examination_id, code, type) values (?, ?, 'M25.5', 'PRIMARY')",
                UUID.randomUUID(), examination);
        if (withPrescription) {
            UUID prescription = UUID.randomUUID();
            jdbc.update("insert into clinical_prescriptions (id, visit_id, patient_profile_id, doctor_profile_id, status, issued_at, created_at, updated_at, version, issuance_key, issuance_expected_version) values (?, ?, ?, ?, 'ISSUED', ?, ?, ?, 2, 'key-1', 1)",
                    prescription, visit, patient, doctor, at, at, at);
            jdbc.update("insert into clinical_prescription_items (id, prescription_id, line_number, medicine, dose, frequency, usage) values (?, ?, 1, 'Ibuprofen', '400mg', '3 lần/ngày', 'Sau ăn')",
                    UUID.randomUUID(), prescription);
        }
        lifecycle.completeWalkIn(visit, doctorAccount, AccountRole.DOCTOR, UUID.randomUUID().toString());
        assertEquals(1, count("select count(*) from clinical_medical_documents where visit_id = ? and status = 'ACTIVE' and version = 1", visit));
        return new Fixture(visit, patient, patientAccount, doctor, doctorAccount, examination);
    }

    private int count(String sql, UUID id) { return jdbc.queryForObject(sql, Integer.class, id); }
    private UUID account(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into accounts (id, normalized_email, account_type, status, created_at, updated_at) values (?, ?, ?, 'ACTIVE', now(), now())", id, id + "@example.test", role);
        return id;
    }
}
