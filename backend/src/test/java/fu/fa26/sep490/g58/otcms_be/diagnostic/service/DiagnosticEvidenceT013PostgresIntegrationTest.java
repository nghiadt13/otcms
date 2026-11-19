package fu.fa26.sep490.g58.otcms_be.diagnostic.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ImagingService;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.RoomEvidence;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.StudyEvidence;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.StudyVerificationStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.UseEvidence;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.UseEvidenceType;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ClinicalImagingOrder;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.VisitEvidence;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticRequestView;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.dto.DiagnosticDtos.CancellationCause;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticRequestStatus;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@LocalPostgresTest
@Import(DiagnosticRequestT012PostgresIntegrationTest.PortConfiguration.class)
class DiagnosticEvidenceT013PostgresIntegrationTest {
    private UUID visitId;
    private UUID patientId;
    private UUID doctorId;
    private UUID serviceId;
    private UUID roomId;

    @Autowired DiagnosticRequestService requests;
    @Autowired DiagnosticImagingRequestRegistration registration;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired DiagnosticEvidenceService evidenceService;
    @Autowired JdbcTemplate jdbc;
    @Autowired DiagnosticRequestT012PostgresIntegrationTest.PortFakes ports;

    @BeforeEach
    void setUp() {
        cleanAbandonedT013Rows();
        ports.reset();
        visitId = UUID.randomUUID();
        patientId = UUID.randomUUID();
        doctorId = UUID.randomUUID();
        serviceId = UUID.randomUUID();
        roomId = UUID.randomUUID();
        UUID technicianId = UUID.randomUUID();
        ports.visits.put(visitId, new VisitEvidence(visitId, patientId, doctorId, true, true, true));
        ports.service = new ImagingService(serviceId, "MRI", true);
        ports.addRoom(roomId, "MRI", technicianId, 3);
    }

    @AfterEach
    void cleanRows() {
        jdbc.update("delete from clinical_service_orders where visit_id = ?", visitId);
        jdbc.update("delete from clinical_ordering_rounds where visit_id = ?", visitId);
    }

    @Test
    void firstUseWritesExecutionOnceAndReplays() {
        DiagnosticRequestView created = createRequest();
        UseEvidence use = useEvidence(created.requestId());
        DiagnosticRequestView first = evidenceService.recordFirstUse(
                DiagnosticActor.trustedIntegration(), use, "corr-first-use");
        assertEquals(DiagnosticRequestStatus.IN_PROGRESS, first.status());
        assertEquals(1, first.version());
        assertEquals(use.attemptId(), UUID.fromString(jdbc.queryForObject(
                "select attempt_id from imaging_order_executions where service_order_id = ?", String.class, created.requestId())));

        DiagnosticRequestView replay = evidenceService.recordFirstUse(
                DiagnosticActor.trustedIntegration(), use, "corr-first-use-replay");
        assertEquals(first, replay);
        assertEquals(1, jdbc.queryForObject("select count(*) from imaging_order_executions "
                + "where service_order_id = ? and first_use_event_id = ?", Integer.class, created.requestId(), use.eventId()));
    }

    @Test
    void unverifiedFirstUseDoesNotAdvanceExecution() {
        DiagnosticRequestView created = createRequest();
        UseEvidence use = useEvidence(created.requestId());
        ports.firstUseAccepted = false;

        DiagnosticOperationException rejected = assertThrows(DiagnosticOperationException.class,
                () -> evidenceService.recordFirstUse(DiagnosticActor.trustedIntegration(), use, "corr-rejected-use"));

        assertEquals(DiagnosticOperationOutcome.EVIDENCE_MISMATCH, rejected.outcome());
        assertEquals(DiagnosticRequestStatus.PENDING, requests.get(doctor(), created.requestId()).status());
        assertEquals(0L, jdbc.queryForObject("select version from imaging_order_executions where service_order_id = ?",
                Long.class, created.requestId()));
        assertNull(jdbc.queryForObject("select attempt_id from imaging_order_executions where service_order_id = ?",
                String.class, created.requestId()));
    }

    @Test
    void laterVerifiedUseEvidenceIsAuditedWithoutAnotherAttempt() {
        DiagnosticRequestView created = createRequest();
        DiagnosticRequestView started = evidenceService.recordFirstUse(DiagnosticActor.trustedIntegration(),
                useEvidence(created.requestId()), "corr-initial-use");
        UUID attemptId = UUID.fromString(jdbc.queryForObject(
                "select attempt_id from imaging_order_executions where service_order_id = ?", String.class, created.requestId()));
        UseEvidence laterUse = new UseEvidence(UUID.randomUUID(), created.requestId(), attemptId,
                UseEvidenceType.CONTRAST_ADMINISTERED, "trusted-imaging-source", null,
                Instant.now().minusSeconds(1), Instant.now(), true);

        DiagnosticRequestView afterLaterUse = evidenceService.recordFirstUse(
                DiagnosticActor.trustedIntegration(), laterUse, "corr-later-use");

        assertEquals(DiagnosticRequestStatus.IN_PROGRESS, afterLaterUse.status());
        assertEquals(started, afterLaterUse);
        assertEquals(1L, jdbc.queryForObject("select version from imaging_order_executions where service_order_id = ?",
                Long.class, created.requestId()));
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_logs "
                + "where source = 'diagnostic_audit_events' and entity_id = ? "
                + "and action = 'RECORD_SUBSEQUENT_USE_EVIDENCE:ALLOWED'", Integer.class, created.requestId()));
    }

    @Test
    void verifiedUseEvidenceArrivingAfterCancellationIsAuditedWithoutCharging() {
        DiagnosticRequestView created = createRequest();
        requests.cancel(doctor(), created.requestId(), "cancel-before-use", 0,
                CancellationCause.PATIENT_DECLINED, "Patient declined", "corr-cancel-before-use");
        UseEvidence lateEvidence = useEvidence(created.requestId());

        DiagnosticRequestView cancelled = evidenceService.recordFirstUse(DiagnosticActor.trustedIntegration(),
                lateEvidence, "corr-late-use");

        assertEquals(DiagnosticRequestStatus.CANCELLED, cancelled.status());
        assertEquals(1L, jdbc.queryForObject("select version from imaging_order_executions where service_order_id = ?",
                Long.class, created.requestId()));
        assertEquals("CANCELLED", jdbc.queryForObject("select execution_status from imaging_order_executions where service_order_id = ?",
                String.class, created.requestId()));
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_logs "
                + "where source = 'diagnostic_audit_events' and entity_id = ? "
                + "and action = 'LATE_FIRST_USE_RECONCILIATION:STATE_CONFLICT'", Integer.class, created.requestId()));
    }

    @Test
    void mismatchedStudyNeverLinksAndConfirmedReplayQueuesVisitOnlyOnce() {
        DiagnosticRequestView created = createRequest();
        evidenceService.recordFirstUse(DiagnosticActor.trustedIntegration(), useEvidence(created.requestId()),
                "corr-first-use-study");
        UUID studyEventId = UUID.randomUUID();
        UUID attemptId = UUID.fromString(jdbc.queryForObject(
                "select attempt_id from imaging_order_executions where service_order_id = ?", String.class, created.requestId()));
        StudyEvidence incoming = new StudyEvidence(studyEventId, "study:opaque:123", created.requestId(),
                visitId, patientId, attemptId, StudyVerificationStatus.CONFIRMED_MATCH, true);
        ports.studyConfirmation = new StudyEvidence(studyEventId, incoming.studyRef(), created.requestId(),
                visitId, UUID.randomUUID(), attemptId, StudyVerificationStatus.CONFIRMED_MATCH, true);

        DiagnosticOperationException rejected = assertThrows(DiagnosticOperationException.class,
                () -> evidenceService.linkConfirmedStudy(DiagnosticActor.trustedIntegration(), incoming,
                        "corr-mismatched-study"));
        assertEquals(DiagnosticOperationOutcome.EVIDENCE_MISMATCH, rejected.outcome());
        assertEquals(DiagnosticRequestStatus.IN_PROGRESS, requests.get(doctor(), created.requestId()).status());
        assertEquals(1L, jdbc.queryForObject("select version from imaging_order_executions where service_order_id = ?",
                Long.class, created.requestId()));

        ports.studyConfirmation = null;
        DiagnosticRequestView linked = evidenceService.linkConfirmedStudy(DiagnosticActor.trustedIntegration(),
                incoming, "corr-confirmed-study");
        DiagnosticRequestView replay = evidenceService.linkConfirmedStudy(DiagnosticActor.trustedIntegration(),
                incoming, "corr-confirmed-study-replay");
        assertEquals(DiagnosticRequestStatus.RESULT_AVAILABLE, linked.status());
        assertEquals(incoming.studyRef(), linked.currentStudyRef());
        assertEquals(linked, replay);
        assertEquals(1, ports.resultReadyCalls);
    }

    @Test
    void linkingAConfirmedStudyMarksTheLinkedClinicalOrderOnce() {
        UUID orderId = UUID.randomUUID();
        ports.orders.put(orderId, new ClinicalImagingOrder(orderId, visitId, serviceId, true, true));
        DiagnosticRequestView created = DiagnosticRequestT012PostgresIntegrationTest.registerAssigned(jdbc, transactions, registration, ports,
                visitId, roomId, orderId, "Chest", "Pain");
        evidenceService.recordFirstUse(DiagnosticActor.trustedIntegration(), useEvidence(created.requestId()),
                "corr-first-use-linked");
        UUID attemptId = UUID.fromString(jdbc.queryForObject(
                "select attempt_id from imaging_order_executions where service_order_id = ?", String.class, created.requestId()));
        StudyEvidence incoming = new StudyEvidence(UUID.randomUUID(), "study:opaque:linked", created.requestId(),
                visitId, patientId, attemptId, StudyVerificationStatus.CONFIRMED_MATCH, true);

        assertEquals(List.of(), ports.orderResultCalls);
        evidenceService.linkConfirmedStudy(DiagnosticActor.trustedIntegration(), incoming, "corr-linked-study");
        evidenceService.linkConfirmedStudy(DiagnosticActor.trustedIntegration(), incoming, "corr-linked-replay");

        assertEquals(List.of(orderId), ports.orderResultCalls);
    }

    @Test
    void invalidStudyEvidenceAndWrongActorAreRejected() {
        DiagnosticRequestView created = createRequest();
        DiagnosticOperationException wrongActor = assertThrows(DiagnosticOperationException.class,
                () -> evidenceService.recordFirstUse(DiagnosticActor.system(), useEvidence(created.requestId()),
                        "corr-wrong-actor"));
        assertEquals(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD, wrongActor.outcome());

        UseEvidence use = useEvidence(created.requestId());
        evidenceService.recordFirstUse(DiagnosticActor.trustedIntegration(), use, "corr-first-use-pending");
        UUID attemptId = UUID.fromString(jdbc.queryForObject(
                "select attempt_id from imaging_order_executions where service_order_id = ?", String.class, created.requestId()));
        StudyEvidence unverified = new StudyEvidence(UUID.randomUUID(), "study:pending", created.requestId(), visitId,
                patientId, attemptId, StudyVerificationStatus.PENDING, false);
        DiagnosticOperationException rejected = assertThrows(DiagnosticOperationException.class,
                () -> evidenceService.linkConfirmedStudy(DiagnosticActor.trustedIntegration(), unverified,
                        "corr-unverified-study"));

        assertEquals(DiagnosticOperationOutcome.EVIDENCE_MISMATCH, rejected.outcome());
        assertEquals(DiagnosticRequestStatus.IN_PROGRESS, requests.get(doctor(), created.requestId()).status());
    }

    private DiagnosticRequestView createRequest() {
        UUID orderId = UUID.randomUUID();
        ports.orders.put(orderId, new fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ClinicalImagingOrder(orderId, visitId, serviceId, true, true));
        return DiagnosticRequestT012PostgresIntegrationTest.registerAssigned(jdbc, transactions, registration, ports,
                visitId, roomId, orderId, "Chest", "Pain");
    }

    private void cleanAbandonedT013Rows() {
        // The execution and evidence live on clinical_service_orders; each test also cleans its visit in @AfterEach.
    }

    private UseEvidence useEvidence(UUID requestId) {
        return new UseEvidence(UUID.randomUUID(), requestId, UUID.randomUUID(), UseEvidenceType.ACQUISITION_STARTED,
                "trusted-imaging-source", null, Instant.now().minusSeconds(3), Instant.now(), true);
    }

    private DiagnosticActor doctor() { return DiagnosticActor.user(doctorId, AccountRole.DOCTOR); }
}
