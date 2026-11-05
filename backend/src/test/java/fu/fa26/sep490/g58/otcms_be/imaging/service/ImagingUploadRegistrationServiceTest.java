package fu.fa26.sep490.g58.otcms_be.imaging.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingAssignedWork;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingIdempotencyRecordJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingIngestOperationJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationType;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingIdempotencyRepository;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingIngestOperationRepository;
import fu.fa26.sep490.g58.otcms_be.imaging.service.impl.ImagingUploadRegistrationService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImagingUploadRegistrationServiceTest {
    @Mock ImagingIngestOperationRepository operations;
    @Mock ImagingIdempotencyRepository idempotency;

    @Test
    void registersOperationAndIdempotencyBeforeReturningCreatedReservation() {
        UUID actorId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        ImagingAssignedWork work = new ImagingAssignedWork(actorId, requestId, attemptId,
                UUID.randomUUID(), UUID.randomUUID(), "assignment-v1", true);
        when(idempotency.findByActorAccountIdAndRequestIdAndAttemptIdAndOperationTypeAndIdempotencyKey(
                actorId, requestId, attemptId, ImagingOperationType.UPLOAD, "upload-1"))
                .thenReturn(Optional.empty());
        when(operations.saveAndFlush(any(ImagingIngestOperationJpaEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(idempotency.saveAndFlush(any(ImagingIdempotencyRecordJpaEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = new ImagingUploadRegistrationService(operations, idempotency)
                .register(actorId, requestId, attemptId, "upload-1", "a".repeat(64), 321, work);

        assertTrue(result.created());
        assertEquals(requestId, result.operation().requestId());
        assertEquals(attemptId, result.operation().attemptId());
        assertEquals(321, result.operation().payloadSizeBytes());
        verify(operations).saveAndFlush(any(ImagingIngestOperationJpaEntity.class));
        verify(idempotency).saveAndFlush(any(ImagingIdempotencyRecordJpaEntity.class));
    }

    @Test
    void sameKeyAndSameDigestReturnsExistingOperationWithoutCreatingAnother() {
        UUID actorId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        var prior = new fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingIdempotencyRecord(UUID.randomUUID(),
                actorId, requestId, attemptId, ImagingOperationType.UPLOAD, "upload-1", "b".repeat(64),
                operationId, null, java.time.Instant.now());
        when(idempotency.findByActorAccountIdAndRequestIdAndAttemptIdAndOperationTypeAndIdempotencyKey(
                actorId, requestId, attemptId, ImagingOperationType.UPLOAD, "upload-1"))
                .thenReturn(Optional.of(new ImagingIdempotencyRecordJpaEntity(prior)));
        var existing = new ImagingIngestOperationJpaEntity(new fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingOperationRecord(
                operationId, null, requestId, attemptId, UUID.randomUUID(), UUID.randomUUID(), actorId,
                ImagingOperationType.UPLOAD,
                fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationState.PROCESSING,
                "b".repeat(64), 55, null, null, 0, 0,
                fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingVerificationStatus.NOT_EVALUATED,
                java.util.List.of(), null, null, 0, java.time.Instant.now(), java.time.Instant.now()));
        when(operations.findById(operationId)).thenReturn(Optional.of(existing));

        var result = new ImagingUploadRegistrationService(operations, idempotency)
                .register(actorId, requestId, attemptId, "upload-1", "b".repeat(64), 55,
                        new ImagingAssignedWork(actorId, requestId, attemptId, existing.toModel().visitId(),
                                existing.toModel().patientId(), "assignment-v1", true));

        assertFalse(result.created());
        assertEquals(operationId, result.operation().id());
        verify(operations).findById(operationId);
        org.mockito.Mockito.verifyNoMoreInteractions(operations, idempotency);
    }

    @Test
    void changedPayloadWithSameKeyFailsBeforeOperationLookup() {
        UUID actorId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        var prior = new fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingIdempotencyRecord(UUID.randomUUID(),
                actorId, requestId, attemptId, ImagingOperationType.UPLOAD, "upload-1", "c".repeat(64),
                UUID.randomUUID(), null, java.time.Instant.now());
        when(idempotency.findByActorAccountIdAndRequestIdAndAttemptIdAndOperationTypeAndIdempotencyKey(
                actorId, requestId, attemptId, ImagingOperationType.UPLOAD, "upload-1"))
                .thenReturn(Optional.of(new ImagingIdempotencyRecordJpaEntity(prior)));

        var thrown = org.junit.jupiter.api.Assertions.assertThrows(
                fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException.class,
                () -> new ImagingUploadRegistrationService(operations, idempotency).register(actorId, requestId,
                        attemptId, "upload-1", "d".repeat(64), 55,
                        new ImagingAssignedWork(actorId, requestId, attemptId, UUID.randomUUID(), UUID.randomUUID(),
                                "assignment-v1", true)));

        assertEquals(fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome.IDEMPOTENCY_CONFLICT,
                thrown.outcome());
        verifyNoInteractions(operations);
    }

}
