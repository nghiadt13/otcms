package fu.fa26.sep490.g58.otcms_be.visit.mapper;

import fu.fa26.sep490.g58.otcms_be.visit.model.VisitRoom;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.VisitRoomJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.VisitRoomResponse;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.VisitRoomsResponse;
import java.util.List;

public final class VisitRoomMapper {
    private VisitRoomMapper() { }

    public static VisitRoomResponse toResponse(VisitRoom room) {
        return new VisitRoomResponse(room.id(), room.roomCode(), room.displayLabel(), room.type(),
                room.description(), room.capabilities(), room.supportedServiceIds(),
                room.active(), room.createdAt(), room.updatedAt());
    }

    public static VisitRoom toModel(VisitRoomJpaEntity room) {
        return new VisitRoom(room.getId(), room.getRoomCode(), room.getDisplayLabel(),
                room.getType(), room.getDescription(), room.getCapabilities().stream().sorted().toList(),
                room.getSupportedServiceIds().stream().sorted().toList(), room.isActive(),
                room.getCreatedAt(), room.getUpdatedAt());
    }

    public static VisitRoomsResponse toItemsResponse(List<VisitRoom> rooms) {
        return new VisitRoomsResponse(rooms.stream().map(VisitRoomMapper::toResponse).toList());
    }
}
