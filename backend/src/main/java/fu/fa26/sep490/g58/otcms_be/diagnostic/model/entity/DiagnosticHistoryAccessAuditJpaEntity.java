package fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticHistoryScope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "diagnostic_history_access_audits")
public class DiagnosticHistoryAccessAuditJpaEntity {
    @Id
    private UUID id;

    @Column(name = "actor_account_id", nullable = false, updatable = false)
    private UUID actorAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, updatable = false, length = 16)
    private DiagnosticHistoryScope scopeType;

    @Column(name = "scope_id", nullable = false, updatable = false)
    private UUID scopeId;

    @Column(name = "page_number", nullable = false, updatable = false)
    private int pageNumber;

    @Column(name = "page_size", nullable = false, updatable = false)
    private int pageSize;

    @Column(name = "returned_count", nullable = false, updatable = false)
    private int returnedCount;

    @Column(name = "recorded_at", nullable = false, insertable = false, updatable = false)
    private Instant recordedAt;

    protected DiagnosticHistoryAccessAuditJpaEntity() { }

    public DiagnosticHistoryAccessAuditJpaEntity(UUID id, UUID actorAccountId, DiagnosticHistoryScope scopeType,
            UUID scopeId, int pageNumber, int pageSize, int returnedCount) {
        this.id = id;
        this.actorAccountId = actorAccountId;
        this.scopeType = scopeType;
        this.scopeId = scopeId;
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
        this.returnedCount = returnedCount;
    }

    public UUID getId() { return id; }
    public UUID getActorAccountId() { return actorAccountId; }
    public DiagnosticHistoryScope getScopeType() { return scopeType; }
    public UUID getScopeId() { return scopeId; }
    public int getPageNumber() { return pageNumber; }
    public int getPageSize() { return pageSize; }
    public int getReturnedCount() { return returnedCount; }
    public Instant getRecordedAt() { return recordedAt; }
}
