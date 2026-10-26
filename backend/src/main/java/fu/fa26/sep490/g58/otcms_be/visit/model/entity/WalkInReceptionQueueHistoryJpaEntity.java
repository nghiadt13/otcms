package fu.fa26.sep490.g58.otcms_be.visit.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "walk_in_reception_queue_history")
public class WalkInReceptionQueueHistoryJpaEntity {
    @Id
    private UUID id;

    @Column(name = "queue_entry_id", nullable = false)
    private UUID queueEntryId;

    @Column(name = "from_status", length = 24)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 24)
    private String toStatus;

    @Column(length = 24)
    private String reason;

    @Column(name = "actor_account_id")
    private UUID actorAccountId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    protected WalkInReceptionQueueHistoryJpaEntity() { }

    public static WalkInReceptionQueueHistoryJpaEntity entered(UUID queueEntryId, UUID actorAccountId,
            Instant occurredAt, String correlationId) {
        WalkInReceptionQueueHistoryJpaEntity entity = new WalkInReceptionQueueHistoryJpaEntity();
        entity.id = UUID.randomUUID();
        entity.queueEntryId = queueEntryId;
        entity.toStatus = "WAITING";
        entity.actorAccountId = actorAccountId;
        entity.occurredAt = occurredAt;
        entity.correlationId = correlationId;
        return entity;
    }
}
