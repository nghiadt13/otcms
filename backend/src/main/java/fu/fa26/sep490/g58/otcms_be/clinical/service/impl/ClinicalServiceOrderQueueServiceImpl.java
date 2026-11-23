package fu.fa26.sep490.g58.otcms_be.clinical.service.impl;

import fu.fa26.sep490.g58.otcms_be.clinical.repository.ServiceOrderRepository;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderQueueService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Native read of the order columns the queues need (room and execution status are not on the JPA entity). */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class ClinicalServiceOrderQueueServiceImpl implements ClinicalServiceOrderQueueService {
    private static final String SELECT = """
            select o.id, o.order_code, o.visit_id, o.patient_id, o.service_id, o.service_name_snapshot, o.service_type,
                   o.instructions, o.body_area, o.clinical_reason, o.requesting_doctor_id, o.order_status,
                   o.payment_status, o.room_id, e.execution_status, r.ordered_at
            from service_orders o
            join service_order_rounds r on r.id = o.ordering_round_id
            left join imaging_executions e on e.service_order_id = o.id
            """;
    private final EntityManager entityManager;
    private final ServiceOrderRepository orders;
    private final Clock clock;

    public ClinicalServiceOrderQueueServiceImpl(EntityManager entityManager, ServiceOrderRepository orders, Clock clock) {
        this.entityManager = entityManager;
        this.orders = orders;
        this.clock = clock;
    }

    @Override
    public Optional<QueueOrder> find(UUID orderId) {
        return query(" where o.id = :id", "id", orderId).stream().findFirst();
    }

    @Override
    public List<QueueOrder> findAll(Collection<UUID> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) return List.of();
        return query(" where o.id in (:ids)", "ids", orderIds);
    }

    @Override
    public List<QueueOrder> findByVisit(UUID visitId) {
        return query(" where o.visit_id = :id order by r.ordered_at, o.order_code, o.id", "id", visitId);
    }

    @Override
    public void markPaid(UUID orderId) {
        orders.findById(orderId).ifPresent(order -> {
            order.markPaid(clock.instant());
            orders.saveAndFlush(order);
        });
    }

    @SuppressWarnings("unchecked")
    private List<QueueOrder> query(String clause, String parameter, Object value) {
        entityManager.flush();
        List<Tuple> rows = entityManager.createNativeQuery(SELECT + clause, Tuple.class)
                .setParameter(parameter, value).getResultList();
        return rows.stream().map(ClinicalServiceOrderQueueServiceImpl::map).toList();
    }

    /** Native timestamptz columns come back as Instant, OffsetDateTime or Timestamp depending on the driver path. */
    private static Instant instant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof java.time.OffsetDateTime offset) return offset.toInstant();
        throw new IllegalStateException("Unexpected timestamp type " + value.getClass());
    }

    private static QueueOrder map(Tuple row) {
        return new QueueOrder(row.get("id", UUID.class), row.get("order_code", String.class),
                row.get("visit_id", UUID.class), row.get("patient_id", UUID.class), row.get("service_id", UUID.class),
                row.get("service_name_snapshot", String.class), row.get("service_type", String.class),
                row.get("instructions", String.class), row.get("body_area", String.class),
                row.get("clinical_reason", String.class), row.get("requesting_doctor_id", UUID.class),
                row.get("order_status", String.class), row.get("payment_status", String.class),
                row.get("room_id", UUID.class), row.get("execution_status", String.class),
                instant(row.get("ordered_at")));
    }
}
