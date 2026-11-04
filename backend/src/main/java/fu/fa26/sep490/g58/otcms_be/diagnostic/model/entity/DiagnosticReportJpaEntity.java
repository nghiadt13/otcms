package fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticReportValidationStatus;
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
    @UniqueConstraint(name = "uq_diagnostic_reports_request_version", columnNames = {"request_id", "report_version"}),
    @UniqueConstraint(name = "uq_diagnostic_reports_storage_ref", columnNames = "storage_ref")
})
public class DiagnosticReportJpaEntity {
    @Id
    private UUID id;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "visit_id", nullable = false)
    private UUID visitId;

    @Column(name = "study_ref", nullable = false, columnDefinition = "text")
    private String studyRef;

    @Column(name = "report_version", nullable = false)
    private int reportVersion;

    @Column(name = "storage_ref", nullable = false, columnDefinition = "text")
    private String storageRef;

    @Column(name = "content_sha256", nullable = false, length = 64)
    private String contentSha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", nullable = false, length = 16)
    private DiagnosticReportValidationStatus validationStatus;

    @Column(name = "uploaded_by_doctor_id", nullable = false)
    private UUID uploadedByDoctorId;

    @Column(name = "uploaded_at", nullable = false, insertable = false, updatable = false)
    private Instant uploadedAt;

    @Column(name = "validated_at")
    private Instant validatedAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    protected DiagnosticReportJpaEntity() { }

    public DiagnosticReportJpaEntity(UUID id, UUID requestId, UUID visitId, String studyRef,
            int reportVersion, String storageRef, String contentSha256,
            DiagnosticReportValidationStatus validationStatus, UUID uploadedByDoctorId,
            Instant validatedAt, Instant releasedAt) {
        this.id = id;
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
}
