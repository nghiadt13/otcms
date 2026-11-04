package fu.fa26.sep490.g58.otcms_be.diagnostic.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ImagingService;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.RoomEvidence;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.StudyEvidence;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.StudyVerificationStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.UseEvidence;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.UseEvidenceType;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.UseEvidenceType;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.VisitEvidence;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticRequestView;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.dto.DiagnosticDtos.CancellationCause;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticRequestStatus;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
        jdbc.update("delete from diagnostic_audit_events where visit_id = ?", visitId);
        jdbc.update("delete from diagnostic_outbox where visit_id = ?", visitId);
        jdbc.update("delete from diagnostic_idempotency_records where visit_id = ?", visitId);
        jdbc.update("update diagnostic_requests set status = 'CANCELLED', cancellation_cause = 'PATIENT_DECLINED', "
                + "cancellation_reason = 'Test cleanup', current_attempt_id = null where visit_id = ?", visitId);
        jdbc.update("delete from diagnostic_attempts where request_id in "
                + "(select id from diagnostic_requests where visit_id = ?)", visitId);
        jdbc.update("delete from diagnostic_requests where visit_id = ?", visitId);
    }

    @Test
    void firstUseCreatesOneAttemptAndBillingObligationAndRetriesDelivery() {
        DiagnosticRequestView created = createRequest();
        UseEvidence use = useEvidence(created.requestId());

        ports.billingFails = true;
        DiagnosticRequestView first = evidenceService.recordFirstUse(
                DiagnosticActor.trustedIntegration(), use, "corr-first-use");
        assertEquals(DiagnosticRequestStatus.IN_PROGRESS, first.status());
        assertEquals(1, first.version());
        assertEquals(1, jdbc.queryForObject("select count(*) from diagnostic_attempts where request_id = ?",
                Integer.class, created.requestId()));
        assertEquals(1, jdbc.queryForObject("select count(*) from diagnostic_outbox "
                + "where request_id = ? and event_type = 'FIRST_USE_BILLING_OBLIGATION'",
                Integer.class, created.requestId()));

        var retryPolicy = new DiagnosticEvidenceService.RetryPolicy(3, 30, 3600);
        var failedDelivery = evidenceService.dispatchDueOutbox(10, retryPolicy);
        assertEquals(1, failedDelivery.retryScheduled());
        assertEquals("PENDING", jdbc.queryForObject("select status from diagnostic_outbox where request_id = ?",
                String.class, created.requestId()));

        ports.billingFails = false;
        jdbc.update("update diagnostic_outbox set next_attempt_at = now() - interval '1 second' where request_id = ?",
                created.requestId());
        var successfulDelivery = evidenceService.dispatchDueOutbox(10, retryPolicy);
        assertEquals(1, successfulDelivery.delivered());

        DiagnosticRequestView replay = evidenceService.recordFirstUse(
                DiagnosticActor.trustedIntegration(), use, "corr-first-use-retry");
        assertEquals(first, replay);
        assertEquals(1, jdbc.queryForObject("select count(*) from diagnostic_attempts where first_use_event_id = ?",
                Integer.class, use.eventId()));
        assertEquals(1, ports.billedRequestIds.size());
    }

    @Test
    void unverifiedFirstUseDoesNotAdvanceRequestOrCreateBillingEvent() {
        DiagnosticRequestView created = createRequest();
        UseEvidence use = useEvidence(created.requestId());
        ports.firstUseAccepted = false;

        DiagnosticOperationException rejected = assertThrows(DiagnosticOperationException.class,
                () -> evidenceService.recordFirstUse(DiagnosticActor.trustedIntegration(), use, "corr-rejected-use"));

        assertEquals(DiagnosticOperationOutcome.EVIDENCE_MISMATCH, rejected.outcome());
        assertEquals(DiagnosticRequestStatus.PENDING, requests.get(doctor(), created.requestId()).status());
        assertEquals(0, jdbc.queryForObject("select count(*) from diagnostic_attempts where request_id = ?",
                Integer.class, created.requestId()));
        assertEquals(0, jdbc.queryForObject("select count(*) from diagnostic_outbox where request_id = ?",
                Integer.class, created.requestId()));
    }

    @Test
    void laterVerifiedUseEvidenceIsAuditedWithoutAnotherAttemptOrFee() {
        DiagnosticRequestView created = createRequest();
        DiagnosticRequestView started = evidenceService.recordFirstUse(DiagnosticActor.trustedIntegration(),
                useEvidence(created.requestId()), "corr-initial-use");
        UUID attemptId = UUID.fromString(jdbc.queryForObject(
                "select current_attempt_id from diagnostic_requests where id = ?", String.class, created.requestId()));
        UseEvidence laterUse = new UseEvidence(UUID.randomUUID(), created.requestId(), attemptId,
                UseEvidenceType.CONTRAST_ADMINISTERED, "trusted-imaging-source", null,
                Instant.now().minusSeconds(1), Instant.now(), true);

        DiagnosticRequestView afterLaterUse = evidenceService.recordFirstUse(
                DiagnosticActor.trustedIntegration(), laterUse, "corr-later-use");

        assertEquals(DiagnosticRequestStatus.IN_PROGRESS, afterLaterUse.status());
        assertEquals(started, afterLaterUse);
        assertEquals(1, jdbc.queryForObject("select count(*) from diagnostic_attempts where request_id = ?",
                Integer.class, created.requestId()));
        assertEquals(1, jdbc.queryForObject("select count(*) from diagnostic_outbox "
                + "where request_id = ? and event_type = 'FIRST_USE_BILLING_OBLIGATION'",
                Integer.class, created.requestId()));
        assertEquals(1, jdbc.queryForObject("select count(*) from diagnostic_audit_events "
                + "where event_id = ? and action = 'RECORD_SUBSEQUENT_USE_EVIDENCE'",
                Integer.class, laterUse.eventId()));
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
        assertEquals(0, jdbc.queryForObject("select count(*) from diagnostic_attempts where request_id = ?",
                Integer.class, created.requestId()));
        assertEquals(0, jdbc.queryForObject("select count(*) from diagnostic_outbox where request_id = ? "
                + "and event_type = 'FIRST_USE_BILLING_OBLIGATION'", Integer.class, created.requestId()));
        assertEquals("STATE_CONFLICT", jdbc.queryForObject("select outcome from diagnostic_audit_events "
                + "where event_id = ?", String.class, lateEvidence.eventId()));
    }

    @Test
    void mismatchedStudyNeverLinksAndConfirmedReplayQueuesVisitOnlyOnce() {
        DiagnosticRequestView created = createRequest();
        evidenceService.recordFirstUse(DiagnosticActor.trustedIntegration(), useEvidence(created.requestId()),
                "corr-first-use-study");
        UUID studyEventId = UUID.randomUUID();
        UUID attemptId = UUID.fromString(jdbc.queryForObject(
                "select current_attempt_id from diagnostic_requests where id = ?", String.class, created.requestId()));
        StudyEvidence incoming = new StudyEvidence(studyEventId, "study:opaque:123", created.requestId(),
                visitId, patientId, attemptId, StudyVerificationStatus.CONFIRMED_MATCH, true);
        ports.studyConfirmation = new StudyEvidence(studyEventId, incoming.studyRef(), created.requestId(),
                visitId, UUID.randomUUID(), attemptId, StudyVerificationStatus.CONFIRMED_MATCH, true);

        DiagnosticOperationException rejected = assertThrows(DiagnosticOperationException.class,
                () -> evidenceService.linkConfirmedStudy(DiagnosticActor.trustedIntegration(), incoming,
                        "corr-mismatched-study"));
        assertEquals(DiagnosticOperationOutcome.EVIDENCE_MISMATCH, rejected.outcome());
        assertEquals(DiagnosticRequestStatus.IN_PROGRESS, requests.get(doctor(), created.requestId()).status());
        assertEquals(0, jdbc.queryForObject("select count(*) from diagnostic_outbox "
                + "where request_id = ? and event_type = 'IMAGING_RESULT_READY'", Integer.class, created.requestId()));

        ports.studyConfirmation = null;
        DiagnosticRequestView linked = evidenceService.linkConfirmedStudy(DiagnosticActor.trustedIntegration(),
                incoming, "corr-confirmed-study");
        DiagnosticRequestView replay = evidenceService.linkConfirmedStudy(DiagnosticActor.trustedIntegration(),
                incoming, "corr-confirmed-study-replay");
        assertEquals(DiagnosticRequestStatus.RESULT_AVAILABLE, linked.status());
        assertEquals(incoming.studyRef(), linked.currentStudyRef());
        assertEquals(linked, replay);
        assertEquals(1, jdbc.queryForObject("select count(*) from diagnostic_outbox "
                + "where request_id = ? and event_type = 'IMAGING_RESULT_READY'", Integer.class, created.requestId()));
        var delivered = evidenceService.dispatchDueOutbox(10,
                new DiagnosticEvidenceService.RetryPolicy(3, 30, 3600));
        assertEquals(2, delivered.delivered());
        assertEquals(1, ports.resultReadyCalls);
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
                "select current_attempt_id from diagnostic_requests where id = ?", String.class, created.requestId()));
        StudyEvidence unverified = new StudyEvidence(UUID.randomUUID(), "study:pending", created.requestId(), visitId,
                patientId, attemptId, StudyVerificationStatus.PENDING, false);
        DiagnosticOperationException rejected = assertThrows(DiagnosticOperationException.class,
                () -> evidenceService.linkConfirmedStudy(DiagnosticActor.trustedIntegration(), unverified,
                        "corr-unverified-study"));

        assertEquals(DiagnosticOperationOutcome.EVIDENCE_MISMATCH, rejected.outcome());
        assertEquals(DiagnosticRequestStatus.IN_PROGRESS, requests.get(doctor(), created.requestId()).status());
    }

    private DiagnosticRequestView createRequest() {
        return requests.create(doctor(), visitId, "create-" + UUID.randomUUID(), serviceId, "MRI", "Chest",
                "Pain", roomId, "corr-create");
    }

    private void cleanAbandonedT013Rows() {
        String testRequests = "select request_id from diagnostic_attempts where source_id = 'trusted-imaging-source'";
        jdbc.update("delete from diagnostic_audit_events where request_id in (" + testRequests + ")");
        jdbc.update("delete from diagnostic_outbox where request_id in (" + testRequests + ")");
        jdbc.update("delete from diagnostic_idempotency_records where request_id in (" + testRequests + ")");
        jdbc.update("update diagnostic_requests set status = 'CANCELLED', cancellation_cause = 'PATIENT_DECLINED', "
                + "cancellation_reason = 'Test cleanup', current_attempt_id = null where id in ("
                + testRequests + ")");
        jdbc.update("delete from diagnostic_attempts where request_id in (" + testRequests + ")");
        jdbc.update("delete from diagnostic_requests where id in (" + testRequests + ")");
    }

    private UseEvidence useEvidence(UUID requestId) {
        return new UseEvidence(UUID.randomUUID(), requestId, UUID.randomUUID(), UseEvidenceType.ACQUISITION_STARTED,
                "trusted-imaging-source", null, Instant.now().minusSeconds(3), Instant.now(), true);
    }

    private DiagnosticActor doctor() { return DiagnosticActor.user(doctorId, AccountRole.DOCTOR); }
}
