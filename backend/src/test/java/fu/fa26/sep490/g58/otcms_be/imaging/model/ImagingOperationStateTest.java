package fu.fa26.sep490.g58.otcms_be.imaging.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationState;
import org.junit.jupiter.api.Test;

class ImagingOperationStateTest {
    @Test
    void allowsOnlyTheApprovedForwardTransitions() {
        assertDoesNotThrow(() -> ImagingOperationState.PROCESSING
                .requireTransitionTo(ImagingOperationState.ARCHIVED_NOT_SENT));
        assertDoesNotThrow(() -> ImagingOperationState.PROCESSING
                .requireTransitionTo(ImagingOperationState.QUARANTINED));
        assertDoesNotThrow(() -> ImagingOperationState.ARCHIVED_NOT_SENT
                .requireTransitionTo(ImagingOperationState.CONFIRMATION_PENDING));
        assertDoesNotThrow(() -> ImagingOperationState.CONFIRMATION_PENDING
                .requireTransitionTo(ImagingOperationState.LINKED));
    }

    @Test
    void rejectsFalseLinkageAndTerminalStateMutation() {
        assertThrows(IllegalArgumentException.class, () -> ImagingOperationState.PROCESSING
                .requireTransitionTo(ImagingOperationState.LINKED));
        assertThrows(IllegalArgumentException.class, () -> ImagingOperationState.LINKED
                .requireTransitionTo(ImagingOperationState.PROCESSING));
        assertThrows(IllegalArgumentException.class, () -> ImagingOperationState.FAILED
                .requireTransitionTo(ImagingOperationState.ARCHIVED_NOT_SENT));
    }

    @Test
    void rejectsNullAndSameStateTransitions() {
        assertThrows(IllegalArgumentException.class, () -> ImagingOperationState.PROCESSING
                .requireTransitionTo(ImagingOperationState.PROCESSING));
        assertThrows(IllegalArgumentException.class, () -> ImagingOperationState.PROCESSING
                .requireTransitionTo(null));
    }
}
