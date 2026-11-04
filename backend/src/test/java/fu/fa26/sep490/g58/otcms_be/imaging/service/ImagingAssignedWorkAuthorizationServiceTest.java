package fu.fa26.sep490.g58.otcms_be.imaging.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.imaging.integration.diagnostic.ImagingWorkAuthorizationPort;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingAssignedWork;
import fu.fa26.sep490.g58.otcms_be.imaging.service.impl.ImagingAssignedWorkAuthorizationService;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ImagingAssignedWorkAuthorizationServiceTest {
    @Test
    void returnsOnlyTheCurrentOwnerDecisionForTheRequestedAttempt() {
        UUID actor = UUID.randomUUID();
        UUID request = UUID.randomUUID();
        UUID attempt = UUID.randomUUID();
        ImagingAssignedWork allowed = new ImagingAssignedWork(actor, request, attempt,
                UUID.randomUUID(), UUID.randomUUID(), "fixture-v1", true);
        ImagingWorkAuthorizationPort controlledOwner = (actorId, requestId, attemptId) -> allowed;
        ImagingAssignedWorkAuthorizationService service = new ImagingAssignedWorkAuthorizationService(
                controlledOwner, new ImagingWorkAuthorizationPolicy());

        assertEquals(allowed, service.requireAssignedWork(actor, request, attempt));
    }

    @Test
    void deniesWhenOwnerReturnsNoCurrentAssignment() {
        ImagingWorkAuthorizationPort unavailableOwner = (actorId, requestId, attemptId) -> null;
        ImagingAssignedWorkAuthorizationService service = new ImagingAssignedWorkAuthorizationService(
                unavailableOwner, new ImagingWorkAuthorizationPolicy());

        ImagingOperationException exception = assertThrows(ImagingOperationException.class,
                () -> service.requireAssignedWork(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));
        assertEquals(ImagingOperationOutcome.UNAUTHORIZED_RECORD, exception.outcome());
    }
}
