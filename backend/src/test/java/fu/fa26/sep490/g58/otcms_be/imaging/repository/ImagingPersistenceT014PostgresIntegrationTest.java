package fu.fa26.sep490.g58.otcms_be.imaging.repository;

import static org.junit.jupiter.api.Assertions.*;

import fu.fa26.sep490.g58.otcms_be.common.idempotency.IdempotencyScope;
import fu.fa26.sep490.g58.otcms_be.common.idempotency.IdempotencyStore;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingOperationRecord;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingReconciliationDecision;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingReconciliationDecisionCommand;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingStudyJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingViewerGrantJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationState;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationType;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingReconciliationClassification;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingVerificationStatus;
import fu.fa26.sep490.g58.otcms_be.imaging.service.ImagingReconciliationQuery;
import fu.fa26.sep490.g58.otcms_be.imaging.service.ImagingReconciliationService;
import fu.fa26.sep490.g58.otcms_be.imaging.service.impl.ImagingAuditPersistenceService;
import fu.fa26.sep490.g58.otcms_be.imaging.service.impl.ImagingIngestOperationPersistenceService;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** P03 imaging storage, scoped to a rolled-back transaction so append-only audits remain untouched. */
@SpringBootTest
@LocalPostgresTest
@Transactional
class ImagingPersistenceT014PostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-05T00:00:00Z");
    private final UUID patientAccount = UUID.randomUUID(), patient = UUID.randomUUID();
    private final UUID doctor = UUID.randomUUID(), staff = UUID.randomUUID(), visit = UUID.randomUUID();
    private final UUID service = UUID.randomUUID(), order = UUID.randomUUID(), attempt = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();

    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    @Autowired ImagingIngestOperationPersistenceService operations;
    @Autowired ImagingStudyRepository studies;
    @Autowired IdempotencyStore idempotency;
    @Autowired ImagingViewerGrantRepository viewerGrants;
    @Autowired ImagingAuditPersistenceService audit;
    @Autowired ImagingReconciliationCaseRepository reconciliationCases;
    @Autowired ImagingReconciliationService reconciliation;

    @BeforeEach void seedOwners() {
        account(patientAccount, "PATIENT"); account(doctor, "DOCTOR"); account(actor, "IMAGING_TECHNICIAN");
        jdbc.update("insert into patient_profiles (id,account_id,full_name,phone_canonical,phone_owner_type,date_of_birth,gender) values (?,?,'Imaging T014',?,'PATIENT',date '1990-01-01','OTHER')",
                patient, patientAccount, "+849" + String.format("%09d", Math.floorMod(patient.getLeastSignificantBits(), 1_000_000_000L)));
        jdbc.update("insert into staff_profiles (is_doctor,id,account_id,display_name) values (true,?,?,'Imaging doctor')", staff, doctor);
        jdbc.update("insert into visits (id,patient_profile_id,source,arrived_at,checked_in_at,created_by_account_id,status) values (?,?,'WALK_IN',now(),now(),?,'OPEN')", visit, patient, doctor);
        UUID round = UUID.randomUUID();
        jdbc.update("insert into service_order_rounds (id,visit_id,round_number,ordered_by_doctor_id,ordered_at,created_at) values (?,?,1,?,now(),now())",
                round, visit, staff);
        jdbc.update("insert into service_orders (id,order_code,ordering_round_id,visit_id,patient_id,service_id,service_name_snapshot,service_type,requesting_doctor_id,clinical_reason,body_area,price_snapshot,order_status,payment_status,created_at,updated_at) values (?,?,?,?,?,?,'CT','IMAGING',?,'T014','CHEST',1,'ORDERED','PAID',now(),now())",
                order, order.toString().replace("-", ""), round, visit, patient, service, staff);
    }

    private void account(UUID id, String type) {
        jdbc.update("insert into accounts (id,normalized_email,account_type,status,created_at,updated_at) values (?,?,?,'ACTIVE',now(),now())", id, id + "@example.test", type);
    }

    @Test void persistsStudyArchiveAuditAndRecoveryProjectionsThroughJpaRepositories() {
        ImagingOperationRecord operation = operation(ImagingOperationState.ARCHIVED_NOT_SENT);
        operations.insert(operation);
        String studyRef = "study-" + UUID.randomUUID();
        var study = studies.findById(operation.id()).orElseThrow();
        var index = new ImagingStudyJpaEntity.DicomIndex(List.of(new ImagingStudyJpaEntity.DicomSeries(
                "1.2.826.0.1.3680043.10.100.2", "orthanc-series-ref", List.of(
                new ImagingStudyJpaEntity.DicomInstance("1.2.826.0.1.3680043.10.100.3", "orthanc-instance-ref", 1)))));
        study.persistConfirmedStudy(studyRef, "orthanc-study-ref", index, NOW);
        studies.saveAndFlush(study);
        assertEquals(studyRef, studies.findById(operation.id()).orElseThrow().getStudyRef());
        assertTrue(studies.containsDicomIndex(operation.id(), "{\"series\":[{\"uid\":\"1.2.826.0.1.3680043.10.100.2\",\"instances\":[{\"sop_uid\":\"1.2.826.0.1.3680043.10.100.3\"}]}]}"));
        UUID eventId = UUID.randomUUID();
        study.prepareConfirmation(eventId, NOW);
        studies.saveAndFlush(study);
        assertEquals(eventId, studies.findByConfirmationEventId(eventId).orElseThrow().getConfirmationEventId());
        study.markAcknowledged(NOW);
        UUID releaseEvent = UUID.randomUUID();
        study.acceptFindingsRelease(releaseEvent, "d".repeat(64), UUID.randomUUID(), UUID.randomUUID(), "c".repeat(64), NOW);
        studies.saveAndFlush(study);
        assertEquals(releaseEvent, studies.findByReleaseEventId(releaseEvent).orElseThrow().getReleaseEventId());
        UUID grant = UUID.randomUUID();
        viewerGrants.saveAndFlush(new ImagingViewerGrantJpaEntity(grant, "e".repeat(64), actor,
                "DOCTOR_REVIEW", studyRef, "review-v1", NOW, NOW.plusSeconds(300), null));
        assertEquals(grant, viewerGrants.findByTokenSha256("e".repeat(64)).orElseThrow().getId());
        audit.recordActor(actor, "UPLOAD", "ACCEPTED", operation.id(), studyRef, order, attempt, visit, patient,
                "corr-t014", Map.of());
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_logs where source = 'imaging_audit_events' and action = 'UPLOAD' and metadata ->> 'operationId' = ?", Integer.class, operation.id().toString()));
        reconciliation.openOperation(operation.id(), "ARCHIVE", ImagingReconciliationClassification.ORPHANED, "ARCHIVE_UNCERTAIN");
        reconciliation.openOperation(operation.id(), "CONFIRMATION", ImagingReconciliationClassification.RETRYABLE, "CONFIRMATION_UNAVAILABLE");
        reconciliation.openOperation(operation.id(), "CONFIRMATION", ImagingReconciliationClassification.RETRYABLE, "CONFIRMATION_UNAVAILABLE");
        var cases = reconciliationCases.findAllByOperationIdAndStateNot(operation.id(), "RESOLVED");
        assertEquals(2, cases.size());
        assertTrue(cases.stream().anyMatch(item -> item.getCategory().equals("CONFIRMATION") && item.getClassification().equals("RETRYABLE")));
    }

    @Test void adminDecisionClosesOnlyTheQueueCaseAndPersistsActorAudit() {
        var operation = operation(ImagingOperationState.QUARANTINED); operations.insert(operation);
        reconciliation.openOperation(operation.id(), "INGEST", ImagingReconciliationClassification.CONFLICT, "IDENTITY_MISMATCH");
        UUID queueReader = UUID.randomUUID();
        var queue = reconciliation.listQueue(queueReader, new ImagingReconciliationQuery("OPEN", "CONFLICT", "INGEST", 0, 25), "corr-reconciliation-list");
        var item = queue.getContent().stream().filter(c -> operation.id().equals(c.operationId())).findFirst().orElseThrow();
        UUID admin = UUID.randomUUID();
        reconciliation.recordDecision(admin, item.caseId(), new ImagingReconciliationDecisionCommand(ImagingReconciliationDecision.KEEP_QUARANTINED, "INC-42"), "corr-reconciliation-decision");
        entityManager.clear();
        reconciliation.recordDecision(admin, item.caseId(), new ImagingReconciliationDecisionCommand(ImagingReconciliationDecision.KEEP_QUARANTINED, "INC-42"), "corr-reconciliation-decision");
        var resolved = reconciliationCases.findById(item.caseId()).orElseThrow();
        assertEquals("RESOLVED", resolved.getState()); assertEquals("INC-42", resolved.getResolutionRef());
        assertEquals("KEEP_QUARANTINED", resolved.getResolutionDecision());
        assertEquals(ImagingOperationState.QUARANTINED, operations.findById(operation.id()).orElseThrow().state());
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_logs where action = 'IMAGING_RECONCILIATION_DECISION' and actor_account_id = ? and metadata ->> 'correlationRef' = 'corr-reconciliation-decision' and metadata ->> 'decisionCode' = 'KEEP_QUARANTINED' and metadata ->> 'caseId' = ?", Integer.class, admin, item.caseId().toString()));
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_logs where action = 'IMAGING_RECONCILIATION_QUEUE_READ' and actor_account_id = ? and metadata ->> 'correlationRef' = 'corr-reconciliation-list'", Integer.class, queueReader));
    }

    @Test void reconciliationWorkerClosesRetryableReleaseOnlyAfterMatchingDurableAcceptance() {
        var operation = operation(ImagingOperationState.ARCHIVED_NOT_SENT); operations.insert(operation);
        UUID event = UUID.randomUUID(); String hash = "d".repeat(64);
        reconciliation.openEvent(event, "RELEASE", ImagingReconciliationClassification.RETRYABLE, hash, "RELEASE_BEFORE_LINKED");
        var study = studies.findById(operation.id()).orElseThrow();
        study.persistConfirmedStudy("study-" + operation.id(), "archive-" + operation.id(), null, NOW);
        study.markAcknowledged(NOW);
        study.acceptFindingsRelease(event, hash, UUID.randomUUID(), UUID.randomUUID(), "e".repeat(64), NOW);
        studies.saveAndFlush(study);
        assertNotNull(reconciliation.reconcileOpenBatch(new UUID(0L, 0L)));
        assertEquals(1, jdbc.queryForObject("select count(*) from study_reconciliation_cases where event_id = ? and state = 'RESOLVED' and classification = 'LINKED' and resolution_decision is null", Integer.class, event));
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_logs where action = 'IMAGING_RECONCILIATION_AUTO_CLOSE' and metadata ->> 'correlationRef' = ? and metadata ->> 'eventId' = ?", Integer.class, event.toString(), event.toString()));
    }

    @Test void storesSafeOperationProjectionAndUsesOptimisticStateTransition() {
        var operation = operation(ImagingOperationState.PROCESSING); operations.insert(operation);
        var persisted = operations.findById(operation.id()).orElseThrow();
        assertEquals(operation.id(), persisted.id()); assertEquals(order, persisted.requestId());
        assertEquals(attempt, persisted.attemptId()); assertEquals(ImagingOperationState.PROCESSING, persisted.state());
        assertEquals(operation.payloadSha256(), persisted.payloadSha256());
        operations.transition(operation.id(), 0, ImagingOperationState.ARCHIVED_NOT_SENT, null, NOW.plusSeconds(1));
        var transitioned = operations.findById(operation.id()).orElseThrow();
        assertEquals(ImagingOperationState.ARCHIVED_NOT_SENT, transitioned.state()); assertEquals(1, transitioned.version());
        assertThrows(ImagingOperationException.class, () -> operations.transition(operation.id(), 0, ImagingOperationState.CONFIRMATION_PENDING, null, NOW.plusSeconds(2)));
        ImagingOperationException conflict = assertThrows(ImagingOperationException.class, () -> operations.transition(operation.id(), 1, ImagingOperationState.PROCESSING, null, NOW.plusSeconds(2)));
        assertEquals(fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome.STATE_CONFLICT, conflict.outcome());
    }

    @Test void persistsIdempotencyUniquenessAndKeepsIdentityTagsOutOfSchema() {
        var operation = operation(ImagingOperationState.PROCESSING); operations.insert(operation);
        String scopedKey = order + ":" + attempt + ":" + operation.id();
        var replay = idempotency.find(IdempotencyScope.IMAGING_UPLOAD, actor, scopedKey).orElseThrow();
        assertEquals(operation.id(), replay.resourceId()); assertTrue(replay.matches(operation.payloadSha256()));
        var saved = studies.findById(operation.id()).orElseThrow();
        assertEquals(operation.id(), saved.getId()); assertEquals(operation.payloadSha256(), saved.getPayloadSha256());
        assertTrue(jdbc.queryForList("select column_name from information_schema.columns where table_schema = current_schema() and table_name = 'imaging_studies' and column_name in ('patient_name','patient_id_tag','accession_number','issuer_value')").isEmpty());
        assertFalse(idempotency.record(IdempotencyScope.IMAGING_UPLOAD, actor, scopedKey, operation.payloadSha256(), UUID.randomUUID(), NOW));
    }

    @Test void studyInstanceUidIsUnique() {
        var first = operation(ImagingOperationState.PROCESSING); operations.insert(first);
        var second = operation(ImagingOperationState.PROCESSING); operations.insert(second);
        assertEquals(first.id(), studies.findByStudyInstanceUid(first.studyInstanceUid()).orElseThrow().getId());
        assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update(
                "update imaging_studies set study_instance_uid = ? where id = ?", first.studyInstanceUid(), second.id()));
    }

    private ImagingOperationRecord operation(ImagingOperationState state) {
        return new ImagingOperationRecord(UUID.randomUUID(), null, order, attempt, visit, patient, actor,
                ImagingOperationType.UPLOAD, state, "a".repeat(64), 1234,
                "1.2.826." + Math.abs(UUID.randomUUID().getMostSignificantBits()), null, 0, 0,
                ImagingVerificationStatus.NOT_EVALUATED, List.of(), null, null, 0, NOW, NOW);
    }
}
