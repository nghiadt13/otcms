package fu.fa26.sep490.g58.otcms_be.imaging.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class ImagingUploadRegistrationServiceTest {
    @Mock ImagingStudyRepository studies;

    @Test
    void insertsTheUploadReservationAndScopedKeyOnOneStudyRow() {
        var work = work();
        when(studies.saveAndFlush(any(ImagingStudyJpaEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        var result = service().register(work.actorAccountId(), work.requestId(), work.attemptId(),
                "upload-1", "a".repeat(64), 321, work);
        assertTrue(result.created());
        assertEquals(work.requestId(), result.operation().requestId());
        assertEquals(work.attemptId(), result.operation().attemptId());
        assertEquals(321, result.operation().payloadSizeBytes());
        verify(studies).saveAndFlush(org.mockito.ArgumentMatchers.argThat(row ->
                "upload-1".equals(row.getUploadIdempotencyKey()) && row.getState() == ImagingOperationState.PROCESSING));
    }

    @Test
    void exactRetryReturnsExistingOperationWithoutAnotherInsert() {
        var work = work();
        var existing = new ImagingStudyJpaEntity(operation(work, "b".repeat(64)), "upload-1");
        when(studies.saveAndFlush(any(ImagingStudyJpaEntity.class))).thenThrow(uploadScopeViolation());
        when(studies.findByActorAccountIdAndRequestIdAndAttemptIdAndUploadIdempotencyKey(
                work.actorAccountId(), work.requestId(), work.attemptId(), "upload-1"))
                .thenReturn(Optional.of(existing));
        var result = service().register(work.actorAccountId(), work.requestId(), work.attemptId(),
                "upload-1", "b".repeat(64), 55, work);
        assertFalse(result.created());
        assertEquals(existing.getId(), result.operation().id());
    }

    @Test
    void sameKeyWithChangedPayloadConflictsBeforeWriting() {
        var work = work();
        var existing = new ImagingStudyJpaEntity(operation(work, "c".repeat(64)), "upload-1");
        when(studies.saveAndFlush(any(ImagingStudyJpaEntity.class))).thenThrow(uploadScopeViolation());
        when(studies.findByActorAccountIdAndRequestIdAndAttemptIdAndUploadIdempotencyKey(
                work.actorAccountId(), work.requestId(), work.attemptId(), "upload-1"))
                .thenReturn(Optional.of(existing));
        var error = assertThrows(ImagingOperationException.class, () -> service().register(work.actorAccountId(),
                work.requestId(), work.attemptId(), "upload-1", "d".repeat(64), 55, work));
        assertEquals(ImagingOperationOutcome.IDEMPOTENCY_CONFLICT, error.outcome());
    }

    @Test
    void rereadsTheWinnerAfterTheUploadScopeUniqueConstraint() {
        var work = work();
        var existing = new ImagingStudyJpaEntity(operation(work, "e".repeat(64)), "upload-1");
        when(studies.saveAndFlush(any(ImagingStudyJpaEntity.class))).thenThrow(uploadScopeViolation());
        when(studies.findByActorAccountIdAndRequestIdAndAttemptIdAndUploadIdempotencyKey(
                work.actorAccountId(), work.requestId(), work.attemptId(), "upload-1"))
                .thenReturn(Optional.of(existing));
        var result = service().register(work.actorAccountId(), work.requestId(), work.attemptId(),
                "upload-1", "e".repeat(64), 55, work);
        assertFalse(result.created());
        assertEquals(existing.getId(), result.operation().id());
    }

    private ImagingUploadRegistrationService service() { return new ImagingUploadRegistrationService(studies); }

    private static DataIntegrityViolationException uploadScopeViolation() {
        return new DataIntegrityViolationException("duplicate",
                new ConstraintViolationException("duplicate", new SQLException("duplicate key", "23505"),
                        "uk_imaging_studies_upload_scope"));
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
