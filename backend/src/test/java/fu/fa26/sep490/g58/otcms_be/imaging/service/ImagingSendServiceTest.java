package fu.fa26.sep490.g58.otcms_be.imaging.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingIdempotencyRecord;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingOperationRecord;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingConfirmationOutboxJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingIdempotencyRecordJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingIngestOperationJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationState;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationType;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingVerificationStatus;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingConfirmationOutboxRepository;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingIdempotencyRepository;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingIngestOperationRepository;
import fu.fa26.sep490.g58.otcms_be.imaging.service.impl.ImagingAuditPersistenceService;
import fu.fa26.sep490.g58.otcms_be.imaging.service.impl.ImagingIngestOperationPersistenceService;
import fu.fa26.sep490.g58.otcms_be.imaging.service.impl.ImagingSendService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImagingSendServiceTest {
    @Mock ImagingIngestOperationRepository operations;
    @Mock ImagingIdempotencyRepository idempotency;
    @Mock ImagingConfirmationOutboxRepository outbox;
    @Mock ImagingIngestOperationPersistenceService operationPersistence;
    @Mock ImagingAuditPersistenceService audit;

    @Test
    void sendPersistsOutboxAndIdempotencyBeforeReturningPendingStatus() {
        ImagingOperationRecord archived = operation(ImagingOperationState.ARCHIVED_NOT_SENT, true);
        when(operations.findById(archived.id())).thenReturn(Optional.of(new ImagingIngestOperationJpaEntity(archived)));
        when(idempotency.findByActorAccountIdAndRequestIdAndAttemptIdAndOperationTypeAndIdempotencyKey(
                archived.actorAccountId(), archived.requestId(), archived.attemptId(), ImagingOperationType.SEND,
                "send-1")).thenReturn(Optional.empty());
        ImagingOperationRecord pending = new ImagingOperationRecord(archived.id(), archived.studyRef(),
                archived.requestId(), archived.attemptId(), archived.visitId(), archived.patientId(),
                archived.actorAccountId(), ImagingOperationType.UPLOAD, ImagingOperationState.CONFIRMATION_PENDING,
                archived.payloadSha256(), archived.payloadSizeBytes(), archived.studyInstanceUid(), archived.modality(),
                archived.seriesCount(), archived.instanceCount(), archived.verificationStatus(),
                archived.verificationEvidence(), archived.archiveStudyRef(), null, 1, archived.createdAt(),
                Instant.now());
        when(operationPersistence.findById(archived.id())).thenReturn(Optional.of(pending));
        when(outbox.saveAndFlush(any(ImagingConfirmationOutboxJpaEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(idempotency.saveAndFlush(any(ImagingIdempotencyRecordJpaEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var status = service().send(archived.actorAccountId(), archived.id(), "send-1");

        assertEquals(ImagingOperationState.CONFIRMATION_PENDING, status.state());
        verify(audit).recordActor(org.mockito.ArgumentMatchers.eq(archived.actorAccountId()),
                org.mockito.ArgumentMatchers.eq("IMAGING_SEND"), org.mockito.ArgumentMatchers.eq("ACCEPTED"),
                org.mockito.ArgumentMatchers.eq(archived.id()), org.mockito.ArgumentMatchers.eq(archived.studyRef()),
                org.mockito.ArgumentMatchers.eq(archived.requestId()), org.mockito.ArgumentMatchers.eq(archived.attemptId()),
                org.mockito.ArgumentMatchers.eq(archived.visitId()), org.mockito.ArgumentMatchers.eq(archived.patientId()),
                any(), any());
        verify(outbox).saveAndFlush(any(ImagingConfirmationOutboxJpaEntity.class));
        verify(idempotency).saveAndFlush(any(ImagingIdempotencyRecordJpaEntity.class));
        verify(operationPersistence).transition(org.mockito.ArgumentMatchers.eq(archived.id()),
                org.mockito.ArgumentMatchers.eq(0L), org.mockito.ArgumentMatchers.eq(ImagingOperationState.CONFIRMATION_PENDING),
                org.mockito.ArgumentMatchers.isNull(), any());
    }

    @Test
    void quarantinedOrUnverifiedOperationCannotBeSent() {
        ImagingOperationRecord quarantined = operation(ImagingOperationState.QUARANTINED, false);
        when(operations.findById(quarantined.id())).thenReturn(Optional.of(new ImagingIngestOperationJpaEntity(quarantined)));
        when(idempotency.findByActorAccountIdAndRequestIdAndAttemptIdAndOperationTypeAndIdempotencyKey(
                quarantined.actorAccountId(), quarantined.requestId(), quarantined.attemptId(), ImagingOperationType.SEND,
                "send-2")).thenReturn(Optional.empty());

        ImagingOperationException conflict = assertThrows(ImagingOperationException.class,
                () -> service().send(quarantined.actorAccountId(), quarantined.id(), "send-2"));

        assertEquals(ImagingOperationOutcome.STATE_CONFLICT, conflict.outcome());
        verifyNoInteractions(outbox, operationPersistence, audit);
    }

    private ImagingSendService service() {
        return new ImagingSendService(operations, idempotency, outbox, operationPersistence, audit);
    }

    private static ImagingOperationRecord operation(ImagingOperationState state, boolean verified) {
        return new ImagingOperationRecord(UUID.randomUUID(), verified ? "study-1" : null, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), ImagingOperationType.UPLOAD,
                state, "a".repeat(64), 123, "1.2.3", "CT", 1, 1,
                verified ? ImagingVerificationStatus.CONFIRMED_MATCH : ImagingVerificationStatus.MISMATCH,
                verified ? List.of("PATIENT_ID_MATCH", "ACCESSION_MATCH") : List.of(),
                verified ? "orthanc-study" : null, null, 0, Instant.now(), Instant.now());
    }
}
