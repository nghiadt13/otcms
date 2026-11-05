package fu.fa26.sep490.g58.otcms_be.imaging.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingReconciliationDecision;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingReconciliationDecisionCommand;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingReconciliationCaseJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingIngestOperationJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingReleaseInboxJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingReconciliationClassification;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingIngestOperationRepository;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingReconciliationCaseRepository;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingReleaseInboxRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ImagingReconciliationServiceTest {
    @Test
    void recordsCaseAndEventReferencesInSafeDecisionAuditMetadata() {
        var repository = mock(ImagingReconciliationCaseRepository.class);
        var audit = mock(ImagingAuditPersistenceService.class);
        var service = service(repository, audit, mock(ImagingIngestOperationRepository.class),
                mock(ImagingReleaseInboxRepository.class));
        UUID caseId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        when(repository.findById(caseId)).thenReturn(Optional.of(new ImagingReconciliationCaseJpaEntity(
                caseId, null, eventId, "RELEASE", "ORPHANED", "OPEN", "RELEASE_EVENT_INVALID",
                "IMAGING_OPERATIONS", Instant.now(), null, null)));
        when(repository.recordAdminDecision(eq(caseId), eq("OPEN"), eq("RESOLVED"),
                eq("EXTERNAL_RESOLUTION_CONFIRMED"), eq("INC-42"), any(), any(), any())).thenReturn(1);

        service.recordDecision(actorId, caseId, new ImagingReconciliationDecisionCommand(
                ImagingReconciliationDecision.EXTERNAL_RESOLUTION_CONFIRMED, "INC-42"), "corr-42");

        ArgumentCaptor<java.util.Map<String, Object>> metadata = ArgumentCaptor.forClass(java.util.Map.class);
        verify(audit).recordActor(eq(actorId), eq("IMAGING_RECONCILIATION_DECISION"),
                eq("EXTERNAL_RESOLUTION_CONFIRMED"), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                eq("corr-42"), metadata.capture());
        assertEquals(caseId.toString(), metadata.getValue().get("caseId"));
        assertEquals(eventId.toString(), metadata.getValue().get("eventId"));
    }

    @Test
    void autoResolvesRetryableReleaseOnlyWithMatchingAcceptedPayloadEvidence() {
        var repository = mock(ImagingReconciliationCaseRepository.class);
        var audit = mock(ImagingAuditPersistenceService.class);
        var releases = mock(ImagingReleaseInboxRepository.class);
        UUID caseId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Instant updatedAt = Instant.parse("2026-11-05T00:00:00Z");
        var item = new ImagingReconciliationCaseJpaEntity(caseId, null, eventId, "RELEASE", "RETRYABLE", "OPEN",
                "RELEASE_BEFORE_LINKED", "a".repeat(64), "IMAGING_OPERATIONS", updatedAt, null, null);
        var receipt = mock(ImagingReleaseInboxJpaEntity.class);
        when(repository.findAllByStateAndAssignedOwnerQueueAndIdGreaterThanOrderByIdAsc(
                eq("OPEN"), eq("IMAGING_OPERATIONS"), any(), any())).thenReturn(java.util.List.of(item));
        when(releases.findByEventId(eventId)).thenReturn(Optional.of(receipt));
        when(receipt.getOutcome()).thenReturn("ACCEPTED");
        when(receipt.getPayloadSha256()).thenReturn("a".repeat(64));
        when(repository.resolveRetryableWithLinkedEvidence(eq(caseId), eq(updatedAt), any())).thenReturn(1);

        assertEquals(caseId, service(repository, audit, mock(ImagingIngestOperationRepository.class), releases)
                .reconcileOpenBatch(null));

        verify(repository).resolveRetryableWithLinkedEvidence(eq(caseId), eq(updatedAt), any());
        verify(audit).recordService(eq("FEAT_004"), eq("IMAGING_RECONCILIATION_AUTO_CLOSE"), eq("LINKED"),
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(eventId.toString()), any());
    }

    @Test
    void keepsChangedPayloadAndConflictCasesForAdmin() {
        var repository = mock(ImagingReconciliationCaseRepository.class);
        var audit = mock(ImagingAuditPersistenceService.class);
        var releases = mock(ImagingReleaseInboxRepository.class);
        UUID eventId = UUID.randomUUID();
        var changedPayload = new ImagingReconciliationCaseJpaEntity(UUID.randomUUID(), null, eventId, "RELEASE",
                "RETRYABLE", "OPEN", "RELEASE_BEFORE_LINKED", "a".repeat(64), "IMAGING_OPERATIONS",
                Instant.now(), null, null);
        var conflict = new ImagingReconciliationCaseJpaEntity(UUID.randomUUID(), null, UUID.randomUUID(), "RELEASE",
                "CONFLICT", "OPEN", "RELEASE_EVENT_CONFLICT", "b".repeat(64), "IMAGING_OPERATIONS",
                Instant.now(), null, null);
        var receipt = mock(ImagingReleaseInboxJpaEntity.class);
        when(repository.findAllByStateAndAssignedOwnerQueueAndIdGreaterThanOrderByIdAsc(
                eq("OPEN"), eq("IMAGING_OPERATIONS"), any(), any())).thenReturn(java.util.List.of(changedPayload, conflict));
        when(releases.findByEventId(eventId)).thenReturn(Optional.of(receipt));
        when(receipt.getOutcome()).thenReturn("ACCEPTED");
        when(receipt.getPayloadSha256()).thenReturn("c".repeat(64));

        service(repository, audit, mock(ImagingIngestOperationRepository.class), releases).reconcileOpenBatch(null);

        verify(repository, never()).resolveRetryableWithLinkedEvidence(any(), any(), any());
        verifyNoInteractions(audit);
    }

    @Test
    void autoResolvesRetryableOperationCaseOnlyAfterOperationIsAlreadyLinked() {
        var repository = mock(ImagingReconciliationCaseRepository.class);
        var audit = mock(ImagingAuditPersistenceService.class);
        var operations = mock(ImagingIngestOperationRepository.class);
        UUID caseId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        Instant updatedAt = Instant.parse("2026-11-05T00:00:00Z");
        var item = new ImagingReconciliationCaseJpaEntity(caseId, operationId, null, "CONFIRMATION", "RETRYABLE",
                "OPEN", "CONFIRMATION_UNAVAILABLE", "IMAGING_OPERATIONS", updatedAt, null, null);
        var operation = mock(ImagingIngestOperationJpaEntity.class);
        when(repository.findAllByStateAndAssignedOwnerQueueAndIdGreaterThanOrderByIdAsc(
                eq("OPEN"), eq("IMAGING_OPERATIONS"), any(), any())).thenReturn(java.util.List.of(item));
        when(operations.findById(operationId)).thenReturn(Optional.of(operation));
        when(operation.getState()).thenReturn(fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationState.LINKED);
        when(repository.resolveRetryableWithLinkedEvidence(eq(caseId), eq(updatedAt), any())).thenReturn(1);

        service(repository, audit, operations, mock(ImagingReleaseInboxRepository.class)).reconcileOpenBatch(null);

        verify(repository).resolveRetryableWithLinkedEvidence(eq(caseId), eq(updatedAt), any());
        verify(audit).recordService(eq("FEAT_004"), eq("IMAGING_RECONCILIATION_AUTO_CLOSE"), eq("LINKED"),
                eq(operationId), isNull(), isNull(), isNull(), isNull(), isNull(), eq(operationId.toString()), any());
    }

    private static ImagingReconciliationServiceImpl service(ImagingReconciliationCaseRepository cases,
            ImagingAuditPersistenceService audit, ImagingIngestOperationRepository operations,
            ImagingReleaseInboxRepository releases) {
        return new ImagingReconciliationServiceImpl(cases, audit, operations, releases);
    }

    @Test
    void classifiesIdentityAndReplayConflictsWithoutAutomaticRecovery() {
        assertEquals(ImagingReconciliationClassification.CONFLICT,
                ImagingReconciliationServiceImpl.classify("IDENTITY_MISMATCH"));
        assertEquals(ImagingReconciliationClassification.CONFLICT,
                ImagingReconciliationServiceImpl.classify("RELEASE_EVENT_CONFLICT"));
    }

    @Test
    void classifiesAvailableRetryAndUncertainArchiveOutcomes() {
        assertEquals(ImagingReconciliationClassification.RETRYABLE,
                ImagingReconciliationServiceImpl.classify("RELEASE_BEFORE_LINKED"));
        assertEquals(ImagingReconciliationClassification.RETRYABLE,
                ImagingReconciliationServiceImpl.classify("ARCHIVE_OR_DATABASE_OUTCOME_UNCERTAIN"));
        assertEquals(ImagingReconciliationClassification.ORPHANED,
                ImagingReconciliationServiceImpl.classify("IDENTITY_UNKNOWN"));
        assertThrows(IllegalArgumentException.class,
                () -> ImagingReconciliationServiceImpl.classify("UNCLASSIFIED_REASON"));
    }
}
