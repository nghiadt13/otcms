package fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl;

import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticRequestJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticOrderStatusQueryService;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.LinkedHashMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiagnosticOrderStatusQueryServiceImpl implements DiagnosticOrderStatusQueryService {
    private final DiagnosticRequestJpaRepository requests;
    private final fu.fa26.sep490.g58.otcms_be.visit.service.ImagingRoomQueueService rooms;
    public DiagnosticOrderStatusQueryServiceImpl(DiagnosticRequestJpaRepository requests,
            fu.fa26.sep490.g58.otcms_be.visit.service.ImagingRoomQueueService rooms) { this.requests = requests; this.rooms = rooms; }
    @Override
    @Transactional(readOnly = true)
    public Map<UUID, OrderDiagnosticStatus> findByServiceOrders(Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        var result = new LinkedHashMap<UUID, OrderDiagnosticStatus>();
        var rows = requests.findAllByServiceOrderIdInOrderByCreatedAtDescIdDesc(ids);
        var labels = rooms.findRooms(rows.stream().map(row -> row.getRoomId()).filter(java.util.Objects::nonNull).distinct().toList());
        for (var request : rows) {
            var room = request.getRoomId() == null ? null : labels.get(request.getRoomId());
            result.putIfAbsent(request.getServiceOrderId(), new OrderDiagnosticStatus(request.getId(),
                    request.getStatus().name(), request.getVersion(), request.getRoomId(),
                    room == null ? null : room.roomCode(), room == null ? null : room.displayLabel()));
        }
        return Map.copyOf(result);
    }
}
