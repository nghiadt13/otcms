package fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticFindingsVersionState;
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
@Table(name = "diagnostic_findings_versions", uniqueConstraints = {
    @UniqueConstraint(name = "uq_diagnostic_findings_request_version", columnNames = {"request_id", "version_number"})
})
public class DiagnosticFindingsVersionJpaEntity {
    @Id
    private UUID id;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "visit_id", nullable = false)
    private UUID visitId;

    @Column(name = "study_ref", nullable = false, columnDefinition = "text")
    private String studyRef;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "findings_text", nullable = false, columnDefinition = "text")
    private String findingsText;

    @Column(name = "correction_reason", columnDefinition = "text")
    private String correctionReason;

    @Column(name = "recorded_by_doctor_id", nullable = false)
    private UUID recordedByDoctorId;

    @Column(name = "occurred_at")
    private Instant occurredAt;

    @Column(name = "recorded_at", nullable = false, insertable = false, updatable = false)
    private Instant recordedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DiagnosticFindingsVersionState state;

    @Column(name = "required_pdf_report_id")
    private UUID requiredPdfReportId;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected DiagnosticFindingsVersionJpaEntity() { }

    public DiagnosticFindingsVersionJpaEntity(UUID id, UUID requestId, UUID visitId, String studyRef,
            int versionNumber, String findingsText, UUID recordedByDoctorId, Instant occurredAt,
            DiagnosticFindingsVersionState state, UUID requiredPdfReportId, Instant completedAt) {
        this.id = id;
        this.requestId = requestId;
        this.visitId = visitId;
        this.studyRef = studyRef;
        this.versionNumber = versionNumber;
        this.findingsText = findingsText;
        this.recordedByDoctorId = recordedByDoctorId;
        this.occurredAt = occurredAt;
        this.state = state;
        this.requiredPdfReportId = requiredPdfReportId;
        this.completedAt = completedAt;
    }

    public UUID getId() { return id; }
    public UUID getRequestId() { return requestId; }
    public UUID getVisitId() { return visitId; }
    public String getStudyRef() { return studyRef; }
    public int getVersionNumber() { return versionNumber; }
    public String getFindingsText() { return findingsText; }
    public String getCorrectionReason() { return correctionReason; }
    public UUID getRecordedByDoctorId() { return recordedByDoctorId; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getRecordedAt() { return recordedAt; }
    public DiagnosticFindingsVersionState getState() { return state; }
    public UUID getRequiredPdfReportId() { return requiredPdfReportId; }
    public Instant getCompletedAt() { return completedAt; }

    public void reviseDraft(String findingsText, Instant occurredAt) {
        if (state != DiagnosticFindingsVersionState.DRAFT) {
            throw new IllegalStateException("Completed findings are immutable");
        }
        this.findingsText = findingsText;
        this.occurredAt = occurredAt;
    }

    public void setCorrectionReason(String correctionReason) {
        if (state != DiagnosticFindingsVersionState.DRAFT) {
            throw new IllegalStateException("Completed findings are immutable");
        }
        this.correctionReason = correctionReason;
    }

    public void markReleased(UUID reportId, Instant releasedAt) {
        if (state != DiagnosticFindingsVersionState.DRAFT) {
            throw new IllegalStateException("Completed findings are immutable");
        }
        this.requiredPdfReportId = reportId;
        this.state = DiagnosticFindingsVersionState.COMPLETED;
        this.completedAt = releasedAt;
    }
}
