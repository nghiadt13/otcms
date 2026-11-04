package fu.fa26.sep490.g58.otcms_be.diagnostic.integration;

import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.TechnicianRoomAssignment;
import java.util.UUID;

/** Room-owned assignment check used before exposing operational request status to a Technician. */
public interface RoomStatusAccessPort {
    TechnicianRoomAssignment authorizeAssignedTechnician(UUID roomId, UUID actorId);
}
