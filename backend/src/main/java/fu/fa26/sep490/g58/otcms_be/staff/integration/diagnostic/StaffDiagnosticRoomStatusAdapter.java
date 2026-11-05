package fu.fa26.sep490.g58.otcms_be.staff.integration.diagnostic;

import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.RoomStatusAccessPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.TechnicianRoomAssignment;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffRoomAssignmentQueryService;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Read-only current-shift responsibility. No RoomReservationPort capacity/reservation implementation. */
@Component
public class StaffDiagnosticRoomStatusAdapter implements RoomStatusAccessPort {
    private final StaffRoomAssignmentQueryService assignments;
    private final Clock clock;
    public StaffDiagnosticRoomStatusAdapter(StaffRoomAssignmentQueryService assignments, Clock clock) {
        this.assignments = assignments;
        this.clock = clock;
    }
    @Override public TechnicianRoomAssignment authorizeAssignedTechnician(UUID roomId, UUID actorId) {
        return new TechnicianRoomAssignment(roomId, actorId,
                assignments.isImagingTechnicianAssigned(actorId, roomId, clock.instant()));
    }
}
