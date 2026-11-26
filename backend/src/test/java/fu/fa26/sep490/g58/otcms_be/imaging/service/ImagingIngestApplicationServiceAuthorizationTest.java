package fu.fa26.sep490.g58.otcms_be.imaging.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingOperationRecord;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationState;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationType;
import fu.fa26.sep490.g58.otcms_be.imaging.service.impl.ImagingAssignedWorkAuthorizationService;
import fu.fa26.sep490.g58.otcms_be.imaging.service.impl.ImagingIngestApplicationService;
import fu.fa26.sep490.g58.otcms_be.imaging.service.impl.ImagingIngestOperationPersistenceService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImagingIngestApplicationServiceAuthorizationTest {
    private static final Instant NOW = Instant.parse("2026-11-05T00:00:00Z");

    @Mock ImagingAssignedWorkAuthorizationService workAuthorization;
    @Mock ImagingIngestOperationPersistenceService operations;

    @Test
    void statusRechecksCurrentOwnerReadAccessForThePersistedRequest() {
        ImagingOperationRecord operation = operation(ImagingOperationState.PROCESSING);
        when(operations.findById(operation.id())).thenReturn(Optional.of(operation));
        ImagingIngestApplicationService service = service();

        var status = service.getOperationStatus(operation.actorAccountId(), operation.id());

        assertEquals(operation.id(), status.operationId());
        assertEquals(operation.state(), status.state());
        verify(workAuthorization).requireStatusRead(operation.actorAccountId(), operation.requestId());
    }

    @Test
    void sendRechecksCurrentOwnerBeforeCheckingOperationState() {
        ImagingOperationRecord operation = operation(ImagingOperationState.PROCESSING);
        when(operations.findById(operation.id())).thenReturn(Optional.of(operation));
        ImagingIngestApplicationService service = service();

        ImagingOperationException conflict = assertThrows(ImagingOperationException.class,
                () -> service.send(operation.actorAccountId(), operation.id(), "send-key-001"));

        assertEquals(ImagingOperationOutcome.STATE_CONFLICT, conflict.outcome());
        verify(workAuthorization).requireAssignedWork(operation.actorAccountId(),
                operation.requestId(), operation.attemptId());
    }

    @Test
    void missingOperationIsGenericAndDoesNotCallOwnerService() {
        UUID operationId = UUID.randomUUID();
        when(operations.findById(operationId)).thenReturn(Optional.empty());
        ImagingIngestApplicationService service = service();

        ImagingOperationException missing = assertThrows(ImagingOperationException.class,
                () -> service.getOperationStatus(UUID.randomUUID(), operationId));

        assertEquals(ImagingOperationOutcome.UNAUTHORIZED_RECORD, missing.outcome());
        verifyNoInteractions(workAuthorization);
    }

    @Test
    void deniedCurrentAssignmentBlocksStatusDisclosure() {
        ImagingOperationRecord operation = operation(ImagingOperationState.PROCESSING);
        when(operations.findById(operation.id())).thenReturn(Optional.of(operation));
        doThrow(new ImagingOperationException(ImagingOperationOutcome.UNAUTHORIZED_RECORD))
                .when(workAuthorization).requireStatusRead(operation.actorAccountId(), operation.requestId());
        ImagingIngestApplicationService service = service();

        ImagingOperationException denied = assertThrows(ImagingOperationException.class,
                () -> service.getOperationStatus(operation.actorAccountId(), operation.id()));

        assertEquals(ImagingOperationOutcome.UNAUTHORIZED_RECORD, denied.outcome());
    }

    @Test
    void listReturnsPersistedCountOnlyAfterStatusAuthorization() {
        var original = operation(ImagingOperationState.LINKED);
        var record = new ImagingOperationRecord(original.id(), original.studyRef(), original.requestId(),
                original.attemptId(), original.visitId(), original.patientId(), original.actorAccountId(),
                original.operationType(), original.state(), original.payloadSha256(), original.payloadSizeBytes(),
                original.studyInstanceUid(), original.modality(), 6, 123, original.verificationStatus(),
                original.verificationEvidence(), original.archiveStudyRef(), original.safeOutcomeCode(),
                original.version(), original.createdAt(), original.updatedAt());
        when(operations.findAllByRequestAndAttempt(record.requestId(), record.attemptId()))
                .thenReturn(java.util.List.of(record));
        var result = service().getOperationsForAttempt(record.actorAccountId(), record.requestId(), record.attemptId());
        assertEquals(123, result.getFirst().instanceCount());
        verify(workAuthorization).requireStatusRead(record.actorAccountId(), record.requestId());
    }

    @Test
    void listDoesNotReadOperationsWhenTechnicianIsNotAssigned() {
        var actor = UUID.randomUUID();
        var request = UUID.randomUUID();
        doThrow(new ImagingOperationException(ImagingOperationOutcome.UNAUTHORIZED_RECORD))
                .when(workAuthorization).requireStatusRead(actor, request);
        assertThrows(ImagingOperationException.class,
                () -> service().getOperationsForAttempt(actor, request, UUID.randomUUID()));
        verifyNoInteractions(operations);
    }

    private ImagingIngestApplicationService service() {
        return new ImagingIngestApplicationService(workAuthorization, operations);
    }

    private ImagingOperationRecord operation(ImagingOperationState state) {
        return new ImagingOperationRecord(UUID.randomUUID(), null, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), ImagingOperationType.UPLOAD,
                state, "a".repeat(64), 1234, null, null, 0, 0,
                fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingVerificationStatus.NOT_EVALUATED,
                java.util.List.of(), null, null, 0, NOW, NOW);
    }
}
