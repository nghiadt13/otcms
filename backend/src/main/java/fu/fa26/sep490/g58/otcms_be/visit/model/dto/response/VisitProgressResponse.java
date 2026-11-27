package fu.fa26.sep490.g58.otcms_be.visit.model.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Tiến độ một lượt khám. `queueStage`/`queueNumber`/`peopleAhead`/`currentRoom` là hàng đang chờ gần nhất
 * (cho thẻ tóm tắt). Màn chi tiết dùng hai khối tách riêng, không phải đoán: `examQueue` là hàng khám bệnh,
 * `ServiceOrder.queue` là hàng của từng chỉ định (null khi chưa vào hàng).
 */
public record VisitProgressResponse(UUID visitId, String source, String status, String currentStage,
        Instant arrivedAt, Integer queuePosition, String currentRoom, Integer queueNumber, Integer peopleAhead,
        String queueStage,
        boolean beingCalled, List<ServiceOrder> serviceOrders, QueueInfo examQueue) {
    public VisitProgressResponse(UUID visitId, String source, String status, String currentStage,
            Instant arrivedAt, Integer queuePosition, String currentRoom) {
        this(visitId, source, status, currentStage, arrivedAt, queuePosition, currentRoom, null,
                queuePosition == null ? null : queuePosition - 1, null, false, List.of(), null);
    }

    /** Một chỉ định kèm vị trí trong hàng của phòng thực hiện (`queue` null khi chưa xếp hàng). */
    public record ServiceOrder(UUID orderId, String orderCode, String serviceName, String status,
            String paymentStatus, UUID roomId, String roomName, QueueInfo queue) { }

    /** Vị trí trong một hàng phòng: số thứ tự, trạng thái (WAITING, CALLED, IN_EXAMINATION), phòng, số người trước. */
    public record QueueInfo(Integer queueNumber, String status, String roomCode, int peopleAhead,
            boolean beingCalled) { }
}
