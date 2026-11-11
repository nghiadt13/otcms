package fu.fa26.sep490.g58.otcms_be.visit.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "room_incident_requests")
public class RoomIncidentJpaEntity {
    @Id private UUID id;
    @Column(name = "room_id", nullable = false) private UUID roomId;
    @Column(name = "diagnostic_request_id", nullable = false) private UUID diagnosticRequestId;
    @Column(name = "visit_id", nullable = false) private UUID visitId;
    @Column(name = "reported_by_account_id", nullable = false) private UUID reportedByAccountId;
    @Column(name = "report_idempotency_key", nullable = false) private String reportIdempotencyKey;
    @Column(name = "report_fingerprint", nullable = false, length = 64, columnDefinition = "char(64)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private String reportFingerprint;
    @Column(name = "incident_type", nullable = false) private String incidentType;
    @Column(nullable = false, columnDefinition = "text") private String description;
    @Column(nullable = false, length = 16) private String status;
    @Column(name = "decided_by_account_id") private UUID decidedByAccountId;
    @Column(name = "decision_reason", columnDefinition = "text") private String decisionReason;
    @Column(name = "reported_at", nullable = false, insertable = false, updatable = false) private Instant reportedAt;
    @Column(name = "decided_at") private Instant decidedAt;
    @Column(name = "restored_at") private Instant restoredAt;
    @Version @Column(nullable = false) private long version;

    protected RoomIncidentJpaEntity() { }

    public RoomIncidentJpaEntity(UUID id, UUID roomId, UUID requestId, UUID visitId, UUID reporterId,
            String reportKey, String fingerprint, String incidentType, String description) {
        this.id = id;
        this.roomId = roomId;
        this.diagnosticRequestId = requestId;
        this.visitId = visitId;
        this.reportedByAccountId = reporterId;
        this.reportIdempotencyKey = reportKey;
        this.reportFingerprint = fingerprint;
        this.incidentType = incidentType;
        this.description = description;
        this.status = "REPORTED";
    }

    public void decide(UUID actorId, String decision, String reason, Instant now) {
        if (!"REPORTED".equals(status)) throw new IllegalStateException("INCIDENT_ALREADY_DECIDED");
        status = decision;
        decidedByAccountId = actorId;
        decisionReason = reason;
        decidedAt = now;
    }

    public void restore(UUID actorId, String reason, Instant now) {
        if (!"CONFIRMED".equals(status)) throw new IllegalStateException("INCIDENT_NOT_CONFIRMED");
        status = "RESTORED";
        decidedByAccountId = actorId;
        decisionReason = reason;
        restoredAt = now;
    }

    public UUID getId() { return id; }
    public UUID getRoomId() { return roomId; }
    public UUID getDiagnosticRequestId() { return diagnosticRequestId; }
    public UUID getVisitId() { return visitId; }
    public UUID getReportedByAccountId() { return reportedByAccountId; }
    public String getReportIdempotencyKey() { return reportIdempotencyKey; }
    public String getReportFingerprint() { return reportFingerprint; }
    public String getIncidentType() { return incidentType; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
    public UUID getDecidedByAccountId() { return decidedByAccountId; }
    public String getDecisionReason() { return decisionReason; }
    public Instant getReportedAt() { return reportedAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public long getVersion() { return version; }
}
