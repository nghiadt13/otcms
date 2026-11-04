package fu.fa26.sep490.g58.otcms_be.imaging.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "imaging_release_inbox")
public class ImagingReleaseInboxJpaEntity {
    @Id private UUID id;
    @Column(name = "event_id", nullable = false, unique = true) private UUID eventId;
    @Column(name = "request_id", nullable = false) private UUID requestId;
    @Column(name = "visit_id", nullable = false) private UUID visitId;
    @Column(name = "patient_id", nullable = false) private UUID patientId;
    @Column(name = "study_ref", nullable = false, length = 128) private String studyRef;
    @Column(name = "findings_version_id", nullable = false) private UUID findingsVersionId;
    @Column(name = "report_id", nullable = false) private UUID reportId;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "report_sha256", nullable = false, length = 64, columnDefinition = "char(64)")
    private String reportSha256;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "payload_sha256", nullable = false, length = 64, columnDefinition = "char(64)")
    private String payloadSha256;
    @Column(nullable = false, length = 24) private String outcome;
    @Column(name = "received_at", nullable = false, insertable = false, updatable = false) private Instant receivedAt;
    @Column(name = "acknowledged_at") private Instant acknowledgedAt;

    protected ImagingReleaseInboxJpaEntity() { }

    public ImagingReleaseInboxJpaEntity(UUID id, UUID eventId, UUID requestId, UUID visitId, UUID patientId,
            String studyRef, UUID findingsVersionId, UUID reportId, String reportSha256,
            String payloadSha256, String outcome, Instant acknowledgedAt) {
        this.id = id; this.eventId = eventId; this.requestId = requestId; this.visitId = visitId;
        this.patientId = patientId; this.studyRef = studyRef; this.findingsVersionId = findingsVersionId;
        this.reportId = reportId; this.reportSha256 = reportSha256; this.payloadSha256 = payloadSha256;
        this.outcome = outcome; this.acknowledgedAt = acknowledgedAt;
    }

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public UUID getRequestId() { return requestId; }
    public UUID getVisitId() { return visitId; }
    public UUID getPatientId() { return patientId; }
    public String getStudyRef() { return studyRef; }
    public UUID getFindingsVersionId() { return findingsVersionId; }
    public UUID getReportId() { return reportId; }
    public String getReportSha256() { return reportSha256; }
    public String getPayloadSha256() { return payloadSha256; }
    public String getOutcome() { return outcome; }
    public Instant getReceivedAt() { return receivedAt; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
}
