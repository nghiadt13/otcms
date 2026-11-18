package fu.fa26.sep490.g58.otcms_be.clinical.service;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Order facts the room queues need (phase 08), for every service type. Joins the caller's transaction. The visit
 * package reads orders only through this interface, never through the order table.
 */
public interface ClinicalServiceOrderQueueService {
    /**
     * @param roomId          room assigned to the order, null while none
     * @param executionStatus imaging execution status (PENDING, IN_PROGRESS, ...), null for other service types or
     *                        before dispatch
     */
    record QueueOrder(UUID orderId, String orderCode, UUID visitId, UUID patientId, UUID serviceId,
            String serviceName, String serviceType, String instructions, String bodyArea, String clinicalReason,
            UUID requestingDoctorProfileId, String orderStatus, String paymentStatus, UUID roomId,
            String executionStatus, Instant orderedAt) { }

    Optional<QueueOrder> find(UUID orderId);

    List<QueueOrder> findAll(Collection<UUID> orderIds);

    /** Orders of the visit, oldest first (ordered_at, order_code). */
    List<QueueOrder> findByVisit(UUID visitId);

    /**
     * Marks the order's payment status PAID after billing confirmed its fee item (per-item payment, D7). Idempotent;
     * a cancelled order stays as it is.
     */
    void markPaid(UUID orderId);
}
