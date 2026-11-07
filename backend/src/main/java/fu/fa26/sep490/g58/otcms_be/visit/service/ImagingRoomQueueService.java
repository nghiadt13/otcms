package fu.fa26.sep490.g58.otcms_be.visit.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Room-owned queue admission only. Physical occupancy is deliberately excluded. */
public interface ImagingRoomQueueService {
    record QueueRoom(UUID roomId, String roomCode, String displayLabel) { }
    Map<UUID, List<QueueRoom>> findEligibleRooms(Collection<UUID> serviceIds);
    Map<UUID, QueueRoom> findRooms(Collection<UUID> roomIds);
    void requireEligibleRoom(UUID roomId, UUID serviceId, String imagingType);
}
