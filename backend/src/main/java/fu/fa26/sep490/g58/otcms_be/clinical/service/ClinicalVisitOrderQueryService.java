package fu.fa26.sep490.g58.otcms_be.clinical.service;

import java.util.List;
import java.util.UUID;

/** Internal projection; callers must authorize the visit before invoking it. */
public interface ClinicalVisitOrderQueryService {
    List<Order> findByVisit(UUID visitId);

    record Order(UUID id, String code, String serviceName, String status, String paymentStatus, UUID roomId) { }
}
