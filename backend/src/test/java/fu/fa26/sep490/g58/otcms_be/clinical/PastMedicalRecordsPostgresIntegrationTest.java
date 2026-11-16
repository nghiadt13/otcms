package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.junit.jupiter.api.Assertions.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalPastMedicalRecordsService;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticPatientHistoryQueryService;
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
    @Autowired DiagnosticPatientHistoryQueryService diagnostic;
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
        jdbc.update("insert into clinical_service_orders (id,order_code,ordering_round_id,round_number,ordered_at,"
                + "visit_id,patient_id,service_id,service_name_snapshot,service_type,requesting_doctor_id,clinical_reason,body_area,"
                + "price_snapshot,order_status,payment_status,created_at,updated_at,ordering_doctor_account_id,room_id,imaging_type,"
                + "execution_status,attempt_id,first_use_event_id,first_use_type,first_use_source_id,first_use_at,study_ref,"
                + "execution_created_at,execution_updated_at) "
                + "values (?,?,?,1,now(),?,?,?,'UC33 MRI','IMAGING',?,'UC33 fixture','KNEE',1,'ORDERED','PAID',now(),now(),"
                + "?,?,'MRI','REVIEWED',?,?,'ACQUISITION_STARTED','UC33',now(),?,now(),now())",
                id, id.toString().replace("-", ""), UUID.randomUUID(), visit, requestPatient, UUID.randomUUID(), oldDoctor,
                oldAccount, UUID.randomUUID(), attempt, UUID.randomUUID(), studyRef(id));
        return id;
    }
    private String studyRef(UUID requestId) { return "uc33-study-" + requestId; }
    void finding(UUID request, UUID visit, int version, boolean completed, boolean released) {
        UUID report = UUID.randomUUID(); String study = studyRef(request);
        jdbc.update("insert into diagnostic_reports (id,service_order_id,visit_id,study_ref,version_number,findings_text,"
                + "recorded_by_doctor_id,state,completed_at,report_id,report_storage_ref,report_sha256,validation_status,validated_at,released_at) "
                + "values (?,?,?,?,?,'UC33 finding',?,?,?,?,?,?,?,?,?)", UUID.randomUUID(), request, visit, study, version,
                oldAccount, completed ? "COMPLETED" : "DRAFT", completed ? Timestamp.from(now) : null,
                completed ? report : null, completed ? "uc33-private-" + report : null, completed ? "a".repeat(64) : null,
                completed ? "VALIDATED" : null, completed ? Timestamp.from(now) : null, released ? Timestamp.from(now) : null);
    }
    @Test void completedReleasedImagingOfDifferentDoctorIsIncludedButDraftUnreleasedAndOtherPatientAreNot() {
        UUID old = UUID.randomUUID(); visit(old, "CLOSED", now.minusSeconds(5000), "COMPLETED", oldDoctor);
        UUID allowed = request(old, patient); finding(allowed, old, 1, true, true); finding(allowed, old, 2, false, false);
        UUID draft = request(old, patient); finding(draft, old, 1, false, false);
        UUID privateReport = request(old, patient); finding(privateReport, old, 1, true, false);
        UUID otherPatient = request(old, UUID.randomUUID()); finding(otherPatient, old, 1, true, true);
        var result = history.getPastMedicalRecords(current, doctor, account, 0, 20);
        assertEquals(List.of(allowed), result.items().getFirst().imagingResults().stream().map(i -> i.requestId()).toList());
        assertEquals(1, diagnostic.findReleased(patient, List.of(old), account, 0, 20).size());
    }
}
