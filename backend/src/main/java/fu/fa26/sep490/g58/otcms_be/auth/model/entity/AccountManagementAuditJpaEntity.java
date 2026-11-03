package fu.fa26.sep490.g58.otcms_be.auth.model.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Safe management evidence only; credentials and personal data never enter this record. */
@Entity
@Table(name = "account_management_audits")
@Getter
public class AccountManagementAuditJpaEntity {
    @Id private UUID id;
    @Column(name = "account_id", nullable = false) private UUID accountId;
    @Column(name = "actor_account_id", nullable = false) private UUID actorAccountId;
    @Column(nullable = false, length = 24) private String action;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    @Column(name = "correlation_id", nullable = false, length = 64) private String correlationId;

    protected AccountManagementAuditJpaEntity() { }

    public AccountManagementAuditJpaEntity(UUID accountId, UUID actorAccountId, String action,
            Instant occurredAt, String correlationId) {
        this.id = UUID.randomUUID();
        this.accountId = accountId;
        this.actorAccountId = actorAccountId;
        this.action = action;
        this.occurredAt = occurredAt;
        this.correlationId = correlationId;
    }
}
