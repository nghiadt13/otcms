package fu.fa26.sep490.g58.otcms_be.diagnostic.service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Internal minimal projection. Caller must authorize the Clinical records before selecting IDs. */
public interface DiagnosticOrderStatusQueryService {
    Map<UUID, OrderDiagnosticStatus> findByServiceOrders(Collection<UUID> orderIds);
    record OrderDiagnosticStatus(UUID requestId, String status, long version, UUID roomId, String roomCode, String displayLabel) { }
}
