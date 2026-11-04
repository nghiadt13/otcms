package fu.fa26.sep490.g58.otcms_be.imaging.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingAssignedWork;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ImagingWorkAuthorizationPolicyTest {
    private final ImagingWorkAuthorizationPolicy policy = new ImagingWorkAuthorizationPolicy();

    @Test
    void acceptsOnlyTheOwnerDecisionForTheAuthenticatedActorAndExactAttempt() {
        UUID actor = UUID.randomUUID();
        UUID request = UUID.randomUUID();
        UUID attempt = UUID.randomUUID();
        ImagingAssignedWork ownerDecision = new ImagingAssignedWork(actor, request, attempt,
                UUID.randomUUID(), UUID.randomUUID(), "assignment-v3", true);

        assertEquals(ownerDecision, policy.requireAssignedWork(actor, request, attempt, ownerDecision));
    }

    @Test
    void failsClosedForAbsentOrNegativeOwnerDecision() {
        UUID actor = UUID.randomUUID();
        UUID request = UUID.randomUUID();
        UUID attempt = UUID.randomUUID();
        ImagingAssignedWork denied = new ImagingAssignedWork(actor, request, attempt,
                UUID.randomUUID(), UUID.randomUUID(), "assignment-v3", false);

        assertUnauthorized(actor, request, attempt, null);
        assertUnauthorized(actor, request, attempt, denied);
    }

    @Test
    void failsClosedWhenOwnerDecisionIsForDifferentActorRequestOrAttempt() {
        UUID actor = UUID.randomUUID();
        UUID request = UUID.randomUUID();
        UUID attempt = UUID.randomUUID();
        ImagingAssignedWork ownerDecision = new ImagingAssignedWork(actor, request, attempt,
                UUID.randomUUID(), UUID.randomUUID(), "assignment-v3", true);

        assertUnauthorized(UUID.randomUUID(), request, attempt, ownerDecision);
        assertUnauthorized(actor, UUID.randomUUID(), attempt, ownerDecision);
        assertUnauthorized(actor, request, UUID.randomUUID(), ownerDecision);
    }

    private void assertUnauthorized(UUID actor, UUID request, UUID attempt, ImagingAssignedWork decision) {
        ImagingOperationException exception = assertThrows(ImagingOperationException.class,
                () -> policy.requireAssignedWork(actor, request, attempt, decision));
        assertEquals(ImagingOperationOutcome.UNAUTHORIZED_RECORD, exception.outcome());
    }
}
