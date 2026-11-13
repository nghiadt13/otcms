package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.junit.jupiter.api.Assertions.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalPastMedicalRecordsService;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticRequestJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticRequestStatus;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitPatientHistoryQueryService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Dedicated test DB only; UUID fixtures and rollback. Compiling this is not PostgreSQL verification. */
@SpringBootTest @LocalPostgresTest @Transactional
class PastMedicalRecordsPostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired VisitPatientHistoryQueryService visits;
    @Autowired ClinicalPastMedicalRecordsService history;
    @Autowired DiagnosticRequestJpaRepository diagnostic;
    UUID patient = UUID.randomUUID(), current = UUID.randomUUID(), doctor = UUID.randomUUID(), account = UUID.randomUUID();
    UUID oldDoctor = UUID.randomUUID(), oldAccount = UUID.randomUUID();
    Instant now = Instant.parse("2026-11-05T12:00:00Z");
    @BeforeEach void setup() {
        account(account, "DOCTOR"); account(oldAccount, "DOCTOR"); UUID patientAccount = UUID.randomUUID(); account(patientAccount, "PATIENT");
        jdbc.update("insert into patient_profiles (id,account_id,full_name,phone_canonical,phone_owner_type,date_of_birth,gender) values (?,?,'UC33 fixture','+84977773333','PATIENT','1990-01-01','OTHER')", patient, patientAccount);
        jdbc.update("insert into staff_profiles (is_doctor, id,account_id,display_name,work_contact) values (true, ?,?,'UC33 current',?)", doctor, account, "uc33-" + doctor);
        jdbc.update("insert into staff_profiles (is_doctor, id,account_id,display_name,work_contact) values (true, ?,?,'UC33 previous',?)", oldDoctor, oldAccount, "uc33-" + oldDoctor);
        visit(current, "OPEN", now, "IN_PROGRESS", doctor);
    }
    void account(UUID id, String role) {
        jdbc.update("insert into accounts (id,normalized_email,account_type,status,created_at,updated_at) values (?,?,?,'ACTIVE',now(),now())", id, "uc33-" + id + "@example.test", role);
    }
    void visit(UUID id, String status, Instant arrived, String assignmentStatus, UUID assignedDoctor) {
        jdbc.update("insert into visits (id,patient_profile_id,source,arrived_at,checked_in_at,created_by_account_id,status) values (?,?,'WALK_IN',?,?,?,?)",
                id, patient, Timestamp.from(arrived), Timestamp.from(arrived), account, status);
        if (assignmentStatus != null) jdbc.update("insert into visit_assignments (id,visit_id,doctor_profile_id,starts_at,ends_at,status) values (?,?,?,?,?,?)",
                UUID.randomUUID(), id, assignedDoctor, Timestamp.from(arrived), Timestamp.from(arrived.plusSeconds(1800)), assignmentStatus);
    }
    @Test void closedCompletedScopeExcludesCancellationActiveAndCurrentAndPagesWithoutDuplicates() {
        UUID old = UUID.randomUUID(), older = UUID.randomUUID();
        visit(old, "CLOSED", now.minusSeconds(5000), "COMPLETED", oldDoctor);
        visit(older, "CLOSED", now.minusSeconds(10000), "COMPLETED", oldDoctor);
        visit(UUID.randomUUID(), "CLOSED", now.minusSeconds(1000), null, oldDoctor);
        visit(UUID.randomUUID(), "OPEN", now.minusSeconds(2000), "COMPLETED", oldDoctor);
        var page = visits.findPastVisits(patient, current, 0, 1); assertEquals(2, page.total()); assertEquals(old, page.items().getFirst().visitId());
        assertEquals(older, visits.findPastVisits(patient, current, 1, 1).items().getFirst().visitId());
        assertTrue(visits.findPastVisits(patient, current, 2, 1).items().isEmpty());
        var result = history.getPastMedicalRecords(current, doctor, account, 0, 20);
        assertEquals(List.of(old, older), result.items().stream().map(v -> v.visitId()).toList());
    }
    UUID request(UUID visit, UUID requestPatient) {
        UUID id = UUID.randomUUID(), attempt = UUID.randomUUID();
        jdbc.update("insert into diagnostic_requests (id,visit_id,patient_id,ordering_doctor_id,service_id,room_id,imaging_type,body_area,clinical_reason,status) values (?,?,?,?,?,?,'MRI','KNEE','UC33 fixture','PENDING')",
                id, visit, requestPatient, oldAccount, UUID.randomUUID(), UUID.randomUUID());
        jdbc.update("insert into diagnostic_attempts (id,request_id,attempt_number,first_use_event_id,first_use_type,source_id,occurred_at) values (?,?,1,?,'ACQUISITION_STARTED','UC33',now())", attempt, id, UUID.randomUUID());
        jdbc.update("update diagnostic_requests set status='IN_PROGRESS',current_attempt_id=? where id=?", attempt, id);
        jdbc.update("update diagnostic_attempts set study_ref=? where id=? and request_id=?", studyRef(id), attempt, id);
        jdbc.update("update diagnostic_requests set status='RESULT_AVAILABLE' where id=?", id);
        jdbc.update("update diagnostic_requests set status='REVIEWED' where id=?", id);
        return id;
    }
    private String studyRef(UUID requestId) { return "uc33-study-" + requestId; }
    void finding(UUID request, UUID visit, int version, boolean completed, boolean released) {
        UUID report = UUID.randomUUID(); String study = studyRef(request);
        if (completed) jdbc.update("insert into diagnostic_reports (id,request_id,visit_id,study_ref,report_version,storage_ref,content_sha256,validation_status,uploaded_by_doctor_id,validated_at,released_at) values (?,?,?,?,?,?,?,'VALIDATED',?,now(),?)",
                report, request, visit, study, version, "uc33-private-" + report, "a".repeat(64), oldAccount, released ? Timestamp.from(now) : null);
        jdbc.update("insert into diagnostic_findings_versions (id,request_id,visit_id,study_ref,version_number,findings_text,recorded_by_doctor_id,state,required_pdf_report_id,completed_at) values (?,?,?,?,?,'UC33 finding',?,?,?,?)",
                UUID.randomUUID(), request, visit, study, version, oldAccount, completed ? "COMPLETED" : "DRAFT", completed ? report : null, completed ? Timestamp.from(now) : null);
    }
    @Test void completedReleasedImagingOfDifferentDoctorIsIncludedButDraftUnreleasedAndOtherPatientAreNot() {
        UUID old = UUID.randomUUID(); visit(old, "CLOSED", now.minusSeconds(5000), "COMPLETED", oldDoctor);
        UUID allowed = request(old, patient); finding(allowed, old, 1, true, true); finding(allowed, old, 2, false, false);
        UUID draft = request(old, patient); finding(draft, old, 1, false, false);
        UUID privateReport = request(old, patient); finding(privateReport, old, 1, true, false);
        UUID otherPatient = request(old, UUID.randomUUID()); finding(otherPatient, old, 1, true, true);
        var result = history.getPastMedicalRecords(current, doctor, account, 0, 20);
        assertEquals(List.of(allowed), result.items().getFirst().imagingResults().stream().map(i -> i.requestId()).toList());
        assertEquals(1, diagnostic.findReleasedPatientHistory(patient, List.of(old), List.of(DiagnosticRequestStatus.REVIEWED)).size());
    }
}
