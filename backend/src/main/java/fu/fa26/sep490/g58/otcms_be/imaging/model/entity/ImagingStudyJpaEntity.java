package fu.fa26.sep490.g58.otcms_be.imaging.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "imaging_studies")
public class ImagingStudyJpaEntity {
    @Id
    @Column(name = "study_ref", length = 128)
    private String studyRef;
    @Column(name = "ingest_operation_id", nullable = false, unique = true)
    private UUID ingestOperationId;
    @Column(name = "request_id", nullable = false)
    private UUID requestId;
    @Column(name = "attempt_id", nullable = false)
    private UUID attemptId;
    @Column(name = "visit_id", nullable = false)
    private UUID visitId;
    @Column(name = "patient_id", nullable = false)
    private UUID patientId;
    @Column(name = "archive_study_ref", nullable = false, unique = true, columnDefinition = "text")
    private String archiveStudyRef;
    @Column(name = "study_instance_uid", nullable = false, unique = true, length = 64)
    private String studyInstanceUid;
    @Column(nullable = false, length = 16)
    private String modality;
    @Column(name = "series_count", nullable = false)
    private int seriesCount;
    @Column(name = "instance_count", nullable = false)
    private int instanceCount;
    @Column(name = "verification_status", nullable = false, length = 24)
    private String verificationStatus;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected ImagingStudyJpaEntity() { }

    public ImagingStudyJpaEntity(String studyRef, UUID ingestOperationId, UUID requestId, UUID attemptId,
            UUID visitId, UUID patientId, String archiveStudyRef, String studyInstanceUid, String modality,
            int seriesCount, int instanceCount, String verificationStatus) {
        this.studyRef = studyRef;
        this.ingestOperationId = ingestOperationId;
        this.requestId = requestId;
        this.attemptId = attemptId;
        this.visitId = visitId;
        this.patientId = patientId;
        this.archiveStudyRef = archiveStudyRef;
        this.studyInstanceUid = studyInstanceUid;
        this.modality = modality;
        this.seriesCount = seriesCount;
        this.instanceCount = instanceCount;
        this.verificationStatus = verificationStatus;
    }

    public String getStudyRef() { return studyRef; }
    public UUID getIngestOperationId() { return ingestOperationId; }
    public UUID getRequestId() { return requestId; }
    public UUID getAttemptId() { return attemptId; }
    public UUID getVisitId() { return visitId; }
    public UUID getPatientId() { return patientId; }
    public String getArchiveStudyRef() { return archiveStudyRef; }
    public String getStudyInstanceUid() { return studyInstanceUid; }
    public String getModality() { return modality; }
    public int getSeriesCount() { return seriesCount; }
    public int getInstanceCount() { return instanceCount; }
    public String getVerificationStatus() { return verificationStatus; }
    public Instant getCreatedAt() { return createdAt; }
}
