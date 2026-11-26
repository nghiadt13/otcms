package fu.fa26.sep490.g58.otcms_be.imaging.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fu.fa26.sep490.g58.otcms_be.idempotency.model.dto.IdempotencyReplay;
import fu.fa26.sep490.g58.otcms_be.idempotency.model.IdempotencyScope;
import fu.fa26.sep490.g58.otcms_be.idempotency.service.IdempotencyService;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingAssignedWork;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingOperationRecord;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingStudyJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationState;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationType;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingVerificationStatus;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingStudyRepository;
import fu.fa26.sep490.g58.otcms_be.imaging.service.impl.ImagingUploadRegistrationService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImagingUploadRegistrationServiceTest {
    @Mock ImagingStudyRepository studies;
    @Mock IdempotencyService idempotency;

    @Test
    void insertsTheUploadReservationAndScopedKeyOnOneStudyRow() {
        var work = work();
        when(idempotency.record(eq(IdempotencyScope.IMAGING_UPLOAD), eq(work.actorAccountId()),
                eq(scopedKey(work, "upload-1")), eq("a".repeat(64)), any(UUID.class), any(Instant.class)))
                .thenReturn(true);
        when(studies.saveAndFlush(any(ImagingStudyJpaEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        var result = service().register(work.actorAccountId(), work.requestId(), work.attemptId(),
                "upload-1", "a".repeat(64), 321, work);
        assertTrue(result.created());
        assertEquals(work.requestId(), result.operation().requestId());
        assertEquals(work.attemptId(), result.operation().attemptId());
        assertEquals(321, result.operation().payloadSizeBytes());
        verify(studies).saveAndFlush(org.mockito.ArgumentMatchers.argThat(row ->
                row.getState() == ImagingOperationState.PROCESSING));
    }

    @Test
    void exactRetryReturnsExistingOperationWithoutAnotherInsert() {
        var work = work();
        var existing = new ImagingStudyJpaEntity(operation(work, "b".repeat(64)));
        when(idempotency.record(eq(IdempotencyScope.IMAGING_UPLOAD), eq(work.actorAccountId()),
                eq(scopedKey(work, "upload-1")), eq("b".repeat(64)), any(UUID.class), any(Instant.class)))
                .thenReturn(false);
        when(idempotency.find(IdempotencyScope.IMAGING_UPLOAD, work.actorAccountId(), scopedKey(work, "upload-1")))
                .thenReturn(Optional.of(new IdempotencyReplay("b".repeat(64), existing.getId(), Instant.now())));
        when(studies.findById(existing.getId())).thenReturn(Optional.of(existing));
        var result = service().register(work.actorAccountId(), work.requestId(), work.attemptId(),
                "upload-1", "b".repeat(64), 55, work);
        assertFalse(result.created());
        assertEquals(existing.getId(), result.operation().id());
        verify(studies, never()).saveAndFlush(any());
    }

    @Test
    void sameKeyWithChangedPayloadConflictsBeforeWriting() {
        var work = work();
        when(idempotency.record(eq(IdempotencyScope.IMAGING_UPLOAD), eq(work.actorAccountId()),
                eq(scopedKey(work, "upload-1")), eq("d".repeat(64)), any(UUID.class), any(Instant.class)))
                .thenReturn(false);
        when(idempotency.find(IdempotencyScope.IMAGING_UPLOAD, work.actorAccountId(), scopedKey(work, "upload-1")))
                .thenReturn(Optional.of(new IdempotencyReplay("c".repeat(64), UUID.randomUUID(), Instant.now())));
        var error = assertThrows(ImagingOperationException.class, () -> service().register(work.actorAccountId(),
                work.requestId(), work.attemptId(), "upload-1", "d".repeat(64), 55, work));
        assertEquals(ImagingOperationOutcome.IDEMPOTENCY_CONFLICT, error.outcome());
        verify(studies, never()).saveAndFlush(any());
    }

    private ImagingUploadRegistrationService service() { return new ImagingUploadRegistrationService(studies, idempotency); }

    private static String scopedKey(ImagingAssignedWork work, String key) {
        return work.requestId() + ":" + work.attemptId() + ":" + key;
    }

    private static ImagingAssignedWork work() {
        return new ImagingAssignedWork(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "assignment-v1", true);
    }

    private static ImagingOperationRecord operation(ImagingAssignedWork work, String digest) {
        return new ImagingOperationRecord(UUID.randomUUID(), null, work.requestId(), work.attemptId(),
                work.visitId(), work.patientId(), work.actorAccountId(), ImagingOperationType.UPLOAD,
                ImagingOperationState.PROCESSING, digest, 55, null, null, 0, 0,
                ImagingVerificationStatus.NOT_EVALUATED, List.of(), null, null, 0, Instant.now(), Instant.now());
    }
}
