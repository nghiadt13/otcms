package fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticReportValidationStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticFindingsVersionState;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "diagnostic_reports", uniqueConstraints = {
    @UniqueConstraint(name = "uq_results_version", columnNames = {"service_order_id", "version_number"}),
    @UniqueConstraint(name = "uq_diagnostic_reports_storage_ref", columnNames = "report_storage_ref")
})
public class DiagnosticReportJpaEntity {
    @Id
    private UUID id;

    @Column(name = "service_order_id", nullable = false)
    private UUID requestId;

    @Column(name = "visit_id", nullable = false)
    private UUID visitId;

    @Column(name = "study_ref", columnDefinition = "text")
    private String studyRef;

    @Column(name = "version_number", nullable = false)
    private int reportVersion;

    @Column(name = "report_storage_ref", columnDefinition = "text")
    private String storageRef;

    @Column(name = "report_sha256", length = 64)
    private String contentSha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", length = 16)
    private DiagnosticReportValidationStatus validationStatus;

    @Column(name = "uploaded_by_doctor_id")
    private UUID uploadedByDoctorId;

    @Column(name = "uploaded_at")
    private Instant uploadedAt;

    @Column(name = "validated_at")
    private Instant validatedAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "report_id")
    private UUID reportId;

    @Column(name = "result_type", nullable = false, length = 16)
    private String resultType = "IMAGING";

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 16)
    private DiagnosticFindingsVersionState state = DiagnosticFindingsVersionState.DRAFT;

    @Column(name = "findings_text", columnDefinition = "text")
    private String findingsText;

    @Column(name = "correction_reason", columnDefinition = "text")
    private String correctionReason;

    @Column(name = "recorded_by_doctor_id")
    private UUID recordedByDoctorId;

    @Column(name = "occurred_at")
    private Instant occurredAt;

    @Column(name = "recorded_at", nullable = false, insertable = false, updatable = false)
    private Instant recordedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "draft_idempotency_key", length = 255)
    private String draftIdempotencyKey;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "draft_fingerprint", columnDefinition = "char(64)")
    private String draftFingerprint;

    @Column(name = "correction_idempotency_key", length = 255)
    private String correctionIdempotencyKey;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "correction_fingerprint", columnDefinition = "char(64)")
    private String correctionFingerprint;

    @Column(name = "upload_idempotency_key", length = 255)
    private String uploadIdempotencyKey;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "upload_fingerprint", columnDefinition = "char(64)")
    private String uploadFingerprint;

    @Column(name = "complete_idempotency_key", length = 255)
    private String completeIdempotencyKey;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "complete_fingerprint", columnDefinition = "char(64)")
    private String completeFingerprint;

    protected DiagnosticReportJpaEntity() { }

    public DiagnosticReportJpaEntity(UUID id, UUID requestId, UUID visitId, String studyRef,
            int reportVersion, String storageRef, String contentSha256,
            DiagnosticReportValidationStatus validationStatus, UUID uploadedByDoctorId,
            Instant validatedAt, Instant releasedAt) {
        this.id = id;
        this.reportId = id;
        this.uploadedAt = Instant.now();
        this.requestId = requestId;
        this.visitId = visitId;
        this.studyRef = studyRef;
        this.reportVersion = reportVersion;
        this.storageRef = storageRef;
        this.contentSha256 = contentSha256;
        this.validationStatus = validationStatus;
        this.uploadedByDoctorId = uploadedByDoctorId;
        this.validatedAt = validatedAt;
        this.releasedAt = releasedAt;
    }

    public UUID getId() { return id; }
    public UUID getRequestId() { return requestId; }
    public UUID getVisitId() { return visitId; }
    public String getStudyRef() { return studyRef; }
    public int getReportVersion() { return reportVersion; }
    public String getStorageRef() { return storageRef; }
    public String getContentSha256() { return contentSha256; }
    public DiagnosticReportValidationStatus getValidationStatus() { return validationStatus; }
    public UUID getUploadedByDoctorId() { return uploadedByDoctorId; }
    public Instant getUploadedAt() { return uploadedAt; }
    public Instant getValidatedAt() { return validatedAt; }
    public Instant getReleasedAt() { return releasedAt; }

    public void setValidationStatus(DiagnosticReportValidationStatus validationStatus) {
        this.validationStatus = validationStatus;
    }

    public void setValidatedAt(Instant validatedAt) { this.validatedAt = validatedAt; }
    public void setReleasedAt(Instant releasedAt) { this.releasedAt = releasedAt; }

    public DiagnosticReportJpaEntity(UUID id, UUID requestId, UUID visitId, String studyRef,
            int versionNumber, String findingsText, UUID recordedByDoctorId, Instant occurredAt) {
        this.id = id; this.requestId = requestId; this.visitId = visitId; this.studyRef = studyRef;
        this.reportVersion = versionNumber; this.findingsText = findingsText;
        this.recordedByDoctorId = recordedByDoctorId; this.occurredAt = occurredAt;
    }

    public int getVersionNumber() { return reportVersion; }
    public UUID getServiceOrderId() { return requestId; }
    public UUID getReportId() { return reportId; }
    public String getResultType() { return resultType; }
    public DiagnosticFindingsVersionState getState() { return state; }
    public String getFindingsText() { return findingsText; }
    public String getCorrectionReason() { return correctionReason; }
    public UUID getRecordedByDoctorId() { return recordedByDoctorId; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getRecordedAt() { return recordedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public String getDraftIdempotencyKey() { return draftIdempotencyKey; }
    public String getDraftFingerprint() { return draftFingerprint; }
    public String getCorrectionIdempotencyKey() { return correctionIdempotencyKey; }
    public String getCorrectionFingerprint() { return correctionFingerprint; }
    public String getUploadIdempotencyKey() { return uploadIdempotencyKey; }
    public String getUploadFingerprint() { return uploadFingerprint; }
    public String getCompleteIdempotencyKey() { return completeIdempotencyKey; }
    public String getCompleteFingerprint() { return completeFingerprint; }

    public void reviseDraft(String text, Instant occurredAt) {
        requireDraft(); this.findingsText = text; this.occurredAt = occurredAt;
    }
    public void setCorrectionReason(String reason) { requireDraft(); correctionReason = reason; }
    public void attachReport(UUID reportId, String storageRef, String sha256,
            DiagnosticReportValidationStatus status, UUID doctorId, Instant uploadedAt, Instant validatedAt) {
        requireDraft(); this.reportId = reportId; this.storageRef = storageRef; contentSha256 = sha256;
        validationStatus = status; uploadedByDoctorId = doctorId;
        this.uploadedAt = uploadedAt; this.validatedAt = validatedAt;
    }
    public void markCompleted(UUID reportId, Instant now) {
        requireDraft();
        if (!java.util.Objects.equals(this.reportId, reportId)
                || validationStatus != DiagnosticReportValidationStatus.VALIDATED) {
            throw new IllegalStateException("A validated report is required");
        }
        state = DiagnosticFindingsVersionState.COMPLETED; completedAt = now; releasedAt = now;
    }
    private void requireDraft() {
        if (state != DiagnosticFindingsVersionState.DRAFT) {
            throw new IllegalStateException("Completed findings are immutable");
        }
    }
    public void recordDraftKey(String key, String fingerprint) { draftIdempotencyKey = key; draftFingerprint = fingerprint; }
    public void recordCorrectionKey(String key, String fingerprint) { correctionIdempotencyKey = key; correctionFingerprint = fingerprint; }
    public void recordUploadKey(String key, String fingerprint) { uploadIdempotencyKey = key; uploadFingerprint = fingerprint; }
    public void recordCompleteKey(String key, String fingerprint) { completeIdempotencyKey = key; completeFingerprint = fingerprint; }
}
