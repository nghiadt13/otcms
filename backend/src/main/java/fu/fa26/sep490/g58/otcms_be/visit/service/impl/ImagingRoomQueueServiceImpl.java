package fu.fa26.sep490.g58.otcms_be.visit.service.impl;

import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicServiceType;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.GetClinicServiceCatalogService;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffRoomAssignmentQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitRoomJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.ImagingRoomQueueService;
import java.time.Clock;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ImagingRoomQueueServiceImpl implements ImagingRoomQueueService {
    private static final Set<String> MODALITIES = Set.of("XRAY", "MRI", "CT", "ULTRASOUND");
    private final VisitRoomJpaRepository rooms;
    private final StaffRoomAssignmentQueryService staff;
    private final GetClinicServiceCatalogService catalog;
    private final Clock clock;

    public ImagingRoomQueueServiceImpl(VisitRoomJpaRepository rooms, StaffRoomAssignmentQueryService staff,
            GetClinicServiceCatalogService catalog, Clock clock) {
        this.rooms = rooms; this.staff = staff; this.catalog = catalog; this.clock = clock;
    }

    @Override
    public Map<UUID, List<QueueRoom>> findEligibleRooms(Collection<UUID> serviceIds) {
        if (serviceIds.isEmpty()) return Map.of();
        var services = catalog.imagingQueueServices(serviceIds);
        if (!services.keySet().containsAll(serviceIds)) throw new VisitOperationException("SERVICE_NOT_FOUND");
        var eligible = services.values().stream().filter(service -> service.active() && service.type() == ClinicServiceType.IMAGING
                && service.modality() != null && MODALITIES.contains(service.modality()))
                .map(service -> service.serviceId()).collect(Collectors.toSet());
        if (eligible.isEmpty()) return Map.of();
        var rows = rooms.findImagingQueueRooms(eligible).stream()
                .filter(row -> eligible.contains(row.getServiceId()) && row.getActive() && "IMAGING".equals(row.getType())
                        && row.getImagingCapability()).toList();
        var staffedRooms = staff.roomsWithActiveImagingTechnician(rows.stream().map(VisitRoomJpaRepository.ImagingQueueRoomRow::getRoomId)
                .distinct().toList(), clock.instant());
        Map<UUID, List<QueueRoom>> result = new LinkedHashMap<>();
        for (var row : rows) {
            if (staffedRooms.contains(row.getRoomId())) result.computeIfAbsent(row.getServiceId(), ignored -> new ArrayList<>())
                    .add(new QueueRoom(row.getRoomId(), row.getRoomCode(), row.getDisplayLabel()));
        }
        result.replaceAll((id, values) -> List.copyOf(values));
        return Map.copyOf(result);
    }

    @Override
    public Map<UUID, QueueRoom> findRooms(Collection<UUID> roomIds) {
        if (roomIds.isEmpty()) return Map.of();
        return rooms.findAllById(roomIds).stream().collect(Collectors.toUnmodifiableMap(room -> room.getId(),
                room -> new QueueRoom(room.getId(), room.getRoomCode(), room.getDisplayLabel())));
    }

    @Override
    public void requireEligibleRoom(UUID roomId, UUID serviceId, String imagingType) {
        if (roomId == null || serviceId == null || imagingType == null) throw new VisitOperationException("VALIDATION_FAILED");
        var service = catalog.getService(serviceId);
        if (service == null || !service.active() || service.type() != ClinicServiceType.IMAGING
                || service.modality() == null || !MODALITIES.contains(service.modality()) || !imagingType.equals(service.modality())) {
            throw new VisitOperationException("ROOM_NOT_FOUND");
        }
        if (findEligibleRooms(List.of(serviceId)).getOrDefault(serviceId, List.of()).stream()
                .noneMatch(room -> room.roomId().equals(roomId))) throw new VisitOperationException("ROOM_NOT_FOUND");
    }
}
