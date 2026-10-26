package fu.fa26.sep490.g58.otcms_be.visit.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "walk_in_reception_queue_entries")
public class WalkInReceptionQueueEntryJpaEntity {
    @Id
    private UUID id;

    @Column(name = "visit_id", nullable = false)
    private UUID visitId;

    @Column(name = "arrived_at", nullable = false)
    private Instant arrivedAt;

    @Column(nullable = false, length = 24)
    private String status;

    @Column(name = "entered_at", nullable = false)
    private Instant enteredAt;

    @Column(name = "left_at")
    private Instant leftAt;

    @Column(name = "exit_reason", length = 24)
    private String exitReason;

    protected WalkInReceptionQueueEntryJpaEntity() { }

    public static WalkInReceptionQueueEntryJpaEntity waiting(UUID visitId, Instant arrivedAt, Instant enteredAt) {
        WalkInReceptionQueueEntryJpaEntity entity = new WalkInReceptionQueueEntryJpaEntity();
        entity.id = UUID.randomUUID();
        entity.visitId = visitId;
        entity.arrivedAt = arrivedAt;
        entity.status = "WAITING";
        entity.enteredAt = enteredAt;
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getVisitId() { return visitId; }
    public Instant getArrivedAt() { return arrivedAt; }
    public String getStatus() { return status; }
    public Instant getEnteredAt() { return enteredAt; }
    public Instant getLeftAt() { return leftAt; }
    public String getExitReason() { return exitReason; }
}
