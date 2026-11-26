package fu.fa26.sep490.g58.otcms_be.imaging.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import fu.fa26.sep490.g58.otcms_be.idempotency.model.IdempotencyScope;
import fu.fa26.sep490.g58.otcms_be.idempotency.service.IdempotencyService;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingOperationRecord;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingStudyJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationState;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationType;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingVerificationStatus;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingStudyRepository;
import fu.fa26.sep490.g58.otcms_be.imaging.service.impl.ImagingAuditPersistenceService;
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
    @Mock ImagingStudyRepository studies;
    @Mock ImagingAuditPersistenceService audit;
    @Mock IdempotencyService idempotency;

    @Test
    void sendStoresStableEventAndIdempotencyOnTheStudy() {
        var archived = new ImagingStudyJpaEntity(operation(ImagingOperationState.ARCHIVED_NOT_SENT, true));
        when(studies.findById(archived.getId())).thenReturn(Optional.of(archived));
        when(idempotency.record(eq(IdempotencyScope.IMAGING_SEND), eq(archived.getId()), eq("send-1"),
                matches("[0-9a-f]{64}"), eq(archived.getId()))).thenReturn(true);
        when(studies.saveAndFlush(any(ImagingStudyJpaEntity.class))).thenAnswer(call -> call.getArgument(0));
        var status = service().send(archived.getActorAccountId(), archived.getId(), "send-1");
        assertEquals(ImagingOperationState.CONFIRMATION_PENDING, status.state());
        assertNotNull(archived.getConfirmationEventId());
        assertEquals(1, archived.getVersion());
        verify(studies).saveAndFlush(archived);
        verify(audit).recordActor(eq(archived.getActorAccountId()),
                eq("IMAGING_SEND"), eq("ACCEPTED"),
                eq(archived.getId()), eq(archived.getStudyRef()),
                eq(archived.getRequestId()), eq(archived.getAttemptId()),
                eq(archived.getVisitId()), eq(archived.getPatientId()),
                any(), any());
    }

    @Test
    void quarantinedOrUnverifiedStudyCannotBeSent() {
        var quarantined = new ImagingStudyJpaEntity(operation(ImagingOperationState.QUARANTINED, false));
        when(studies.findById(quarantined.getId())).thenReturn(Optional.of(quarantined));
        ImagingOperationException conflict = assertThrows(ImagingOperationException.class,
                () -> service().send(quarantined.getActorAccountId(), quarantined.getId(), "send-2"));
        assertEquals(ImagingOperationOutcome.STATE_CONFLICT, conflict.outcome());
        verify(studies, never()).saveAndFlush(any());
    }

    private ImagingSendService service() { return new ImagingSendService(studies, audit, idempotency); }

    private static ImagingOperationRecord operation(ImagingOperationState state, boolean verified) {
        return new ImagingOperationRecord(UUID.randomUUID(), verified ? "study-1" : null, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), ImagingOperationType.UPLOAD,
                state, "a".repeat(64), 123, "1.2.3", "CT", 1, 1,
                verified ? ImagingVerificationStatus.CONFIRMED_MATCH : ImagingVerificationStatus.MISMATCH,
                verified ? List.of("PATIENT_ID_MATCH", "ACCESSION_MATCH") : List.of(),
                verified ? "orthanc-study" : null, null, 0, Instant.now(), Instant.now());
    }
}
