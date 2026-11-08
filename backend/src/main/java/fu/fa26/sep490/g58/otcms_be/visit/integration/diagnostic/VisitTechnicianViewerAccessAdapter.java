package fu.fa26.sep490.g58.otcms_be.visit.integration.diagnostic;

import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.TechnicianRoomAssignment;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.RoomStatusAccessPort;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffShiftAdminService;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Visit-owned adapter for checking the technician's live room assignment before issuing a viewer grant. */
@Component
@Primary
public final class VisitTechnicianViewerAccessAdapter implements RoomStatusAccessPort {
    private final StaffShiftAdminService shifts;
    private final Clock clock;

    public VisitTechnicianViewerAccessAdapter(StaffShiftAdminService shifts, Clock clock) {
        this.shifts = shifts;
        this.clock = clock;
    }

    @Override
    public TechnicianRoomAssignment authorizeAssignedTechnician(UUID roomId, UUID actorId) {
        boolean active = shifts.isActiveImagingTechnicianAssignedToRoom(actorId, roomId, clock.instant());
        return new TechnicianRoomAssignment(roomId, actorId, active);
    }
}
