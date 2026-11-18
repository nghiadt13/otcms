package fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl;

import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort.ExecutionStatus;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort.ImagingExecution;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditEntry;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditLogWriter;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.FindingsReleaseEvent;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ReleaseAcknowledgement;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ReportValidationStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.StoredReport;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.VisitEvidence;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.ReportStoragePort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.ImagingStudyPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.VisitDiagnosticPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticAttempt;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticEvidenceContext;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticFindingsReleaseView;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticFindingsView;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticReportUpload;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticReportView;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticRequest;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticRequestView;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticReportJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticAction;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticFindingsVersionState;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticReportValidationStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticRequestStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticReportJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticAuthorizationPolicy;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticFindingsService;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.DigestInputStream;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiagnosticFindingsServiceImpl implements DiagnosticFindingsService {
    private static final String REVIEW = "REVIEW_FINDINGS";
    private static final String DRAFT = "SAVE_FINDINGS_DRAFT";
    private static final String CORRECTION = "CREATE_FINDINGS_CORRECTION";
    private static final String UPLOAD_REPORT = "UPLOAD_CLINIC_REPORT";
    private static final String COMPLETE = "COMPLETE_FINDINGS";
    private final DiagnosticReportJpaRepository reports;
    private final DiagnosticAuthorizationPolicy authorization;
    private final ObjectProvider<VisitDiagnosticPort> visitPorts;
    private final ObjectProvider<ReportStoragePort> reportPorts;
    private final ObjectProvider<ImagingStudyPort> studyPorts;
    private final ClinicalServiceOrderExecutionPort executions;
    private final AuditLogWriter auditWriter;

    public DiagnosticFindingsServiceImpl(DiagnosticReportJpaRepository reports,
            DiagnosticAuthorizationPolicy authorization, ObjectProvider<VisitDiagnosticPort> visitPorts,
            ObjectProvider<ReportStoragePort> reportPorts, ObjectProvider<ImagingStudyPort> studyPorts,
            ClinicalServiceOrderExecutionPort executions, AuditLogWriter auditWriter) {
        this.reports = Objects.requireNonNull(reports, "reports");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.visitPorts = Objects.requireNonNull(visitPorts, "visitPorts");
        this.reportPorts = Objects.requireNonNull(reportPorts, "reportPorts");
        this.studyPorts = Objects.requireNonNull(studyPorts, "studyPorts");
        this.executions = Objects.requireNonNull(executions, "executions");
        this.auditWriter = Objects.requireNonNull(auditWriter, "auditWriter");
    }

    @Override
    @Transactional
    public DiagnosticRequestView review(DiagnosticActor actor, UUID requestId, String idempotencyKey,
            long expectedVersion, String studyRef, String correlationId) {
        ImagingExecution request = executions.findForUpdate(requestId)
                .orElseThrow(() -> new DiagnosticOperationException(DiagnosticOperationOutcome.RECORD_NOT_FOUND));
        // UC-50: the ordering doctor or the current responsible doctor (proved through the visit port below).
        if (actor == null || actor.kind() != DiagnosticActor.Kind.USER || actor.role() != AccountRole.DOCTOR) throw unauthorized();
        VisitDiagnosticPort visitPort = visitPorts.getIfAvailable();
        if (visitPort == null) throw dependencyUnavailable();
        VisitEvidence visit;
        try {
            visit = visitPort.requireFindingsAccess(request.visitId(), actor.accountId(), request.orderingDoctorAccountId());
        } catch (DiagnosticOperationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw dependencyUnavailable();
        }
        if (visit == null || !request.visitId().equals(visit.visitId())
                || !request.patientId().equals(visit.patientId()) || !visit.examinationStarted()) throw evidenceMismatch();
        if (!actor.accountId().equals(visit.treatingDoctorId())) throw unauthorized();
        if (!visit.open() || !visit.eligible()) throw new DiagnosticOperationException(DiagnosticOperationOutcome.VISIT_NOT_ELIGIBLE);
        String key = requiredText(idempotencyKey, 255, "Idempotency-Key");
        String fingerprint = fingerprint(studyRef + "\n" + expectedVersion);
        if (key.equals(request.reviewIdempotencyKey())) {
            if (!fingerprint.equals(request.reviewFingerprint())) {
                throw new DiagnosticOperationException(DiagnosticOperationOutcome.IDEMPOTENCY_CONFLICT);
            }
            return executionView(request);
        }
        if (request.executionVersion() != expectedVersion) throw new DiagnosticOperationException(DiagnosticOperationOutcome.VERSION_CONFLICT);
        if (request.attemptId() == null || request.studyRef() == null || request.studyRef().isBlank()) throw stateConflict();
        if (studyRef == null || !studyRef.equals(request.studyRef())) throw evidenceMismatch();
        DiagnosticAttempt attempt = new DiagnosticAttempt(request.attemptId(), request.orderId(), 1,
                request.firstUseEventId(), request.firstUseAt(), request.studyRef());
        DiagnosticRequest domain = new DiagnosticRequest(request.orderId(), request.visitId(), request.patientId(),
                request.orderingDoctorAccountId(), request.serviceId(), request.imagingType(),
                DiagnosticRequestStatus.valueOf(request.status().name()), attempt, request.executionVersion(), request.createdAt());
        authorization.requireAllowed(domain, DiagnosticAction.REVIEW_RESULT, actor,
                new DiagnosticEvidenceContext(request.orderId(), request.visitId(), request.patientId(),
                        actor.accountId(), null, request.attemptId(), false, true, false, false, false), expectedVersion);
        if (executions.markReviewed(requestId, expectedVersion, key, fingerprint) != 1) {
            ImagingExecution current = executions.find(requestId).orElseThrow(this::dependencyUnavailable);
            if (key.equals(current.reviewIdempotencyKey())) {
                if (!fingerprint.equals(current.reviewFingerprint())) throw new DiagnosticOperationException(DiagnosticOperationOutcome.IDEMPOTENCY_CONFLICT);
                return executionView(current);
            }
            if (current.executionVersion() != expectedVersion) throw new DiagnosticOperationException(DiagnosticOperationOutcome.VERSION_CONFLICT);
            throw stateConflict();
        }
        ImagingExecution updated = executions.find(requestId).orElseThrow(this::dependencyUnavailable);
        auditWriter.record(AuditEntry.builder("diagnostic_audit_events", "DIAGNOSTIC", "REVIEW_RESULT:ALLOWED", "DIAGNOSTIC_REQUEST")
                .entityId(requestId).recordLabel(requestId.toString()).actorAccountId(actor.accountId()).actorType("ACCOUNT")
                .metadata(Map.of("studyRef", studyRef, "correlationId", requiredText(correlationId, 128, "correlationId")))
                .outcome("ALLOWED").occurredAt(Instant.now()).build());
        return executionView(updated);
    }

    private DiagnosticRequestView executionView(ImagingExecution execution) {
        return new DiagnosticRequestView(execution.orderId(), execution.visitId(), execution.patientId(),
                execution.orderingDoctorAccountId(), execution.serviceId(), execution.roomId(), execution.imagingType(),
                execution.bodyArea(), execution.clinicalReason(), DiagnosticRequestStatus.valueOf(execution.status().name()),
                execution.cancellationCause(), execution.cancellationReason(), execution.studyRef(), execution.executionVersion(),
                execution.createdAt());
    }

    @Override
    @Transactional
    public DiagnosticFindingsView saveDraft(DiagnosticActor actor, UUID requestId, String idempotencyKey,
            long expectedVersion, String content, String correlationId) {
        ImagingExecution request = lockRequest(requestId);
        requireDoctorAccess(actor, request);
        String body = requiredText(content, 20000, "content");
        String studyRef = requireCurrentStudy(request, request.studyRef());
        String fingerprint = fingerprint(body + "\n" + expectedVersion + "\n" + studyRef);
        String key = requiredText(idempotencyKey, 255, "Idempotency-Key");
        DiagnosticReportJpaEntity replay = reports.findByRequestIdAndDraftIdempotencyKey(requestId, key).orElse(null);
        if (replay != null) return replayFindings(replay, replay.getDraftFingerprint(), fingerprint);
        requireVersion(request, expectedVersion);
        VisitEvidence visit = requireVisitEvidence(actor, request);
        if (!actor.accountId().equals(visit.treatingDoctorId())) throw unauthorized();
        requireReviewed(request);
        DiagnosticReportJpaEntity latest = latestFindings(requestId);
        if (latest != null && latest.getState() == DiagnosticFindingsVersionState.COMPLETED) {
            throw stateConflict();
        }
        if (latest == null) {
            latest = new DiagnosticReportJpaEntity(UUID.randomUUID(), requestId, request.visitId(),
                    studyRef, 1, body, actor.accountId(), Instant.now());
        } else {
            if (!studyRef.equals(latest.getStudyRef())) throw evidenceMismatch();
            latest.reviseDraft(body, Instant.now());
        }
        latest.recordDraftKey(key, fingerprint);
        reports.saveAndFlush(latest);
        audit(request, actor, "SAVE_FINDINGS_DRAFT", correlationId,
                Map.of("findingsVersionId", latest.getId().toString(), "version", latest.getVersionNumber()));
        return findingsView(latest);
    }

    @Override
    @Transactional
    public DiagnosticFindingsView createCorrection(DiagnosticActor actor, UUID requestId, String idempotencyKey,
            long expectedVersion, String content, String correctionReason, String correlationId) {
        ImagingExecution request = lockRequest(requestId);
        requireDoctorAccess(actor, request);
        String body = requiredText(content, 20000, "content");
        String reason = requiredText(correctionReason, 2000, "correctionReason");
        String studyRef = requireCurrentStudy(request, request.studyRef());
        String fingerprint = fingerprint(body + "\n" + reason + "\n" + expectedVersion + "\n" + studyRef);
        String key = requiredText(idempotencyKey, 255, "Idempotency-Key");
        DiagnosticReportJpaEntity replay = reports.findByRequestIdAndCorrectionIdempotencyKey(requestId, key).orElse(null);
        if (replay != null) return replayFindings(replay, replay.getCorrectionFingerprint(), fingerprint);
        requireVersion(request, expectedVersion);
        VisitEvidence visit = requireVisitEvidence(actor, request);
        if (!actor.accountId().equals(visit.treatingDoctorId())) throw unauthorized();
        requireReviewed(request);
        DiagnosticReportJpaEntity latest = latestFindings(requestId);
        if (latest == null || latest.getState() != DiagnosticFindingsVersionState.COMPLETED) {
            throw stateConflict();
        }
        DiagnosticReportJpaEntity correction = new DiagnosticReportJpaEntity(UUID.randomUUID(),
                requestId, request.visitId(), studyRef, latest.getVersionNumber() + 1, body, actor.accountId(),
                Instant.now());
        correction.setCorrectionReason(reason);
        correction.recordCorrectionKey(key, fingerprint);
        reports.saveAndFlush(correction);
        audit(request, actor, "CREATE_FINDINGS_CORRECTION", correlationId, Map.of(
                "findingsVersionId", correction.getId().toString(), "version", correction.getVersionNumber(),
                "correctionReason", reason));
        return findingsView(correction);
    }

    @Override
    @Transactional
    public DiagnosticReportView uploadReport(DiagnosticActor actor, UUID requestId, String idempotencyKey,
            long expectedVersion, DiagnosticReportUpload upload,
            String correlationId) {
        ImagingExecution request = lockRequest(requestId);
        requireDoctorAccess(actor, request);
        if (upload == null || upload.content() == null || upload.contentLength() <= 0
                || !"application/pdf".equalsIgnoreCase(upload.contentType())) {
            throw reportInvalid();
        }
        String studyRef = requireCurrentStudy(request, request.studyRef());
        VisitEvidence visit = requireVisitEvidence(actor, request);
        if (!actor.accountId().equals(visit.treatingDoctorId())) throw unauthorized();
        requireReviewed(request);
        DiagnosticReportJpaEntity latest = latestFindings(requestId);
        int version = latest == null ? 1 : latest.getVersionNumber();
        if (latest != null && latest.getState() == DiagnosticFindingsVersionState.COMPLETED) {
            throw stateConflict();
        }
        String contentSha256 = hashUpload(upload);
        String fingerprint = fingerprint(studyRef + "\n" + expectedVersion + "\n" + upload.contentLength()
                + "\n" + version + "\n" + contentSha256);
        String key = requiredText(idempotencyKey, 255, "Idempotency-Key");
        DiagnosticReportJpaEntity replay = reports.findByRequestIdAndUploadIdempotencyKey(requestId, key).orElse(null);
        if (replay != null) return replayReport(replay, replay.getUploadFingerprint(), fingerprint);
        requireVersion(request, expectedVersion);
        ReportStoragePort storage = reportPorts.getIfAvailable();
        if (storage == null) throw dependencyUnavailable();
        UUID reportId = UUID.randomUUID();
        MessageDigest digest = sha256();
        StoredReport stored;
        CountingInputStream countedContent;
        try (InputStream rawContent = upload.content().open()) {
            PushbackInputStream checkedContent = new PushbackInputStream(rawContent, 5);
            byte[] signature = checkedContent.readNBytes(5);
            if (signature.length != 5 || signature[0] != '%' || signature[1] != 'P'
                    || signature[2] != 'D' || signature[3] != 'F' || signature[4] != '-') {
                throw reportInvalid();
            }
            checkedContent.unread(signature);
            countedContent = new CountingInputStream(checkedContent);
            InputStream digestStream = new DigestInputStream(countedContent, digest);
            try {
                stored = storage.storePrivateReport(requestId, request.visitId(), studyRef, version,
                        digestStream, upload.contentLength());
            } catch (RuntimeException exception) {
                throw dependencyUnavailable();
            }
        } catch (IOException exception) {
            throw reportInvalid();
        }
        String storedSha256 = HexFormat.of().formatHex(digest.digest());
        if (countedContent.count != upload.contentLength() || !contentSha256.equals(storedSha256)) throw reportInvalid();
        if (!matchesStoredReport(stored, request, studyRef, version)) throw evidenceMismatch();
        ReportValidationStatus validation = stored.validationStatus();
        if (validation == ReportValidationStatus.PENDING) {
            try {
                validation = storage.validateStoredReport(stored.reportId(), stored.storageRef());
            } catch (RuntimeException exception) {
                validation = ReportValidationStatus.PENDING;
            }
        }
        if (validation == null) validation = ReportValidationStatus.PENDING;
        DiagnosticReportValidationStatus persistedStatus = DiagnosticReportValidationStatus.valueOf(validation.name());
        if (latest == null) {
            latest = new DiagnosticReportJpaEntity(UUID.randomUUID(), requestId, request.visitId(), studyRef,
                    version, null, actor.accountId(), Instant.now());
        }
        latest.attachReport(stored.reportId(), stored.storageRef(), storedSha256, persistedStatus,
                actor.accountId(), Instant.now(),
                persistedStatus == DiagnosticReportValidationStatus.PENDING ? null : Instant.now());
        latest.recordUploadKey(key, fingerprint);
        reports.saveAndFlush(latest);
        audit(request, actor, "UPLOAD_CLINIC_REPORT", correlationId,
                Map.of("reportId", stored.reportId().toString(), "validationStatus", persistedStatus.name()));
        return new DiagnosticReportView(stored.reportId(), persistedStatus.name(), version);
    }

    @Override
    @Transactional
    public DiagnosticFindingsReleaseView complete(DiagnosticActor actor, UUID requestId, String idempotencyKey,
            long expectedVersion, int findingsVersion, UUID reportId, String studyRef, String correlationId) {
        ImagingExecution request = lockRequest(requestId);
        requireDoctorAccess(actor, request);
        String fingerprint = fingerprint(findingsVersion + "\n" + reportId + "\n" + studyRef + "\n" + expectedVersion);
        String key = requiredText(idempotencyKey, 255, "Idempotency-Key");
        DiagnosticReportJpaEntity replay = reports.findByRequestIdAndCompleteIdempotencyKey(requestId, key).orElse(null);
        if (replay != null) {
            if (!fingerprint.equals(replay.getCompleteFingerprint())) {
                throw new DiagnosticOperationException(DiagnosticOperationOutcome.IDEMPOTENCY_CONFLICT);
            }
            if (replay.getState() != DiagnosticFindingsVersionState.COMPLETED || replay.getReleasedAt() == null) {
                throw stateConflict();
            }
            return new DiagnosticFindingsReleaseView(requestId, replay.getVersionNumber(), "RELEASED");
        }
        requireVersion(request, expectedVersion);
        requireCurrentStudy(request, studyRef);
        VisitEvidence visit = requireVisitEvidence(actor, request);
        if (!actor.accountId().equals(visit.treatingDoctorId())) throw unauthorized();
        requireReviewed(request);
        DiagnosticReportJpaEntity version = reports.findByRequestIdAndReportVersion(requestId, findingsVersion)
                .orElseThrow(() -> new DiagnosticOperationException(DiagnosticOperationOutcome.RECORD_NOT_FOUND));
        if (version.getState() != DiagnosticFindingsVersionState.DRAFT || !studyRef.equals(version.getStudyRef())) {
            throw stateConflict();
        }
        if (!Objects.equals(version.getReportId(), reportId)) throw reportNotReady();
        // UC-50 AF2: the PDF report is optional; without one the findings text alone completes the version.
        if (reportId != null && version.getValidationStatus() == DiagnosticReportValidationStatus.PENDING) {
            ReportStoragePort storage = reportPorts.getIfAvailable();
            if (storage == null) throw dependencyUnavailable();
            ReportValidationStatus validation;
            try {
                validation = storage.validateStoredReport(reportId, version.getStorageRef());
            } catch (RuntimeException exception) {
                throw reportNotReady();
            }
            if (validation != null && validation != ReportValidationStatus.PENDING) {
                version.setValidationStatus(DiagnosticReportValidationStatus.valueOf(validation.name()));
                if (validation == ReportValidationStatus.VALIDATED) version.setValidatedAt(Instant.now());
            }
        }
        if ((reportId != null && (version.getValidationStatus() != DiagnosticReportValidationStatus.VALIDATED
                    || version.getContentSha256() == null))
                || !version.getVisitId().equals(request.visitId()) || !studyRef.equals(version.getStudyRef())
                || version.getReportVersion() != findingsVersion || version.getFindingsText() == null) {
            throw reportNotReady();
        }
        UUID eventId = UUID.nameUUIDFromBytes(("FINDINGS_RELEASE:" + requestId + ":" + key)
                .getBytes(StandardCharsets.UTF_8));
        FindingsReleaseEvent event = new FindingsReleaseEvent(eventId, requestId, request.visitId(),
                request.patientId(), studyRef, version.getId(), reportId, version.getContentSha256());
        String payloadSha256 = releasePayloadSha256(event);
        Instant releasedAt = Instant.now();
        version.recordCompleteKey(key, fingerprint);
        version.markCompleted(reportId, releasedAt);
        reports.saveAndFlush(version);

        VisitDiagnosticPort visitPort = visitPorts.getIfAvailable();
        ImagingStudyPort studyPort = studyPorts.getIfAvailable();
        if (visitPort == null || studyPort == null) throw dependencyUnavailable();
        try {
            requireReleaseAck(visitPort.publishFindingsReleased(event), event, payloadSha256);
            requireReleaseAck(studyPort.acceptFindingsRelease(event), event, payloadSha256);
        } catch (DiagnosticOperationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw dependencyUnavailable();
        }

        audit(request, actor, "COMPLETE_FINDINGS", correlationId,
                Map.of("eventId", eventId.toString(), "findingsVersionId", version.getId().toString(),
                        "reportId", Objects.toString(reportId, "none"), "releaseState", "RELEASED"));
        return new DiagnosticFindingsReleaseView(requestId, findingsVersion, "RELEASED");
    }

    private ImagingExecution lockRequest(UUID requestId) {
        return executions.findForUpdate(requestId)
                .orElseThrow(() -> new DiagnosticOperationException(DiagnosticOperationOutcome.RECORD_NOT_FOUND));
    }

    private void requireDoctorAccess(DiagnosticActor actor, ImagingExecution request) {
        // UC-50: ordering doctor or current responsible doctor; the visit port decides which one the actor is.
        if (actor == null || actor.kind() != DiagnosticActor.Kind.USER || actor.role() != AccountRole.DOCTOR) throw unauthorized();
        // Replays also require active current care; knowing an old idempotency key grants no access.
        requireVisitEvidence(actor, request);
    }

    private VisitEvidence requireVisitEvidence(DiagnosticActor actor, ImagingExecution request) {
        VisitDiagnosticPort port = visitPorts.getIfAvailable();
        if (port == null) throw dependencyUnavailable();
        VisitEvidence evidence;
        try {
            evidence = port.requireFindingsAccess(request.visitId(), actor.accountId(), request.orderingDoctorAccountId());
        } catch (DiagnosticOperationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw dependencyUnavailable();
        }
        if (evidence == null || !request.visitId().equals(evidence.visitId())
                || !request.patientId().equals(evidence.patientId()) || !evidence.examinationStarted()) {
            throw evidenceMismatch();
        }
        if (!actor.accountId().equals(evidence.treatingDoctorId())) throw unauthorized();
        if (!evidence.open() || !evidence.eligible()) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.VISIT_NOT_ELIGIBLE);
        }
        return evidence;
    }

    private String requireCurrentStudy(ImagingExecution request, String studyRef) {
        if (request.attemptId() == null || request.studyRef() == null || request.studyRef().isBlank()
                || (request.status() != ExecutionStatus.RESULT_AVAILABLE && request.status() != ExecutionStatus.REVIEWED)) {
            throw stateConflict();
        }
        if (studyRef == null || !studyRef.equals(request.studyRef())) throw evidenceMismatch();
        return request.studyRef();
    }

    private DiagnosticReportJpaEntity latestFindings(UUID requestId) {
        return reports.findFirstByRequestIdOrderByReportVersionDesc(requestId).orElse(null);
    }

    private void requireReviewed(ImagingExecution request) {
        if (request.status() != ExecutionStatus.REVIEWED) throw stateConflict();
    }

    private DiagnosticFindingsView replayFindings(DiagnosticReportJpaEntity row, String storedFingerprint,
            String fingerprint) {
        if (!fingerprint.equals(storedFingerprint)) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.IDEMPOTENCY_CONFLICT);
        }
        return findingsView(row);
    }

    private DiagnosticReportView replayReport(DiagnosticReportJpaEntity row, String storedFingerprint,
            String fingerprint) {
        if (!fingerprint.equals(storedFingerprint)) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.IDEMPOTENCY_CONFLICT);
        }
        return new DiagnosticReportView(row.getReportId(), row.getValidationStatus().name(), row.getReportVersion());
    }

    private DiagnosticFindingsView findingsView(DiagnosticReportJpaEntity row) {
        String releaseState = row.getReleasedAt() == null ? "PRIVATE" : "RELEASED";
        return new DiagnosticFindingsView(row.getRequestId(), row.getId(), row.getVersionNumber(),
                row.getState().name(), releaseState, row.getFindingsText(), row.getCorrectionReason(),
                row.getReportId(), row.getRecordedAt() == null ? row.getOccurredAt() : row.getRecordedAt(),
                row.getCompletedAt());
    }

    private void audit(ImagingExecution request, DiagnosticActor actor, String action,
            String correlationId, Map<String, Object> metadata) {
        Map<String, Object> auditMetadata = new LinkedHashMap<>(metadata);
        auditMetadata.put("correlationRef", requiredText(correlationId, 128, "correlationId"));
        auditWriter.recordIfAbsent(AuditEntry.builder("diagnostic_audit_events", "DIAGNOSTIC",
                        action + ":ALLOWED", "DIAGNOSTIC_REQUEST")
                .entityId(request.orderId()).recordLabel(request.orderId().toString())
                .actorAccountId(actor.accountId()).actorType("ACCOUNT").metadata(auditMetadata)
                .outcome("ALLOWED").occurredAt(Instant.now())
                .dedupKey("diagnostic-findings:" + request.orderId() + ":" + action + ":" + correlationId)
                .build());
    }

    private String releasePayloadSha256(FindingsReleaseEvent event) {
        String canonical = String.join("\n", event.eventId().toString(), event.requestId().toString(),
                event.visitId().toString(), event.patientId().toString(), event.studyRef(),
                event.findingsVersionId().toString(), Objects.toString(event.reportId(), "<null>"),
                Objects.toString(event.reportSha256(), "<null>"));
        return HexFormat.of().formatHex(sha256().digest(canonical.getBytes(StandardCharsets.UTF_8)));
    }

    private void requireReleaseAck(ReleaseAcknowledgement ack, FindingsReleaseEvent event, String payloadSha256) {
        if (ack == null || !ack.accepted() || !event.eventId().equals(ack.eventId())
                || !event.requestId().equals(ack.requestId()) || !payloadSha256.equals(ack.payloadSha256())) {
            throw dependencyUnavailable();
        }
    }

    private boolean matchesStoredReport(StoredReport stored, ImagingExecution request,
            String studyRef, int version) {
        return stored != null && stored.reportId() != null && request.orderId().equals(stored.requestId())
                && request.visitId().equals(stored.visitId()) && studyRef.equals(stored.studyRef())
                && version == stored.reportVersion() && isPrivateStorageRef(stored.storageRef());
    }

    private boolean isPrivateStorageRef(String storageRef) {
        if (storageRef == null || storageRef.isBlank()) return false;
        String normalized = storageRef.strip().toLowerCase(Locale.ROOT);
        return !normalized.startsWith("http://") && !normalized.startsWith("https://")
                && !normalized.startsWith("//");
    }

    private void requireVersion(ImagingExecution request, long expectedVersion) {
        if (request.executionVersion() != expectedVersion) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.VERSION_CONFLICT);
        }
    }

    private String fingerprint(String content) {
        return HexFormat.of().formatHex(sha256().digest(content.getBytes(StandardCharsets.UTF_8)));
    }

    private String hashUpload(DiagnosticReportUpload upload) {
        MessageDigest digest = sha256();
        try (InputStream input = upload.content().open()) {
            byte[] buffer = new byte[8192];
            long count = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                count += read;
                if (count > upload.contentLength()) throw reportInvalid();
                digest.update(buffer, 0, read);
            }
            if (count != upload.contentLength()) throw reportInvalid();
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException exception) {
            throw reportInvalid();
        }
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String requiredText(String value, int maxLength, String field) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(field + " is required and must be at most " + maxLength + " characters");
        }
        return value.trim();
    }

    private DiagnosticOperationException unauthorized() {
        return new DiagnosticOperationException(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD);
    }

    private DiagnosticOperationException evidenceMismatch() {
        return new DiagnosticOperationException(DiagnosticOperationOutcome.EVIDENCE_MISMATCH);
    }

    private DiagnosticOperationException dependencyUnavailable() {
        return new DiagnosticOperationException(DiagnosticOperationOutcome.DEPENDENCY_UNAVAILABLE);
    }

    private DiagnosticOperationException reportNotReady() {
        return new DiagnosticOperationException(DiagnosticOperationOutcome.REPORT_NOT_READY);
    }

    private DiagnosticOperationException reportInvalid() {
        return new DiagnosticOperationException(DiagnosticOperationOutcome.EVIDENCE_MISMATCH);
    }

    private DiagnosticOperationException stateConflict() {
        return new DiagnosticOperationException(DiagnosticOperationOutcome.STATE_CONFLICT);
    }

    private static final class CountingInputStream extends FilterInputStream {
        private long count;

        private CountingInputStream(InputStream input) {
            super(input);
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) count++;
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int read = super.read(bytes, offset, length);
            if (read > 0) count += read;
            return read;
        }
    }
}
