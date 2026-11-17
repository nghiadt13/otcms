package fu.fa26.sep490.g58.otcms_be.visit.model.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record VisitProgressResponse(UUID visitId, String source, String status, String currentStage,
        Instant arrivedAt, Integer queuePosition, String currentRoom, Integer queueNumber, Integer peopleAhead,
        boolean beingCalled, List<ServiceOrder> serviceOrders) {
    public VisitProgressResponse(UUID visitId, String source, String status, String currentStage,
            Instant arrivedAt, Integer queuePosition, String currentRoom) {
        this(visitId, source, status, currentStage, arrivedAt, queuePosition, currentRoom, null,
                queuePosition == null ? null : queuePosition - 1, false, List.of());
    }

    public record ServiceOrder(UUID orderId, String orderCode, String serviceName, String status,
            String paymentStatus, UUID roomId, String roomName) { }
}
