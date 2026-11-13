package fu.fa26.sep490.g58.otcms_be.common.audit.model;

import fu.fa26.sep490.g58.otcms_be.common.audit.AuditEntry;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(name = "audit_logs")
@Getter
public class AuditLogJpaEntity {
    @Id private UUID id;
    @Column(nullable = false) private Instant occurredAt;
    @Column(nullable = false, length = 64) private String source;
    @Column(nullable = false, length = 32) private String category;
    @Column(nullable = false, length = 64) private String action;
    @Column(nullable = false, length = 48) private String recordType;
    private UUID entityId;
    @Column(columnDefinition = "text") private String recordLabel;
    @Column(columnDefinition = "text") private String correctionReason;
    @Column(nullable = false) private boolean visibleInActivityLog;
    private UUID actorAccountId;
    @Column(length = 32) private String actorType;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private String beforeState;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private String afterState;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private String metadata;
    @Column(length = 128) private String correlationId;
    @Column(length = 64) private String clientIp;
    @Column(columnDefinition = "text") private String userAgent;
    @Column(length = 255) private String identifierHmac;
    @Column(length = 64) private String outcome;
    @Column(length = 200) private String dedupKey;

    protected AuditLogJpaEntity() { }

    public AuditLogJpaEntity(AuditEntry entry, Instant occurredAt, String correlationId,
            String beforeState, String afterState, String metadata) {
        id = UUID.randomUUID();
        this.occurredAt = occurredAt;
        source = entry.source(); category = entry.category(); action = entry.action(); recordType = entry.recordType();
        entityId = entry.entityId(); recordLabel = entry.recordLabel(); correctionReason = entry.correctionReason();
        visibleInActivityLog = entry.visibleInActivityLog(); actorAccountId = entry.actorAccountId();
        actorType = entry.actorType() == null && actorAccountId != null ? "ACCOUNT" : entry.actorType();
        this.beforeState = beforeState; this.afterState = afterState; this.metadata = metadata;
        this.correlationId = correlationId;
        clientIp = entry.clientIp(); userAgent = entry.userAgent(); identifierHmac = entry.identifierHmac();
        outcome = entry.outcome(); dedupKey = entry.dedupKey();
    }
}
