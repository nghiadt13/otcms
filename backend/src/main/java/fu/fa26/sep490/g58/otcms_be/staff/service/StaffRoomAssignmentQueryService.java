package fu.fa26.sep490.g58.otcms_be.staff.service;

import java.time.Instant;
import java.util.UUID;

/** Trusted responsibility evidence from existing V040 work shifts. Does not grant imaging mutation rights. */
public interface StaffRoomAssignmentQueryService {
    java.util.Set<UUID> roomsWithActiveImagingTechnician(java.util.Collection<UUID> roomIds, Instant at);

    boolean isImagingTechnicianAssigned(UUID accountId, UUID roomId, Instant at);
}
