package fu.fa26.sep490.g58.otcms_be.servicecatalog.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "service_audits")
public class ServiceCatalogManagementAuditJpaEntity {
    @Id private UUID id;
    @Column(name="service_id", nullable=false) private UUID serviceId;
    @Column(name="actor_account_id", nullable=false) private UUID actorAccountId;
    @Column(name="resource_type", nullable=false) private String resourceType;
    @Column(name="resource_id", nullable=false) private UUID resourceId;
    @Column(nullable=false) private String action;
    @Column(name="before_state", columnDefinition="jsonb") private String beforeState;
    @Column(name="after_state", nullable=false, columnDefinition="jsonb") private String afterState;
    @Column(name="occurred_at", nullable=false) private Instant occurredAt;
    @Column(name="correlation_id", nullable=false) private String correlationId;
    protected ServiceCatalogManagementAuditJpaEntity() { }
    public ServiceCatalogManagementAuditJpaEntity(UUID id, UUID serviceId, UUID actorAccountId, String resourceType,
            UUID resourceId, String action, String beforeState, String afterState, Instant occurredAt, String correlationId) {
        this.id=id; this.serviceId=serviceId; this.actorAccountId=actorAccountId; this.resourceType=resourceType;
        this.resourceId=resourceId; this.action=action; this.beforeState=beforeState; this.afterState=afterState;
        this.occurredAt=occurredAt; this.correlationId=correlationId;
    }
}
