package fu.fa26.sep490.g58.otcms_be.clinical.service.impl;

import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalServiceType;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ServiceOrderStatus;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.ServiceOrderRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.LaboratoryRoomQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.service.ServiceAdmissionHandler;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Laboratory part of automatic service-room admission after payment (UC45, BR-27, BR-48): selects eligible
 * laboratory rooms and assigns the room on the paid laboratory service order.
 */
@Component
public class LaboratoryServiceAdmissionHandler implements ServiceAdmissionHandler {
    private final ServiceOrderRepository orders;
    private final LaboratoryRoomQueryService rooms;
    private final Clock clock;

    public LaboratoryServiceAdmissionHandler(ServiceOrderRepository orders, LaboratoryRoomQueryService rooms,
            Clock clock) {
        this.orders = orders;
        this.rooms = rooms;
        this.clock = clock;
    }

    @Override
    public boolean supports(String serviceType) {
        return "LABORATORY".equals(serviceType);
    }

    @Override
    public List<UUID> eligibleRoomIds(UUID serviceOrderId) {
        return orders.findById(serviceOrderId)
                .filter(order -> order.getServiceType() == ClinicalServiceType.LABORATORY
                        && order.getOrderStatus() == ServiceOrderStatus.ORDERED)
                .map(order -> rooms.eligibleRooms(order.getServiceId()).stream()
                        .map(LaboratoryRoomQueryService.Room::id).toList())
                .orElse(List.of());
    }

    @Override
    public void assignRoom(UUID serviceOrderId, UUID roomId) {
        orders.assignLaboratoryRoom(serviceOrderId, roomId, clock.instant());
    }
}
