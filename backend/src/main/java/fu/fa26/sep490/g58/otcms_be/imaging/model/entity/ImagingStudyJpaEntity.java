package fu.fa26.sep490.g58.otcms_be.imaging.model.entity;

import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingOperationRecord;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationState;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationType;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingVerificationStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dicom_index", columnDefinition = "jsonb")
    private DicomIndex dicomIndex;

    // EAGER is deliberate: Hibernate cannot lazy-load this inverse 0..1 side without bytecode enhancement.
    // It is one primary-key lookup per study, never a collection; the owner side is ImagingStudyReleaseJpaEntity.study.
    @OneToOne(mappedBy = "study", fetch = FetchType.EAGER, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private ImagingStudyReleaseJpaEntity release;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    protected ImagingStudyJpaEntity() { }

    public ImagingStudyJpaEntity(ImagingOperationRecord operation) {
        id = operation.id();
        studyRef = operation.studyRef();
        requestId = operation.requestId();
        attemptId = operation.attemptId();
        visitId = operation.visitId();
        patientId = operation.patientId();
        actorAccountId = operation.actorAccountId();
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

    public void persistConfirmedStudy(String studyRef, String archiveStudyRef, DicomIndex index,
            Instant now) {
        this.studyRef = studyRef;
        this.archiveStudyRef = archiveStudyRef;
        this.dicomIndex = index;
        updatedAt = now;
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
    public DicomIndex getDicomIndex() { return dicomIndex; }
    public UUID getConfirmationEventId() { return release == null ? null : release.getConfirmationEventId(); }
    public int getConfirmationAttemptCount() { return release == null ? 0 : release.getConfirmationAttemptCount(); }
    public Instant getNextConfirmationAttemptAt() { return release == null ? null : release.getNextConfirmationAttemptAt(); }
    public String getLastSafeErrorCode() { return release == null ? null : release.getLastSafeErrorCode(); }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public UUID getReleaseEventId() { return release == null ? null : release.getReleaseEventId(); }
    public String getReleasePayloadSha256() { return release == null ? null : release.getReleasePayloadSha256(); }
    public UUID getReleasedFindingsId() { return release == null ? null : release.getReleasedFindingsId(); }
    public UUID getReleasedReportId() { return release == null ? null : release.getReleasedReportId(); }
    public String getReleasedReportSha256() { return release == null ? null : release.getReleasedReportSha256(); }
    public Instant getFindingsReleasedAt() { return release == null ? null : release.getFindingsReleasedAt(); }

    public void updateConfirmationOutcome(ImagingOperationState nextState, String safeCode, Instant now) {
        state = nextState;
        safeOutcomeCode = safeCode;
        updatedAt = now;
    }

    public void recordConfirmationRetry(Instant nextAt, String safeCode, Instant now) {
        releaseRow(now).recordRetry(nextAt, safeCode, now);
        safeOutcomeCode = safeCode;
        version++;
        updatedAt = now;
    }

    public void recordConfirmationReconciliation(String safeCode, Instant now) {
        state = ImagingOperationState.RECONCILIATION_REQUIRED;
        releaseRow(now).recordAttempt(safeCode, now);
        safeOutcomeCode = safeCode;
        version++;
        updatedAt = now;
    }

    public void setDicomIndex(DicomIndex index) { dicomIndex = index; }
    public void prepareConfirmation(UUID eventId, Instant now) {
        state = ImagingOperationState.CONFIRMATION_PENDING; releaseRow(now).prepare(eventId, now);
        safeOutcomeCode = null; version++; updatedAt = now;
    }
    public void markAcknowledged(Instant now) {
        state = ImagingOperationState.LINKED; releaseRow(now).recordAttempt(null, now);
        acknowledgedAt = now; safeOutcomeCode = null; version++; updatedAt = now;
    }
    public void scheduleRetry(Instant nextAt, String safeCode) {
        Instant now = Instant.now();
        releaseRow(now).recordRetry(nextAt, safeCode, now); safeOutcomeCode = safeCode;
        version++; updatedAt = now;
    }
    public void markReconciliationRequired(String safeCode) {
        Instant now = Instant.now();
        state = ImagingOperationState.RECONCILIATION_REQUIRED;
        releaseRow(now).recordAttempt(safeCode, now);
        safeOutcomeCode = safeCode; version++; updatedAt = now;
    }
    public void acceptFindingsRelease(UUID eventId, String payloadSha256, UUID findingsId,
            UUID reportId, String reportSha256, Instant now) {
        releaseRow(now).accept(eventId, payloadSha256, findingsId, reportId, reportSha256, now);
        version++; updatedAt = now;
    }
    /** Release bookkeeping lives in study_releases; the row is created on first use inside the caller's transaction. */
    private ImagingStudyReleaseJpaEntity releaseRow(Instant now) {
        if (release == null) release = new ImagingStudyReleaseJpaEntity(this, now);
        return release;
    }
    public record DicomIndex(List<DicomSeries> series) { }
    public record DicomSeries(String uid, @com.fasterxml.jackson.annotation.JsonProperty("orthanc_ref") String orthancRef,
            List<DicomInstance> instances) { }
    public record DicomInstance(@com.fasterxml.jackson.annotation.JsonProperty("sop_uid") String sopUid,
            @com.fasterxml.jackson.annotation.JsonProperty("orthanc_ref") String orthancRef, Integer frames) { }
}
