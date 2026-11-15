package fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl;

import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticOrderStatusQueryService;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.LinkedHashMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiagnosticOrderStatusQueryServiceImpl implements DiagnosticOrderStatusQueryService {
    private final ClinicalServiceOrderExecutionPort requests;
    private final fu.fa26.sep490.g58.otcms_be.visit.service.ImagingRoomQueueService rooms;
    public DiagnosticOrderStatusQueryServiceImpl(ClinicalServiceOrderExecutionPort requests,
            fu.fa26.sep490.g58.otcms_be.visit.service.ImagingRoomQueueService rooms) { this.requests = requests; this.rooms = rooms; }
    @Override
    @Transactional(readOnly = true)
    public Map<UUID, OrderDiagnosticStatus> findByServiceOrders(Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        var result = new LinkedHashMap<UUID, OrderDiagnosticStatus>();
        var rows = requests.findAllByOrderIds(ids);
        var labels = rooms.findRooms(rows.stream().map(row -> row.roomId()).filter(java.util.Objects::nonNull).distinct().toList());
        for (var request : rows) {
            var room = request.roomId() == null ? null : labels.get(request.roomId());
            result.putIfAbsent(request.orderId(), new OrderDiagnosticStatus(request.orderId(),
                    request.status().name(), request.executionVersion(), request.roomId(),
                    room == null ? null : room.roomCode(), room == null ? null : room.displayLabel()));
        }
        return Map.copyOf(result);
    }
}
