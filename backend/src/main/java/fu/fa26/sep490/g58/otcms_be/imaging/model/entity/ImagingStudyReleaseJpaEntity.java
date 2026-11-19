package fu.fa26.sep490.g58.otcms_be.imaging.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

/** Confirmation and release bookkeeping of one imaging study; shares the study id (imaging_study_releases, 1 - 0..1). */
@Entity
@Table(name = "imaging_study_releases")
public class ImagingStudyReleaseJpaEntity {
    @Id
    @Column(name = "imaging_study_id")
    private UUID imagingStudyId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "imaging_study_id")
    private ImagingStudyJpaEntity study;

    @Column(name = "confirmation_event_id")
    private UUID confirmationEventId;

    @Column(name = "confirmation_attempt_count", nullable = false)
    private int confirmationAttemptCount;

    @Column(name = "next_confirmation_attempt_at")
    private Instant nextConfirmationAttemptAt;

    @Column(name = "last_safe_error_code", length = 64)
    private String lastSafeErrorCode;

    @Column(name = "release_event_id")
    private UUID releaseEventId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "release_payload_sha256", columnDefinition = "char(64)")
    private String releasePayloadSha256;

    @Column(name = "released_findings_id")
    private UUID releasedFindingsId;

    @Column(name = "released_report_id")
    private UUID releasedReportId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "released_report_sha256", columnDefinition = "char(64)")
    private String releasedReportSha256;

    @Column(name = "findings_released_at")
    private Instant findingsReleasedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ImagingStudyReleaseJpaEntity() { }

    ImagingStudyReleaseJpaEntity(ImagingStudyJpaEntity study, Instant now) {
        this.study = study;
        imagingStudyId = study.getId();
        updatedAt = now;
    }

    public UUID getConfirmationEventId() { return confirmationEventId; }
    public int getConfirmationAttemptCount() { return confirmationAttemptCount; }
    public Instant getNextConfirmationAttemptAt() { return nextConfirmationAttemptAt; }
    public String getLastSafeErrorCode() { return lastSafeErrorCode; }
    public UUID getReleaseEventId() { return releaseEventId; }
    public String getReleasePayloadSha256() { return releasePayloadSha256; }
    public UUID getReleasedFindingsId() { return releasedFindingsId; }
    public UUID getReleasedReportId() { return releasedReportId; }
    public String getReleasedReportSha256() { return releasedReportSha256; }
    public Instant getFindingsReleasedAt() { return findingsReleasedAt; }

    void prepare(UUID eventId, Instant now) {
        confirmationEventId = eventId; nextConfirmationAttemptAt = now; updatedAt = now;
    }
    void recordAttempt(String safeCode, Instant now) {
        confirmationAttemptCount = Math.min(8, confirmationAttemptCount + 1);
        lastSafeErrorCode = safeCode; updatedAt = now;
    }
    void recordRetry(Instant nextAt, String safeCode, Instant now) {
        recordAttempt(safeCode, now); nextConfirmationAttemptAt = nextAt;
    }
    void accept(UUID eventId, String payloadSha256, UUID findingsId, UUID reportId, String reportSha256, Instant now) {
        releaseEventId = eventId; releasePayloadSha256 = payloadSha256; releasedFindingsId = findingsId;
        releasedReportId = reportId; releasedReportSha256 = reportSha256; findingsReleasedAt = now; updatedAt = now;
    }
}
