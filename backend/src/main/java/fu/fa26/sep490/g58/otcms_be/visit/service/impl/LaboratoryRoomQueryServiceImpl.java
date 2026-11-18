package fu.fa26.sep490.g58.otcms_be.visit.service.impl;

import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitRoomJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.LaboratoryRoomQueryService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LaboratoryRoomQueryServiceImpl implements LaboratoryRoomQueryService {
    private final VisitRoomJpaRepository rooms;

    public LaboratoryRoomQueryServiceImpl(VisitRoomJpaRepository rooms) { this.rooms = rooms; }

    @Override public List<Room> eligibleRooms(UUID serviceId) {
        if (serviceId == null) return List.of();
        return rooms.findLaboratoryRoomsForService(serviceId).stream()
                .filter(room -> room.getCapabilities().contains("LAB_ROOM"))
                .map(room -> new Room(room.getId(), room.getRoomCode(), room.getDisplayLabel())).toList();
    }

    @Override public boolean isEligibleRoom(UUID roomId, UUID serviceId) {
        return roomId != null && eligibleRooms(serviceId).stream().anyMatch(room -> room.id().equals(roomId));
    }
}
