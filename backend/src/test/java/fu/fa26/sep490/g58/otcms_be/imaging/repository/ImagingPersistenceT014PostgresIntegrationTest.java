package fu.fa26.sep490.g58.otcms_be.imaging.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingIdempotencyRecord;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingOperationRecord;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingReconciliationDecision;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingReconciliationDecisionCommand;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingAuditEventJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingConfirmationOutboxJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingReleaseInboxJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingReconciliationCaseJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingStudyInstanceJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingStudyJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingStudySeriesJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingUidClaimId;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingUidClaimJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingViewerGrantJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationState;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationType;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingReconciliationClassification;
import fu.fa26.sep490.g58.otcms_be.imaging.service.impl.ImagingIdempotencyPersistenceService;
import fu.fa26.sep490.g58.otcms_be.imaging.service.impl.ImagingIngestOperationPersistenceService;
import fu.fa26.sep490.g58.otcms_be.imaging.service.ImagingReconciliationService;
import fu.fa26.sep490.g58.otcms_be.imaging.service.ImagingReconciliationQuery;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@LocalPostgresTest
class ImagingPersistenceT014PostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-05T00:00:00Z");

    @Autowired JdbcTemplate jdbc;
    @Autowired ImagingIngestOperationPersistenceService operations;
    @Autowired ImagingIdempotencyPersistenceService idempotency;
    @Autowired ImagingStudyRepository studies;
    @Autowired ImagingStudySeriesRepository series;
    @Autowired ImagingStudyInstanceRepository instances;
    @Autowired ImagingUidClaimRepository uidClaims;
    @Autowired ImagingConfirmationOutboxRepository confirmationOutbox;
    @Autowired ImagingReleaseInboxRepository releaseInbox;
    @Autowired ImagingViewerGrantRepository viewerGrants;
    @Autowired ImagingAuditEventRepository auditEvents;
    @Autowired ImagingReconciliationCaseRepository reconciliationCases;
    @Autowired ImagingReconciliationService reconciliation;
    private UUID operationId;
    private UUID releaseEventId;

    @AfterEach
    void cleanup() {
        if (releaseEventId != null) {
            jdbc.update("delete from imaging_audit_events where correlation_id = ?", releaseEventId.toString());
            jdbc.update("delete from imaging_release_inbox where event_id = ?", releaseEventId);
            jdbc.update("delete from imaging_reconciliation_cases where event_id = ?", releaseEventId);
            releaseEventId = null;
        }
        if (operationId != null) {
            jdbc.update("delete from imaging_audit_events where operation_id = ?", operationId);
            jdbc.update("delete from imaging_confirmation_outbox where operation_id = ?", operationId);
            jdbc.update("delete from imaging_reconciliation_cases where operation_id = ?", operationId);
            jdbc.update("delete from imaging_viewer_grants where study_ref in "
                    + "(select study_ref from imaging_studies where ingest_operation_id = ?)", operationId);
            jdbc.update("delete from imaging_uid_claims where study_ref in "
                    + "(select study_ref from imaging_studies where ingest_operation_id = ?)", operationId);
            jdbc.update("delete from imaging_study_instances where series_id in (select id from imaging_study_series "
                    + "where study_ref in (select study_ref from imaging_studies where ingest_operation_id = ?))", operationId);
            jdbc.update("delete from imaging_study_series where study_ref in "
                    + "(select study_ref from imaging_studies where ingest_operation_id = ?)", operationId);
            jdbc.update("delete from imaging_release_inbox where study_ref in "
                    + "(select study_ref from imaging_studies where ingest_operation_id = ?)", operationId);
            jdbc.update("delete from imaging_studies where ingest_operation_id = ?", operationId);
            jdbc.update("delete from imaging_idempotency_records where target_operation_id = ?", operationId);
            jdbc.update("delete from imaging_ingest_operations where id = ?", operationId);
        }
    }

    @Test
    void persistsStudyArchiveAuditAndRecoveryProjectionsThroughJpaRepositories() {
        ImagingOperationRecord operation = operation(ImagingOperationState.ARCHIVED_NOT_SENT, 0);
        operationId = operation.id();
        operations.insert(operation);
        String studyRef = "study-" + UUID.randomUUID();
        ImagingStudyJpaEntity study = new ImagingStudyJpaEntity(studyRef, operation.id(), operation.requestId(),
                operation.attemptId(), operation.visitId(), operation.patientId(), "orthanc-study-ref",
                "1.2.826.0.1.3680043.10.100.1", "CT", 1, 1, "CONFIRMED_MATCH");
        studies.saveAndFlush(study);
        assertEquals(studyRef, studies.findByIngestOperationId(operation.id()).orElseThrow().getStudyRef());

        UUID seriesId = UUID.randomUUID();
        series.saveAndFlush(new ImagingStudySeriesJpaEntity(seriesId, studyRef,
                "1.2.826.0.1.3680043.10.100.2", "orthanc-series-ref", 1));
        UUID instanceId = UUID.randomUUID();
        instances.saveAndFlush(new ImagingStudyInstanceJpaEntity(instanceId, seriesId,
                "1.2.826.0.1.3680043.10.100.3", "orthanc-instance-ref", 1));
        assertEquals(instanceId, instances.findBySeriesIdAndSopInstanceUid(seriesId,
                "1.2.826.0.1.3680043.10.100.3").orElseThrow().getId());

        ImagingUidClaimId uid = new ImagingUidClaimId("STUDY", study.getStudyInstanceUid());
        uidClaims.saveAndFlush(new ImagingUidClaimJpaEntity(uid, studyRef));
        assertEquals(studyRef, uidClaims.findById(uid).orElseThrow().getStudyRef());

        UUID eventId = UUID.randomUUID();
        confirmationOutbox.saveAndFlush(new ImagingConfirmationOutboxJpaEntity(UUID.randomUUID(), eventId,
                operation.id(), studyRef, operation.requestId(), operation.attemptId(), operation.visitId(),
                operation.patientId(), "CONFIRMED_MATCH", Map.of("patient", "MATCH"), "b".repeat(64),
                "PENDING", 0, NOW, null, null, null));
        assertEquals(eventId, confirmationOutbox.findByOperationId(operation.id()).orElseThrow().getEventId());

        UUID releaseEventId = UUID.randomUUID();
        releaseInbox.saveAndFlush(new ImagingReleaseInboxJpaEntity(UUID.randomUUID(), releaseEventId,
                operation.requestId(), operation.visitId(), operation.patientId(), studyRef, UUID.randomUUID(),
                UUID.randomUUID(), "c".repeat(64), "d".repeat(64), "ACCEPTED", null));
        assertEquals(releaseEventId, releaseInbox.findByEventId(releaseEventId).orElseThrow().getEventId());

        UUID grantId = UUID.randomUUID();
        viewerGrants.saveAndFlush(new ImagingViewerGrantJpaEntity(grantId, "e".repeat(64),
                operation.actorAccountId(), "DOCTOR_REVIEW", studyRef, "review-v1", NOW,
                NOW.plusSeconds(300), null));
        assertEquals(grantId, viewerGrants.findByTokenSha256("e".repeat(64)).orElseThrow().getId());

        UUID auditId = UUID.randomUUID();
        auditEvents.saveAndFlush(new ImagingAuditEventJpaEntity(auditId, operation.actorAccountId(), null,
                "UPLOAD", "ACCEPTED", operation.id(), studyRef, operation.requestId(), operation.attemptId(),
                operation.visitId(), operation.patientId(), "corr-" + UUID.randomUUID(), Map.of(), NOW));
        assertEquals(auditId, auditEvents.findAllByStudyRefOrderByRecordedAtDescIdDesc(studyRef).getFirst().getId());

        reconciliationCases.saveAndFlush(new ImagingReconciliationCaseJpaEntity(UUID.randomUUID(), operation.id(),
                null, "ARCHIVE_PARTIAL_FAILURE", "ORPHANED", "OPEN", "ARCHIVE_UNCERTAIN", "IMAGING_OPS", NOW,
                null, null));
        reconciliation.openOperation(operation.id(), "CONFIRMATION",
                ImagingReconciliationClassification.RETRYABLE, "CONFIRMATION_UNAVAILABLE");
        reconciliation.openOperation(operation.id(), "CONFIRMATION",
                ImagingReconciliationClassification.RETRYABLE, "CONFIRMATION_UNAVAILABLE");
        var cases = reconciliationCases.findAllByOperationIdAndStateNot(operation.id(), "RESOLVED");
        assertEquals(2, cases.size());
        assertTrue(cases.stream().anyMatch(item -> item.getCategory().equals("CONFIRMATION")
                && item.getClassification().equals("RETRYABLE")));
    }

    @Test
    void adminDecisionClosesOnlyTheQueueCaseAndPersistsActorAudit() {
        ImagingOperationRecord operation = operation(ImagingOperationState.QUARANTINED, 0);
        operationId = operation.id();
        operations.insert(operation);
        reconciliation.openOperation(operation.id(), "INGEST",
                ImagingReconciliationClassification.CONFLICT, "IDENTITY_MISMATCH");

        UUID queueReaderId = UUID.randomUUID();
        var queue = reconciliation.listQueue(queueReaderId, new ImagingReconciliationQuery(
                "OPEN", "CONFLICT", "INGEST", 0, 25), "corr-reconciliation-list");
        assertEquals(1, queue.getTotalElements());
        var item = queue.getContent().getFirst();
        UUID adminId = UUID.randomUUID();
        reconciliation.recordDecision(adminId, item.caseId(), new ImagingReconciliationDecisionCommand(
                ImagingReconciliationDecision.KEEP_QUARANTINED, "INC-42"), "corr-reconciliation-decision");
        reconciliation.recordDecision(adminId, item.caseId(), new ImagingReconciliationDecisionCommand(
                ImagingReconciliationDecision.KEEP_QUARANTINED, "INC-42"), "corr-reconciliation-decision");

        var resolved = reconciliationCases.findById(item.caseId()).orElseThrow();
        assertEquals("RESOLVED", resolved.getState());
        assertEquals("INC-42", resolved.getResolutionRef());
        assertEquals("KEEP_QUARANTINED", resolved.getResolutionDecision());
        assertEquals(ImagingOperationState.QUARANTINED, operations.findById(operation.id()).orElseThrow().state());
        assertEquals(1, jdbc.queryForObject("""
                select count(*) from imaging_audit_events
                 where operation_id = ? and actor_account_id = ?
                   and action = 'IMAGING_RECONCILIATION_DECISION'
                   and correlation_id = 'corr-reconciliation-decision'
                   and safe_metadata ->> 'decisionCode' = 'KEEP_QUARANTINED'
                   and safe_metadata ->> 'caseId' = ?
                """, Integer.class, operation.id(), adminId, item.caseId().toString()));
        assertEquals(1, jdbc.queryForObject("""
                select count(*) from imaging_audit_events
                 where actor_account_id = ? and action = 'IMAGING_RECONCILIATION_QUEUE_READ'
                   and correlation_id = 'corr-reconciliation-list'
                """, Integer.class, queueReaderId));
    }

    @Test
    @Transactional
    void reconciliationWorkerClosesRetryableReleaseOnlyAfterMatchingDurableAcceptance() {
        releaseEventId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID visitId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID findingsVersionId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        String payloadHash = "d".repeat(64);
        reconciliation.openEvent(releaseEventId, "RELEASE", ImagingReconciliationClassification.RETRYABLE,
                payloadHash, "RELEASE_BEFORE_LINKED");
        assertEquals(1, releaseInbox.insertAcceptedIfAbsent(UUID.randomUUID(), releaseEventId, requestId, visitId,
                patientId, "opaque-study", findingsVersionId, reportId, "e".repeat(64), payloadHash, NOW));

        assertNotNull(reconciliation.reconcileOpenBatch(new UUID(0L, 0L)));

        assertEquals(1, jdbc.queryForObject("""
                select count(*) from imaging_reconciliation_cases
                 where event_id = ? and state = 'RESOLVED' and classification = 'LINKED'
                   and resolution_decision is null
                """, Integer.class, releaseEventId));
        assertEquals(1, jdbc.queryForObject("""
                select count(*) from imaging_audit_events
                 where action = 'IMAGING_RECONCILIATION_AUTO_CLOSE'
                   and correlation_id = ? and safe_metadata ->> 'eventId' = ?
                """, Integer.class, releaseEventId.toString(), releaseEventId.toString()));
    }

    @Test
    void storesSafeOperationProjectionAndUsesOptimisticStateTransition() {
        ImagingOperationRecord operation = operation(ImagingOperationState.PROCESSING, 0);
        operationId = operation.id();
        operations.insert(operation);

        var persisted = operations.findById(operationId).orElseThrow();
        assertEquals(operationId, persisted.id());
        assertEquals(operation.requestId(), persisted.requestId());
        assertEquals(operation.attemptId(), persisted.attemptId());
        assertEquals(ImagingOperationState.PROCESSING, persisted.state());
        assertEquals(operation.payloadSha256(), persisted.payloadSha256());

        operations.transition(operationId, 0, ImagingOperationState.ARCHIVED_NOT_SENT, null, NOW.plusSeconds(1));
        var transitioned = operations.findById(operationId).orElseThrow();
        assertEquals(ImagingOperationState.ARCHIVED_NOT_SENT, transitioned.state());
        assertEquals(1, transitioned.version());
        assertThrows(ImagingOperationException.class, () -> operations.transition(operationId, 0,
                ImagingOperationState.CONFIRMATION_PENDING, null, NOW.plusSeconds(2)));
        ImagingOperationException stateConflict = assertThrows(ImagingOperationException.class,
                () -> operations.transition(operationId, 1, ImagingOperationState.PROCESSING,
                        null, NOW.plusSeconds(2)));
        assertEquals(fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome.STATE_CONFLICT,
                stateConflict.outcome());
    }

    @Test
    void persistsIdempotencyUniquenessAndKeepsIdentityTagsOutOfSchema() {
        ImagingOperationRecord operation = operation(ImagingOperationState.PROCESSING, 0);
        operationId = operation.id();
        operations.insert(operation);
        UUID keyId = UUID.randomUUID();
        String key = "t014-" + UUID.randomUUID();
        ImagingIdempotencyRecord record = new ImagingIdempotencyRecord(keyId, operation.actorAccountId(),
                operation.requestId(), operation.attemptId(), ImagingOperationType.UPLOAD, key,
                operation.payloadSha256(), operation.id(), null, NOW);
        idempotency.insert(record);
        assertEquals(record, idempotency.find(operation.actorAccountId(), operation.requestId(),
                operation.attemptId(), ImagingOperationType.UPLOAD, key).orElseThrow());
        assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update("""
                insert into imaging_idempotency_records
                    (id, actor_account_id, request_id, attempt_id, operation_type, idempotency_key,
                     payload_sha256, target_operation_id)
                values (?, ?, ?, ?, 'UPLOAD', ?, ?, ?)
                """, UUID.randomUUID(), operation.actorAccountId(), operation.requestId(), operation.attemptId(), key,
                operation.payloadSha256(), operation.id()));
        assertTrue(operations.findById(operation.id()).isPresent());
        assertTrue(jdbc.queryForList("""
                select column_name from information_schema.columns
                 where table_schema = current_schema()
                   and table_name in ('imaging_ingest_operations', 'imaging_studies')
                   and column_name in ('patient_name', 'patient_id_tag', 'accession_number', 'issuer_value')
                """).isEmpty());
    }

    private ImagingOperationRecord operation(ImagingOperationState state, long version) {
        return new ImagingOperationRecord(UUID.randomUUID(), null, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), ImagingOperationType.UPLOAD,
                state, "a".repeat(64), 1234, null, null, 0, 0,
                fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingVerificationStatus.NOT_EVALUATED,
                java.util.List.of(), null, null, version, NOW, NOW);
    }
}
