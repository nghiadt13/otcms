package fu.fa26.sep490.g58.otcms_be.diagnostic.integration;

import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.RoomEvidence;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.RoomReservation;
import java.util.UUID;

/** Room-owned capacity check and reservation; implementation must enforce owner locking. */
public interface RoomReservationPort {
    RoomEvidence requireEligibleImagingRoom(UUID roomId, String imagingType);

    RoomReservation reserveImagingCapacity(UUID requestId, UUID roomId, String imagingType,
            long expectedRoomVersion);

    /** Owner must report a real reservation; a non-null request room is only queue assignment. */
    boolean hasImagingCapacityReservation(UUID requestId);

    void releaseImagingCapacity(UUID requestId, String cause);
}
