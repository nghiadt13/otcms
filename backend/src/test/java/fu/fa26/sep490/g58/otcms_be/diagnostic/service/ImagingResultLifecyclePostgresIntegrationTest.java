package fu.fa26.sep490.g58.otcms_be.diagnostic.service;

import static org.junit.jupiter.api.Assertions.*;

import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalVisitCompletionEvidenceService;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.ImagingStudyPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticReportUpload;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticVisitCompletionEvidenceService;
import fu.fa26.sep490.g58.otcms_be.imaging.integration.diagnostic.ImagingStudyContractAdapter;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingAssignedWork;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingStudyRepository;
import fu.fa26.sep490.g58.otcms_be.imaging.service.ImagingReconciliationService;
import fu.fa26.sep490.g58.otcms_be.imaging.service.impl.ImagingAuditPersistenceService;
import fu.fa26.sep490.g58.otcms_be.imaging.service.impl.ImagingConfirmationDispatchTransactionService;
import fu.fa26.sep490.g58.otcms_be.imaging.service.impl.ImagingSendService;
import fu.fa26.sep490.g58.otcms_be.imaging.service.impl.ImagingUploadRegistrationService;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import jakarta.persistence.EntityManager;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@LocalPostgresTest
@Transactional
@Import({ImagingResultLifecyclePostgresIntegrationTest.ActualImagingConfiguration.class,
        DiagnosticFindingsT014PostgresIntegrationTest.ReportStorageConfiguration.class})
class ImagingResultLifecyclePostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    @Autowired ClinicalServiceOrderExecutionPort executions;
    @Autowired ImagingUploadRegistrationService uploads;
    @Autowired ImagingSendService sends;
    @Autowired ImagingConfirmationDispatchTransactionService confirmation;
    @Autowired DiagnosticFindingsService findings;
    @Autowired ClinicalVisitCompletionEvidenceService clinicalCompletion;
    @Autowired DiagnosticVisitCompletionEvidenceService diagnosticCompletion;

    final UUID patientAccount = UUID.randomUUID(), patient = UUID.randomUUID(), visit = UUID.randomUUID();
    final UUID doctorAccount = UUID.randomUUID(), doctorProfile = UUID.randomUUID(), serviceId = UUID.randomUUID();
    final UUID room = UUID.randomUUID(), order = UUID.randomUUID(), attempt = UUID.randomUUID();
    final UUID technician = UUID.randomUUID();

    @BeforeEach void setup() {
        account(patientAccount, "PATIENT"); account(doctorAccount, "DOCTOR"); account(technician, "IMAGING_TECHNICIAN");
        jdbc.update("insert into patient_profiles (id,account_id,full_name,phone_canonical,phone_owner_type,date_of_birth,gender) "
                + "values (?,?,'Lifecycle fixture','+84977776666','PATIENT','1990-01-01','OTHER')", patient, patientAccount);
        jdbc.update("insert into staff_profiles (is_doctor,id,account_id,display_name,work_contact) values (true,?,?,'Lifecycle doctor',?)",
                doctorProfile, doctorAccount, "lifecycle-" + doctorProfile);
        jdbc.update("insert into visits (id,patient_profile_id,source,arrived_at,checked_in_at,created_by_account_id,status) "
                + "values (?,?,'WALK_IN',now(),now(),?,'OPEN')", visit, patient, patientAccount);
        jdbc.update("insert into visit_assignments (id,visit_id,doctor_profile_id,starts_at,ends_at,status) "
                + "values (?,?,?,now(),now()+interval '30 minutes','IN_PROGRESS')", UUID.randomUUID(), visit, doctorProfile);
        jdbc.update("insert into clinical_examinations (id,visit_id,reason_for_visit,symptoms,examination_findings,status,created_at,updated_at) "
                + "values (?,?, 'Imaging follow-up','Knee pain','Stable','COMPLETED',now(),now())", UUID.randomUUID(), visit);
        UUID round = UUID.randomUUID();
        jdbc.update("insert into clinical_ordering_rounds (id,visit_id,round_number,ordered_by_doctor_id,ordered_at,created_at) "
                + "values (?,?,1,?,now(),now())", round, visit, doctorProfile);
        jdbc.update("insert into clinical_service_orders (id,order_code,ordering_round_id,"
                + "visit_id,patient_id,service_id,service_name_snapshot,service_type,requesting_doctor_id,"
                + "clinical_reason,body_area,price_snapshot,order_status,payment_status,created_at,updated_at) "
                + "values (?,?,?,?,?,?,'Lifecycle MRI','IMAGING',?,'Test','KNEE',1,'ORDERED','PAID',now(),now())",
                order, order.toString().replace("-", ""), round, visit, patient, serviceId, doctorProfile);
    }

    private void account(UUID id, String role) {
        jdbc.update("insert into accounts (id,normalized_email,account_type,status,created_at,updated_at) values (?,?,?,'ACTIVE',now(),now())",
                id, "lifecycle-" + id + "@example.test", role);
    }

    @Test void paidOrderFlowsThroughLinkedStudyAndReleasedReport() {
        assertEquals(1, executions.assignImagingRoom(order, visit, patient, serviceId, room, "MRI", doctorAccount));
        // P03 has no acquisition-start endpoint. Seed trusted first-use as an explicit fixture.
        assertEquals(1, jdbc.update("update imaging_order_executions set execution_status='IN_PROGRESS', attempt_id=?, "
                + "first_use_event_id=?, first_use_type='ACQUISITION_STARTED', first_use_source_id='fixture', "
                + "first_use_at=now(), technician_account_id=?, version=1, updated_at=now() where service_order_id=?",
                attempt, UUID.randomUUID(), technician, order));
        ImagingAssignedWork work = new ImagingAssignedWork(technician, order, attempt, visit, patient, "fixture", true);
        UUID operation = uploads.register(technician, order, attempt, "upload-key", "a".repeat(64), 15, work).operation().id();
        String study = "img_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("update imaging_studies set state='ARCHIVED_NOT_SENT', study_ref=?, archive_study_ref=?, "
                + "study_instance_uid=?, modality='MR', series_count=1, instance_count=1, verification_status='CONFIRMED_MATCH', "
                + "verification_evidence='[\"PATIENT_ID_MATCH\"]'::jsonb, "
                + "dicom_index='{" + "\"series\":[{\"uid\":\"1.2.3\",\"orthanc_ref\":\"series\",\"instances\":[{\"sop_uid\":\"1.2.4\",\"orthanc_ref\":\"instance\",\"frames\":1}]}]}'::jsonb where id=?",
                study, "archive-" + operation, "1.2.840." + Math.abs((long) operation.hashCode()), operation);
        entityManager.clear();
        sends.send(technician, operation, "send-key");
        confirmation.dispatch(operation);
        assertEquals("LINKED", jdbc.queryForObject("select state from imaging_studies where id=?", String.class, operation));
        assertEquals("RESULTS_AVAILABLE", jdbc.queryForObject("select order_status from clinical_service_orders where id=?", String.class, order));
        assertNotNull(jdbc.queryForObject("select imaging_result_ready_at from visits where id=?", java.sql.Timestamp.class, visit));
        assertEquals(1, jdbc.queryForObject("select jsonb_array_length(dicom_index->'series') from imaging_studies where id=?", Integer.class, operation));

        var request = findings.review(DiagnosticActor.user(doctorAccount, AccountRole.DOCTOR), order,
                "review-key", 2, study, "lifecycle-review");
        long version = request.version();
        var draft = findings.saveDraft(DiagnosticActor.user(doctorAccount, AccountRole.DOCTOR), order,
                "draft-key", version, "No acute findings", "lifecycle-draft");
        byte[] pdf = "%PDF-1.7 sample".getBytes(StandardCharsets.US_ASCII);
        var report = findings.uploadReport(DiagnosticActor.user(doctorAccount, AccountRole.DOCTOR), order,
                "report-key", version, new DiagnosticReportUpload(() -> new ByteArrayInputStream(pdf), pdf.length, "application/pdf"), "lifecycle-report");
        var released = findings.complete(DiagnosticActor.user(doctorAccount, AccountRole.DOCTOR), order,
                "complete-key", version, draft.version(), report.reportId(), study, "lifecycle-release");
        assertEquals("RELEASED", released.releaseState());
        assertNotNull(jdbc.queryForObject("select findings_released_at from visits where id=?", java.sql.Timestamp.class, visit));
        assertNotNull(jdbc.queryForObject("select findings_released_at from imaging_study_releases where imaging_study_id=?", java.sql.Timestamp.class, operation));
        assertEquals(report.reportId(), jdbc.queryForObject("select released_report_id from imaging_study_releases where imaging_study_id=?", UUID.class, operation));
        assertEquals(draft.findingsVersionId(), jdbc.queryForObject("select released_findings_id from imaging_study_releases where imaging_study_id=?", UUID.class, operation));
        assertTrue(clinicalCompletion.getCompletionEvidence(visit).allNonCancelledOrdersHaveResults());
        assertTrue(clinicalCompletion.getCompletionEvidence(visit).examinationCompleted());
        assertTrue(diagnosticCompletion.getCompletionEvidence(visit, patient, List.of(order)).allComplete());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ActualImagingConfiguration {
        @Bean @Primary ImagingStudyPort lifecycleImagingStudyPort(ImagingStudyRepository studies,
                ImagingAuditPersistenceService audit, DiagnosticViewerAuthorizationService viewer,
                ImagingReconciliationService reconciliation) {
            return new ImagingStudyContractAdapter(studies, audit, viewer, reconciliation);
        }
    }
}
