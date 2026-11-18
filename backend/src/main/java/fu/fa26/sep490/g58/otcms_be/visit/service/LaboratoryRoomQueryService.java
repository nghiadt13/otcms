package fu.fa26.sep490.g58.otcms_be.visit.service;

import java.util.List;
import java.util.UUID;

/** The visit feature owns room configuration and laboratory room eligibility. */
public interface LaboratoryRoomQueryService {
    record Room(UUID id, String code, String label) { }
    List<Room> eligibleRooms(UUID serviceId);
    boolean isEligibleRoom(UUID roomId, UUID serviceId);
}
