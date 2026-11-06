package fu.fa26.sep490.g58.otcms_be.imaging.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
    void statusRechecksCurrentOwnerForThePersistedRequestAndAttempt() {
        ImagingOperationRecord operation = operation(ImagingOperationState.PROCESSING);
        when(operations.findById(operation.id())).thenReturn(Optional.of(operation));
        ImagingIngestApplicationService service = service();

        var status = service.getOperationStatus(operation.actorAccountId(), operation.id());

        assertEquals(operation.id(), status.operationId());
        assertEquals(operation.state(), status.state());
        verify(workAuthorization).requireAssignedWork(operation.actorAccountId(),
                operation.requestId(), operation.attemptId());
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
        when(workAuthorization.requireAssignedWork(operation.actorAccountId(),
                operation.requestId(), operation.attemptId()))
                .thenThrow(new ImagingOperationException(ImagingOperationOutcome.UNAUTHORIZED_RECORD));
        ImagingIngestApplicationService service = service();

        ImagingOperationException denied = assertThrows(ImagingOperationException.class,
                () -> service.getOperationStatus(operation.actorAccountId(), operation.id()));

        assertEquals(ImagingOperationOutcome.UNAUTHORIZED_RECORD, denied.outcome());
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
