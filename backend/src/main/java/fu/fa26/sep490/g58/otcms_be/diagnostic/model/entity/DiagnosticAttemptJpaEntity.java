package fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "diagnostic_attempts", uniqueConstraints = {
    @UniqueConstraint(name = "uq_diagnostic_attempts_request_number", columnNames = {"request_id", "attempt_number"}),
    @UniqueConstraint(name = "uq_diagnostic_attempts_first_use_event", columnNames = "first_use_event_id")
})
public class DiagnosticAttemptJpaEntity {
    @Id
    private UUID id;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "first_use_event_id", nullable = false)
    private UUID firstUseEventId;

    @Column(name = "first_use_type", nullable = false, length = 32)
    private String firstUseType;

    @Column(name = "source_id", nullable = false, length = 128)
    private String sourceId;

    @Column(name = "actor_account_id")
    private UUID actorAccountId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "recorded_at", nullable = false, insertable = false, updatable = false)
    private Instant recordedAt;

    @Column(name = "study_ref", columnDefinition = "text")
    private String studyRef;

    protected DiagnosticAttemptJpaEntity() { }

    public DiagnosticAttemptJpaEntity(UUID id, UUID requestId, int attemptNumber, UUID firstUseEventId,
            String firstUseType, String sourceId, UUID actorAccountId, Instant occurredAt, String studyRef) {
        this.id = id;
        this.requestId = requestId;
        this.attemptNumber = attemptNumber;
        this.firstUseEventId = firstUseEventId;
        this.firstUseType = firstUseType;
        this.sourceId = sourceId;
        this.actorAccountId = actorAccountId;
        this.occurredAt = occurredAt;
        this.studyRef = studyRef;
    }

    public UUID getId() { return id; }
    public UUID getRequestId() { return requestId; }
    public int getAttemptNumber() { return attemptNumber; }
    public UUID getFirstUseEventId() { return firstUseEventId; }
    public String getFirstUseType() { return firstUseType; }
    public String getSourceId() { return sourceId; }
    public UUID getActorAccountId() { return actorAccountId; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getRecordedAt() { return recordedAt; }
    public String getStudyRef() { return studyRef; }

    public void setStudyRef(String studyRef) { this.studyRef = studyRef; }
}
