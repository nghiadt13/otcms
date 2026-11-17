package fu.fa26.sep490.g58.otcms_be.clinical.service.impl;

import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderRoomService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Room moves for every service type. A dispatched imaging order carries its room in the execution columns and goes
 * through the guarded execution write (version +1); any other order only has {@code room_id}.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class ClinicalServiceOrderRoomServiceImpl implements ClinicalServiceOrderRoomService {
    private static final String LOCK = """
            select o.id, o.visit_id, o.service_id, o.service_type, o.imaging_type, o.room_id, o.order_status,
                   o.execution_status, o.execution_version
            from clinical_service_orders o where o.id = :id for update
            """;
    private final EntityManager entityManager;
    private final ClinicalServiceOrderExecutionPort executions;

    public ClinicalServiceOrderRoomServiceImpl(EntityManager entityManager, ClinicalServiceOrderExecutionPort executions) {
        this.entityManager = entityManager;
        this.executions = executions;
    }

    @Override
    public Optional<OrderRoom> lockForTransfer(UUID orderId) {
        entityManager.flush();
        var rows = entityManager.createNativeQuery(LOCK, Tuple.class).setParameter("id", orderId).getResultList();
        if (rows.isEmpty()) return Optional.empty();
        Tuple row = (Tuple) rows.getFirst();
        UUID roomId = row.get("room_id", UUID.class);
        String execution = row.get("execution_status", String.class);
        boolean movable = "ORDERED".equals(row.get("order_status", String.class)) && roomId != null
                && (execution == null || "PENDING".equals(execution));
        return Optional.of(new OrderRoom(row.get("id", UUID.class), row.get("visit_id", UUID.class),
                row.get("service_id", UUID.class), row.get("service_type", String.class),
                row.get("imaging_type", String.class), roomId, movable));
    }

    @Override
    public boolean changeRoom(UUID orderId, UUID roomId) {
        entityManager.flush();
        var rows = entityManager.createNativeQuery(LOCK, Tuple.class).setParameter("id", orderId).getResultList();
        if (rows.isEmpty()) return false;
        Tuple row = (Tuple) rows.getFirst();
        String execution = row.get("execution_status", String.class);
        if (!"ORDERED".equals(row.get("order_status", String.class)) || row.get("room_id", UUID.class) == null
                || (execution != null && !"PENDING".equals(execution))) return false;
        if (execution != null) {
            long version = ((Number) row.get("execution_version")).longValue();
            return executions.transferRoom(orderId, version, roomId, "reception-transfer", sha256(orderId + ":" + roomId)) == 1;
        }
        return entityManager.createNativeQuery("""
                update clinical_service_orders set room_id = :roomId, updated_at = clock_timestamp()
                where id = :id and order_status = 'ORDERED' and execution_status is null
                """).setParameter("roomId", roomId).setParameter("id", orderId).executeUpdate() == 1;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
