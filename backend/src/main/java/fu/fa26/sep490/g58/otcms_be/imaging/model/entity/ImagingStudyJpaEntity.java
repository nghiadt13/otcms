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
@Table(name = "imaging_studies")
public class ImagingStudyJpaEntity {
    @Id
    private UUID id;

    @Column(name = "study_ref", length = 128)
    private String studyRef;

    @Column(name = "service_order_id", nullable = false)
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

    @Column(name = "upload_idempotency_key", nullable = false, length = 128)
    private String uploadIdempotencyKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dicom_index", columnDefinition = "jsonb")
    private DicomIndex dicomIndex;

    @Column(name = "send_idempotency_key", length = 128)
    private String sendIdempotencyKey;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "send_fingerprint", columnDefinition = "char(64)")
    private String sendFingerprint;

    @Column(name = "confirmation_event_id")
    private UUID confirmationEventId;

    @Column(name = "confirmation_attempt_count", nullable = false)
    private int confirmationAttemptCount;

    @Column(name = "next_confirmation_attempt_at")
    private Instant nextConfirmationAttemptAt;

    @Column(name = "last_safe_error_code", length = 64)
    private String lastSafeErrorCode;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

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

    protected ImagingStudyJpaEntity() { }

    public ImagingStudyJpaEntity(ImagingOperationRecord operation, String uploadIdempotencyKey) {
        id = operation.id();
        studyRef = operation.studyRef();
        requestId = operation.requestId();
        attemptId = operation.attemptId();
        visitId = operation.visitId();
        patientId = operation.patientId();
        actorAccountId = operation.actorAccountId();
        this.uploadIdempotencyKey = uploadIdempotencyKey;
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
                actorAccountId, ImagingOperationType.UPLOAD, state, payloadSha256, payloadSizeBytes, studyInstanceUid,
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

    public ImagingStudyJpaEntity(String studyRef, UUID operationId, UUID requestId, UUID attemptId,
            UUID visitId, UUID patientId, String archiveStudyRef, String studyInstanceUid, String modality,
            int seriesCount, int instanceCount, String verificationStatus) {
        id = operationId; this.studyRef = studyRef; this.requestId = requestId; this.attemptId = attemptId;
        this.visitId = visitId; this.patientId = patientId; this.archiveStudyRef = archiveStudyRef;
        this.studyInstanceUid = studyInstanceUid; this.modality = modality;
        this.seriesCount = seriesCount; this.instanceCount = instanceCount;
        this.verificationStatus = ImagingVerificationStatus.valueOf(verificationStatus);
    }
    public UUID getIngestOperationId() { return id; }
    public UUID getServiceOrderId() { return requestId; }
    public String getVerificationStatus() { return verificationStatus.name(); }
    public String getStudyRef() { return studyRef; }
    public UUID getRequestId() { return requestId; }
    public UUID getAttemptId() { return attemptId; }
    public UUID getVisitId() { return visitId; }
    public UUID getPatientId() { return patientId; }
    public UUID getActorAccountId() { return actorAccountId; }
    public String getArchiveStudyRef() { return archiveStudyRef; }
    public String getStudyInstanceUid() { return studyInstanceUid; }
    public String getModality() { return modality; }
    public int getSeriesCount() { return seriesCount; }
    public int getInstanceCount() { return instanceCount; }
    public List<String> getVerificationEvidence() { return verificationEvidence; }
    public String getPayloadSha256() { return payloadSha256; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUploadIdempotencyKey() { return uploadIdempotencyKey; }
    public DicomIndex getDicomIndex() { return dicomIndex; }
    public String getSendIdempotencyKey() { return sendIdempotencyKey; }
    public String getSendFingerprint() { return sendFingerprint; }
    public UUID getConfirmationEventId() { return confirmationEventId; }
    public int getConfirmationAttemptCount() { return confirmationAttemptCount; }
    public Instant getNextConfirmationAttemptAt() { return nextConfirmationAttemptAt; }
    public String getLastSafeErrorCode() { return lastSafeErrorCode; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public UUID getReleaseEventId() { return releaseEventId; }
    public String getReleasePayloadSha256() { return releasePayloadSha256; }
    public UUID getReleasedFindingsId() { return releasedFindingsId; }
    public UUID getReleasedReportId() { return releasedReportId; }
    public String getReleasedReportSha256() { return releasedReportSha256; }
    public Instant getFindingsReleasedAt() { return findingsReleasedAt; }

    public void setDicomIndex(DicomIndex index) { dicomIndex = index; }
    public void prepareConfirmation(UUID eventId, String key, String fingerprint, Instant now) {
        state = ImagingOperationState.CONFIRMATION_PENDING; confirmationEventId = eventId;
        sendIdempotencyKey = key; sendFingerprint = fingerprint; nextConfirmationAttemptAt = now; updatedAt = now;
    }
    public void markAcknowledged(Instant now) {
        state = ImagingOperationState.LINKED; confirmationAttemptCount = Math.min(8, confirmationAttemptCount + 1);
        acknowledgedAt = now; lastSafeErrorCode = null; updatedAt = now;
    }
    public void scheduleRetry(Instant nextAt, String safeCode) {
        confirmationAttemptCount = Math.min(8, confirmationAttemptCount + 1);
        nextConfirmationAttemptAt = nextAt; lastSafeErrorCode = safeCode; updatedAt = Instant.now();
    }
    public void markReconciliationRequired(String safeCode) {
        state = ImagingOperationState.RECONCILIATION_REQUIRED;
        confirmationAttemptCount = Math.min(8, confirmationAttemptCount + 1);
        lastSafeErrorCode = safeCode; updatedAt = Instant.now();
    }
    public void acceptFindingsRelease(UUID eventId, String payloadSha256, UUID findingsId,
            UUID reportId, String reportSha256, Instant now) {
        releaseEventId = eventId; releasePayloadSha256 = payloadSha256; releasedFindingsId = findingsId;
        releasedReportId = reportId; releasedReportSha256 = reportSha256; findingsReleasedAt = now; updatedAt = now;
    }
    public record DicomIndex(List<DicomSeries> series) { }
    public record DicomSeries(String uid, @com.fasterxml.jackson.annotation.JsonProperty("orthanc_ref") String orthancRef,
            List<DicomInstance> instances) { }
    public record DicomInstance(@com.fasterxml.jackson.annotation.JsonProperty("sop_uid") String sopUid,
            @com.fasterxml.jackson.annotation.JsonProperty("orthanc_ref") String orthancRef, Integer frames) { }
}
