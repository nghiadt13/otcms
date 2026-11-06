package fu.fa26.sep490.g58.otcms_be.imaging.model.entity;

import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingOperationRecord;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationState;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationType;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingVerificationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.List;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "imaging_ingest_operations")
public class ImagingIngestOperationJpaEntity {
    @Id
    private UUID id;

    @Column(name = "study_ref", length = 128)
    private String studyRef;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "attempt_id", nullable = false)
    private UUID attemptId;

    @Column(name = "visit_id", nullable = false)
    private UUID visitId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "actor_account_id", nullable = false)
    private UUID actorAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 16)
    private ImagingOperationType operationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ImagingOperationState state;

    @Column(name = "payload_sha256", nullable = false, length = 64, columnDefinition = "char(64)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private String payloadSha256;

    @Column(name = "payload_size_bytes", nullable = false)
    private long payloadSizeBytes;

    @Column(name = "study_instance_uid", length = 64)
    private String studyInstanceUid;

    @Column(length = 16)
    private String modality;

    @Column(name = "series_count", nullable = false)
    private int seriesCount;

    @Column(name = "instance_count", nullable = false)
    private int instanceCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 24)
    private ImagingVerificationStatus verificationStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "verification_evidence", nullable = false, columnDefinition = "jsonb")
    private List<String> verificationEvidence;

    @Column(name = "archive_study_ref", columnDefinition = "text")
    private String archiveStudyRef;

    @Column(name = "safe_outcome_code", length = 64)
    private String safeOutcomeCode;

    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ImagingIngestOperationJpaEntity() { }

    public ImagingIngestOperationJpaEntity(ImagingOperationRecord operation) {
        id = operation.id();
        studyRef = operation.studyRef();
        requestId = operation.requestId();
        attemptId = operation.attemptId();
        visitId = operation.visitId();
        patientId = operation.patientId();
        actorAccountId = operation.actorAccountId();
        operationType = operation.operationType();
        state = operation.state();
        payloadSha256 = operation.payloadSha256();
        payloadSizeBytes = operation.payloadSizeBytes();
        studyInstanceUid = operation.studyInstanceUid();
        modality = operation.modality();
        seriesCount = operation.seriesCount();
        instanceCount = operation.instanceCount();
        verificationStatus = operation.verificationStatus();
        verificationEvidence = operation.verificationEvidence();
        archiveStudyRef = operation.archiveStudyRef();
        safeOutcomeCode = operation.safeOutcomeCode();
        version = operation.version();
        createdAt = operation.createdAt();
        updatedAt = operation.updatedAt();
    }

    public ImagingOperationRecord toModel() {
        return new ImagingOperationRecord(id, studyRef, requestId, attemptId, visitId, patientId,
                actorAccountId, operationType, state, payloadSha256, payloadSizeBytes, studyInstanceUid,
                modality, seriesCount, instanceCount, verificationStatus, verificationEvidence,
                archiveStudyRef, safeOutcomeCode, version, createdAt, updatedAt);
    }

    public UUID getId() { return id; }
    public ImagingOperationState getState() { return state; }
    public long getVersion() { return version; }

    public void applyValidation(fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingDicomStudyValidation validation) {
        studyInstanceUid = validation.studyInstanceUid();
        modality = validation.modality();
        seriesCount = validation.seriesCount();
        instanceCount = validation.instanceCount();
        verificationStatus = validation.verificationStatus();
        verificationEvidence = validation.evidenceCodes();
        updatedAt = Instant.now();
    }

    public void setArchiveStudyRef(String archiveStudyRef) {
        this.archiveStudyRef = archiveStudyRef;
        updatedAt = Instant.now();
    }

    public void setStudyRef(String studyRef) {
        this.studyRef = studyRef;
        updatedAt = Instant.now();
    }
}
