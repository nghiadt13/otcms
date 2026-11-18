package fu.fa26.sep490.g58.otcms_be.visit.service;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.visit.model.VisitRoom;
import java.util.List;
import java.util.UUID;

/** UC-64: rooms relevant to the signed-in staff member's work, never the whole inventory for non-admin roles. */
public interface StaffRoomScopeService {
    /**
     * ADMIN and OWNER: every room (also inactive). RECEPTIONIST: every active room. DOCTOR: rooms of today's shifts and
     * rooms of the service orders of the doctor's open visits. IMAGING_TECHNICIAN: rooms assigned in the current shift.
     * LAB_TECHNICIAN: active laboratory rooms. Other roles get MSG-40.
     */
    List<VisitRoom> listVisible(AuthenticatedAccount actor);

    /** One room of {@link #listVisible}; unknown or outside the actor's scope is MSG-50. */
    VisitRoom getVisible(AuthenticatedAccount actor, UUID roomId);
}
