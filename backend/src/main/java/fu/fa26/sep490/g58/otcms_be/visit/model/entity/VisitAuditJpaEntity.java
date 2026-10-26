package fu.fa26.sep490.g58.otcms_be.visit.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "visit_audits")
public class VisitAuditJpaEntity {
    @Id
    private UUID id;

    @Column(name = "actor_account_id", nullable = false)
    private UUID actorAccountId;

    @Column(name = "visit_id", nullable = false)
    private UUID visitId;

    @Column(nullable = false, length = 32)
    private String action;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "correlation_id", nullable = false, length = 128)
    private String correlationId;

    protected VisitAuditJpaEntity() { }

    public static VisitAuditJpaEntity walkInCreated(UUID actorAccountId, UUID visitId, Instant occurredAt,
            String correlationId) {
        VisitAuditJpaEntity entity = new VisitAuditJpaEntity();
        entity.id = UUID.randomUUID();
        entity.actorAccountId = actorAccountId;
        entity.visitId = visitId;
        entity.action = "WALK_IN_CREATED";
        entity.occurredAt = occurredAt;
        entity.correlationId = correlationId;
        return entity;
    }
}
