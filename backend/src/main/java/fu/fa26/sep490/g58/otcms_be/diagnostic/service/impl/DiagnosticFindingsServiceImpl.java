package fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.FindingsReleaseEvent;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ReportValidationStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.StoredReport;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.VisitEvidence;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.ReportStoragePort;
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
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticAuditEventJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticAttemptJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticFindingsVersionJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticIdempotencyRecordJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticOutboxJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticReportJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticRequestJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticAction;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticFindingsVersionState;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticReportValidationStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticRequestStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticAttemptJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticAuditEventJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticFindingsVersionJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticIdempotencyRecordJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticOutboxJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticReportJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticRequestJpaRepository;
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
    private static final String RELEASE_EVENT = "FINDINGS_RELEASE";

    private final DiagnosticRequestJpaRepository requests;
    private final DiagnosticAttemptJpaRepository attempts;
    private final DiagnosticFindingsVersionJpaRepository findings;
    private final DiagnosticReportJpaRepository reports;
    private final DiagnosticIdempotencyRecordJpaRepository idempotency;
    private final DiagnosticAuditEventJpaRepository auditEvents;
    private final DiagnosticOutboxJpaRepository outbox;
    private final DiagnosticAuthorizationPolicy authorization;
    private final ObjectProvider<VisitDiagnosticPort> visitPorts;
    private final ObjectProvider<ReportStoragePort> reportPorts;

    public DiagnosticFindingsServiceImpl(DiagnosticRequestJpaRepository requests,
            DiagnosticAttemptJpaRepository attempts, DiagnosticFindingsVersionJpaRepository findings,
            DiagnosticReportJpaRepository reports, DiagnosticIdempotencyRecordJpaRepository idempotency,
            DiagnosticAuditEventJpaRepository auditEvents, DiagnosticOutboxJpaRepository outbox,
            DiagnosticAuthorizationPolicy authorization, ObjectProvider<VisitDiagnosticPort> visitPorts,
            ObjectProvider<ReportStoragePort> reportPorts) {
        this.requests = Objects.requireNonNull(requests, "requests");
        this.attempts = Objects.requireNonNull(attempts, "attempts");
        this.findings = Objects.requireNonNull(findings, "findings");
        this.reports = Objects.requireNonNull(reports, "reports");
        this.idempotency = Objects.requireNonNull(idempotency, "idempotency");
        this.auditEvents = Objects.requireNonNull(auditEvents, "auditEvents");
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.visitPorts = Objects.requireNonNull(visitPorts, "visitPorts");
        this.reportPorts = Objects.requireNonNull(reportPorts, "reportPorts");
    }

    @Override
    @Transactional
    public DiagnosticRequestView review(DiagnosticActor actor, UUID requestId, String idempotencyKey,
            long expectedVersion, String studyRef, String correlationId) {
        DiagnosticRequestJpaEntity request = lockRequest(requestId);
        requireDoctorAccess(actor, request);
        String fingerprint = fingerprint(studyRef + "\n" + expectedVersion);
        DiagnosticIdempotencyRecordJpaEntity replay = claim(actor, request, REVIEW, idempotencyKey, fingerprint);
        if (replay != null) return requestView(request);
        requireVersion(request, expectedVersion);
        DiagnosticAttemptJpaEntity attempt = requireCurrentStudy(request, studyRef);
        authorization.requireAllowed(toDomain(request, attempt), DiagnosticAction.REVIEW_RESULT, actor,
                evidence(request, attempt), expectedVersion);
        VisitEvidence visit = requireVisitEvidence(actor, request);
        if (!actor.accountId().equals(visit.treatingDoctorId())) throw unauthorized();
        request.setStatus(DiagnosticRequestStatus.REVIEWED);
        requests.saveAndFlush(request);
        saveResult(actor, request, REVIEW, idempotencyKey,
                Map.of("requestId", requestId.toString(), "version", request.getVersion()));
        audit(request, actor, "REVIEW_RESULT", correlationId, Map.of("studyRef", studyRef));
        return requestView(request);
    }

    @Override
    @Transactional
    public DiagnosticFindingsView saveDraft(DiagnosticActor actor, UUID requestId, String idempotencyKey,
            long expectedVersion, String content, String correlationId) {
        DiagnosticRequestJpaEntity request = lockRequest(requestId);
        requireDoctorAccess(actor, request);
        String body = requiredText(content, 20000, "content");
        String studyRef = currentStudy(request).getStudyRef();
        String fingerprint = fingerprint(body + "\n" + expectedVersion + "\n" + studyRef);
        DiagnosticIdempotencyRecordJpaEntity replay = claim(actor, request, DRAFT, idempotencyKey, fingerprint);
        if (replay != null) return findingsReplay(requestId, replay);
        requireVersion(request, expectedVersion);
        VisitEvidence visit = requireVisitEvidence(actor, request);
        if (!actor.accountId().equals(visit.treatingDoctorId())) throw unauthorized();
        requireReviewed(request);
        DiagnosticFindingsVersionJpaEntity latest = latestFindings(requestId);
        if (latest != null && latest.getState() == DiagnosticFindingsVersionState.COMPLETED) {
            throw stateConflict();
        }
        if (latest == null) {
            latest = new DiagnosticFindingsVersionJpaEntity(UUID.randomUUID(), requestId, request.getVisitId(),
                    studyRef, 1, body, actor.accountId(), Instant.now(), DiagnosticFindingsVersionState.DRAFT,
                    null, null);
        } else {
            if (!studyRef.equals(latest.getStudyRef())) throw evidenceMismatch();
            latest.reviseDraft(body, Instant.now());
        }
        findings.saveAndFlush(latest);
        saveResult(actor, request, DRAFT, idempotencyKey,
                Map.of("findingsVersionId", latest.getId().toString(), "fingerprint", fingerprint));
        audit(request, actor, "SAVE_FINDINGS_DRAFT", correlationId,
                Map.of("findingsVersionId", latest.getId().toString(), "version", latest.getVersionNumber()));
        return findingsView(latest, "PRIVATE");
    }

    @Override
    @Transactional
    public DiagnosticFindingsView createCorrection(DiagnosticActor actor, UUID requestId, String idempotencyKey,
            long expectedVersion, String content, String correctionReason, String correlationId) {
        DiagnosticRequestJpaEntity request = lockRequest(requestId);
        requireDoctorAccess(actor, request);
        String body = requiredText(content, 20000, "content");
        String reason = requiredText(correctionReason, 2000, "correctionReason");
        String studyRef = currentStudy(request).getStudyRef();
        String fingerprint = fingerprint(body + "\n" + reason + "\n" + expectedVersion + "\n" + studyRef);
        DiagnosticIdempotencyRecordJpaEntity replay = claim(actor, request, CORRECTION, idempotencyKey, fingerprint);
        if (replay != null) return findingsReplay(requestId, replay);
        requireVersion(request, expectedVersion);
        VisitEvidence visit = requireVisitEvidence(actor, request);
        if (!actor.accountId().equals(visit.treatingDoctorId())) throw unauthorized();
        requireReviewed(request);
        DiagnosticFindingsVersionJpaEntity latest = latestFindings(requestId);
        if (latest == null || latest.getState() != DiagnosticFindingsVersionState.COMPLETED) {
            throw stateConflict();
        }
        DiagnosticFindingsVersionJpaEntity correction = new DiagnosticFindingsVersionJpaEntity(UUID.randomUUID(),
                requestId, request.getVisitId(), studyRef, latest.getVersionNumber() + 1, body, actor.accountId(),
                Instant.now(), DiagnosticFindingsVersionState.DRAFT, null, null);
        correction.setCorrectionReason(reason);
        findings.saveAndFlush(correction);
        saveResult(actor, request, CORRECTION, idempotencyKey,
                Map.of("findingsVersionId", correction.getId().toString(), "fingerprint", fingerprint));
        audit(request, actor, "CREATE_FINDINGS_CORRECTION", correlationId, Map.of(
                "findingsVersionId", correction.getId().toString(), "version", correction.getVersionNumber(),
                "correctionReason", reason));
        return findingsView(correction, "PRIVATE");
    }

    @Override
    @Transactional
    public DiagnosticReportView uploadReport(DiagnosticActor actor, UUID requestId, String idempotencyKey,
            long expectedVersion, DiagnosticReportUpload upload,
            String correlationId) {
        DiagnosticRequestJpaEntity request = lockRequest(requestId);
        requireDoctorAccess(actor, request);
        if (upload == null || upload.content() == null || upload.contentLength() <= 0
                || !"application/pdf".equalsIgnoreCase(upload.contentType())) {
            throw reportInvalid();
        }
        String studyRef = currentStudy(request).getStudyRef();
        VisitEvidence visit = requireVisitEvidence(actor, request);
        if (!actor.accountId().equals(visit.treatingDoctorId())) throw unauthorized();
        requireReviewed(request);
        DiagnosticFindingsVersionJpaEntity latest = latestFindings(requestId);
        int version = latest == null ? 1 : latest.getVersionNumber();
        if (latest != null && latest.getState() == DiagnosticFindingsVersionState.COMPLETED) {
            throw stateConflict();
        }
        String contentSha256 = hashUpload(upload);
        String fingerprint = fingerprint(studyRef + "\n" + expectedVersion + "\n" + upload.contentLength()
                + "\n" + version + "\n" + contentSha256);
        DiagnosticIdempotencyRecordJpaEntity replay = claim(actor, request, UPLOAD_REPORT, idempotencyKey, fingerprint);
        if (replay != null) return reportReplay(requestId, replay);
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
                stored = storage.storePrivateReport(requestId, request.getVisitId(), studyRef, version,
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
        DiagnosticReportJpaEntity report = new DiagnosticReportJpaEntity(stored.reportId(), requestId,
                request.getVisitId(), studyRef, version, stored.storageRef(),
                storedSha256, persistedStatus, actor.accountId(),
                persistedStatus == DiagnosticReportValidationStatus.PENDING ? null : Instant.now(), null);
        reports.saveAndFlush(report);
        saveResult(actor, request, UPLOAD_REPORT, idempotencyKey,
                Map.of("reportId", report.getId().toString(), "fingerprint", fingerprint));
        audit(request, actor, "UPLOAD_CLINIC_REPORT", correlationId,
                Map.of("reportId", report.getId().toString(), "validationStatus", persistedStatus.name()));
        return new DiagnosticReportView(report.getId(), persistedStatus.name(), version);
    }

    @Override
    @Transactional
    public DiagnosticFindingsReleaseView complete(DiagnosticActor actor, UUID requestId, String idempotencyKey,
            long expectedVersion, int findingsVersion, UUID reportId, String studyRef, String correlationId) {
        DiagnosticRequestJpaEntity request = lockRequest(requestId);
        requireDoctorAccess(actor, request);
        String fingerprint = fingerprint(findingsVersion + "\n" + reportId + "\n" + studyRef + "\n" + expectedVersion);
        DiagnosticIdempotencyRecordJpaEntity replay = claim(actor, request, COMPLETE, idempotencyKey, fingerprint);
        if (replay != null) {
            UUID eventId = UUID.fromString(text(replay.getResultPayload(), "eventId"));
            String state = outbox.findByEventId(eventId)
                    .map(event -> event.getStatus().name().equals("DELIVERED") ? "RELEASED" : "RELEASE_PENDING")
                    .orElse("RELEASE_PENDING");
            return new DiagnosticFindingsReleaseView(requestId,
                    number(replay.getResultPayload(), "findingsVersion"), state);
        }
        requireVersion(request, expectedVersion);
        DiagnosticAttemptJpaEntity attempt = requireCurrentStudy(request, studyRef);
        VisitEvidence visit = requireVisitEvidence(actor, request);
        if (!actor.accountId().equals(visit.treatingDoctorId())) throw unauthorized();
        requireReviewed(request);
        DiagnosticFindingsVersionJpaEntity version = findings.findAllByRequestIdOrderByVersionNumberDesc(requestId)
                .stream().filter(candidate -> candidate.getVersionNumber() == findingsVersion).findFirst()
                .orElseThrow(() -> new DiagnosticOperationException(DiagnosticOperationOutcome.RECORD_NOT_FOUND));
        if (version.getState() != DiagnosticFindingsVersionState.DRAFT || !studyRef.equals(version.getStudyRef())) {
            throw stateConflict();
        }
        DiagnosticReportJpaEntity report = reports.findByIdAndRequestId(reportId, requestId)
                .orElseThrow(this::reportNotReady);
        if (report.getValidationStatus() == DiagnosticReportValidationStatus.PENDING) {
            ReportStoragePort storage = reportPorts.getIfAvailable();
            if (storage == null) throw dependencyUnavailable();
            ReportValidationStatus validation;
            try {
                validation = storage.validateStoredReport(reportId, report.getStorageRef());
            } catch (RuntimeException exception) {
                throw reportNotReady();
            }
            if (validation != null && validation != ReportValidationStatus.PENDING) {
                report.setValidationStatus(DiagnosticReportValidationStatus.valueOf(validation.name()));
                if (validation == ReportValidationStatus.VALIDATED) report.setValidatedAt(Instant.now());
                reports.saveAndFlush(report);
            }
        }
        if (report.getValidationStatus() != DiagnosticReportValidationStatus.VALIDATED
                || !report.getVisitId().equals(request.getVisitId()) || !studyRef.equals(report.getStudyRef())
                || report.getReportVersion() != findingsVersion) throw reportNotReady();
        UUID eventId = UUID.randomUUID();
        Map<String, Object> payload = releasePayload(eventId, request, version, report);
        outbox.save(new DiagnosticOutboxJpaEntity(UUID.randomUUID(), eventId, requestId, request.getVisitId(),
                RELEASE_EVENT, payload, fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOutboxStatus.PENDING,
                0, Instant.now(), null, null));
        saveResult(actor, request, COMPLETE, idempotencyKey, Map.of(
                "eventId", eventId.toString(), "findingsVersion", findingsVersion, "releaseState", "RELEASE_PENDING"));
        audit(request, actor, "REQUEST_FINDINGS_RELEASE", correlationId,
                Map.of("eventId", eventId.toString(), "findingsVersionId", version.getId().toString(),
                        "reportId", reportId.toString(), "releaseState", "RELEASE_PENDING"));
        return new DiagnosticFindingsReleaseView(requestId, findingsVersion, "RELEASE_PENDING");
    }

    private DiagnosticRequestJpaEntity lockRequest(UUID requestId) {
        return requests.findByIdForUpdate(requestId)
                .orElseThrow(() -> new DiagnosticOperationException(DiagnosticOperationOutcome.RECORD_NOT_FOUND));
    }

    private void requireDoctorAccess(DiagnosticActor actor, DiagnosticRequestJpaEntity request) {
        if (actor == null || actor.kind() != DiagnosticActor.Kind.USER || actor.role() != AccountRole.DOCTOR
                || !actor.accountId().equals(request.getOrderingDoctorId())) throw unauthorized();
        // Replays also require active current care; knowing an old idempotency key grants no access.
        requireVisitEvidence(actor, request);
    }

    private VisitEvidence requireVisitEvidence(DiagnosticActor actor, DiagnosticRequestJpaEntity request) {
        VisitDiagnosticPort port = visitPorts.getIfAvailable();
        if (port == null) throw dependencyUnavailable();
        VisitEvidence evidence;
        try {
            evidence = port.requireImagingOrderEligibility(request.getVisitId(), actor.accountId());
        } catch (DiagnosticOperationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw dependencyUnavailable();
        }
        if (evidence == null || !request.getVisitId().equals(evidence.visitId())
                || !request.getPatientId().equals(evidence.patientId()) || !evidence.examinationStarted()) {
            throw evidenceMismatch();
        }
        if (!actor.accountId().equals(evidence.treatingDoctorId())) throw unauthorized();
        if (!evidence.open() || !evidence.eligible()) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.VISIT_NOT_ELIGIBLE);
        }
        return evidence;
    }

    private DiagnosticAttemptJpaEntity currentStudy(DiagnosticRequestJpaEntity request) {
        if (request.getCurrentAttemptId() == null) throw stateConflict();
        DiagnosticAttemptJpaEntity attempt = attempts.findByRequestIdAndId(request.getId(), request.getCurrentAttemptId())
                .orElseThrow(this::dependencyUnavailable);
        if (attempt.getStudyRef() == null || attempt.getStudyRef().isBlank()) throw stateConflict();
        return attempt;
    }

    private DiagnosticAttemptJpaEntity requireCurrentStudy(DiagnosticRequestJpaEntity request, String studyRef) {
        DiagnosticAttemptJpaEntity attempt = currentStudy(request);
        if (studyRef == null || !studyRef.equals(attempt.getStudyRef())) throw evidenceMismatch();
        return attempt;
    }

    private DiagnosticFindingsVersionJpaEntity latestFindings(UUID requestId) {
        return findings.findFirstByRequestIdOrderByVersionNumberDesc(requestId).orElse(null);
    }

    private void requireReviewed(DiagnosticRequestJpaEntity request) {
        if (request.getStatus() != DiagnosticRequestStatus.REVIEWED) throw stateConflict();
    }

    private DiagnosticFindingsView findingsReplay(UUID requestId, DiagnosticIdempotencyRecordJpaEntity record) {
        String id = text(record.getResultPayload(), "findingsVersionId");
        DiagnosticFindingsVersionJpaEntity version = findings.findByIdAndRequestId(UUID.fromString(id), requestId)
                .orElseThrow(this::dependencyUnavailable);
        return findingsView(version, version.getState() == DiagnosticFindingsVersionState.COMPLETED ? "RELEASED" : "PRIVATE");
    }

    private DiagnosticReportView reportReplay(UUID requestId, DiagnosticIdempotencyRecordJpaEntity record) {
        DiagnosticReportJpaEntity report = reports.findByIdAndRequestId(
                UUID.fromString(text(record.getResultPayload(), "reportId")), requestId)
                .orElseThrow(this::dependencyUnavailable);
        return new DiagnosticReportView(report.getId(), report.getValidationStatus().name(), report.getReportVersion());
    }

    private DiagnosticFindingsView findingsView(DiagnosticFindingsVersionJpaEntity version, String releaseState) {
        return new DiagnosticFindingsView(version.getRequestId(), version.getId(), version.getVersionNumber(),
                version.getState().name(), releaseState, version.getFindingsText(), version.getCorrectionReason(),
                version.getRequiredPdfReportId(), version.getRecordedAt(), version.getCompletedAt());
    }

    private DiagnosticRequestView requestView(DiagnosticRequestJpaEntity request) {
        DiagnosticAttemptJpaEntity attempt = request.getCurrentAttemptId() == null ? null
                : attempts.findByRequestIdAndId(request.getId(), request.getCurrentAttemptId()).orElse(null);
        return new DiagnosticRequestView(request.getId(), request.getVisitId(), request.getPatientId(),
                request.getOrderingDoctorId(), request.getServiceId(), request.getRoomId(), request.getImagingType(),
                request.getBodyArea(), request.getClinicalReason(), request.getStatus(), request.getCancellationCause(),
                request.getCancellationReason(), attempt == null ? null : attempt.getStudyRef(), request.getVersion(),
                request.getCreatedAt());
    }

    private DiagnosticRequest toDomain(DiagnosticRequestJpaEntity request, DiagnosticAttemptJpaEntity attempt) {
        DiagnosticAttempt domainAttempt = new DiagnosticAttempt(attempt.getId(), attempt.getRequestId(),
                attempt.getAttemptNumber(), attempt.getFirstUseEventId(), attempt.getOccurredAt(), attempt.getStudyRef());
        return new DiagnosticRequest(request.getId(), request.getVisitId(), request.getPatientId(),
                request.getOrderingDoctorId(), request.getServiceId(), request.getImagingType(), request.getStatus(),
                domainAttempt, request.getVersion(), request.getCreatedAt());
    }

    private DiagnosticEvidenceContext evidence(DiagnosticRequestJpaEntity request,
            DiagnosticAttemptJpaEntity attempt) {
        return new DiagnosticEvidenceContext(request.getId(), request.getVisitId(), request.getPatientId(),
                request.getOrderingDoctorId(), null, attempt.getId(), false, true, false, false, false);
    }

    private DiagnosticIdempotencyRecordJpaEntity claim(DiagnosticActor actor, DiagnosticRequestJpaEntity request,
            String operation, String key, String fingerprint) {
        requiredText(key, 255, "Idempotency-Key");
        if (idempotency.claimActorOperation(UUID.randomUUID(), actor.accountId(), request.getVisitId(),
                request.getId(), operation, key, fingerprint) != 1) {
            DiagnosticIdempotencyRecordJpaEntity existing = idempotency.findActorOperation(actor.accountId(),
                    request.getVisitId(), request.getId(), operation, key).orElseThrow(this::dependencyUnavailable);
            if (!fingerprint.equals(existing.getPayloadFingerprint())) {
                throw new DiagnosticOperationException(DiagnosticOperationOutcome.IDEMPOTENCY_CONFLICT);
            }
            return existing;
        }
        return null;
    }

    private void saveResult(DiagnosticActor actor, DiagnosticRequestJpaEntity request, String operation,
            String key, Map<String, Object> payload) {
        DiagnosticIdempotencyRecordJpaEntity record = idempotency.findActorOperation(actor.accountId(),
                request.getVisitId(), request.getId(), operation, key).orElseThrow(this::dependencyUnavailable);
        // Keep the managed record coherent for another call in the same transaction.
        record.completeResult(payload);
        idempotency.saveAndFlush(record);
    }

    private void audit(DiagnosticRequestJpaEntity request, DiagnosticActor actor, String action,
            String correlationId, Map<String, Object> metadata) {
        auditEvents.save(new DiagnosticAuditEventJpaEntity(UUID.randomUUID(), UUID.randomUUID(), actor.accountId(), null,
                request.getId(), request.getVisitId(), action, DiagnosticOperationOutcome.ALLOWED.name(), Instant.now(),
                requiredText(correlationId, 128, "correlationId"), metadata));
    }

    private Map<String, Object> releasePayload(UUID eventId, DiagnosticRequestJpaEntity request,
            DiagnosticFindingsVersionJpaEntity version, DiagnosticReportJpaEntity report) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", eventId.toString());
        payload.put("requestId", request.getId().toString());
        payload.put("visitId", request.getVisitId().toString());
        payload.put("patientId", request.getPatientId().toString());
        payload.put("studyRef", version.getStudyRef());
        payload.put("findingsVersionId", version.getId().toString());
        payload.put("reportId", report.getId().toString());
        payload.put("reportSha256", report.getContentSha256());
        return payload;
    }

    private boolean matchesStoredReport(StoredReport stored, DiagnosticRequestJpaEntity request,
            String studyRef, int version) {
        return stored != null && stored.reportId() != null && request.getId().equals(stored.requestId())
                && request.getVisitId().equals(stored.visitId()) && studyRef.equals(stored.studyRef())
                && version == stored.reportVersion() && isPrivateStorageRef(stored.storageRef());
    }

    private boolean isPrivateStorageRef(String storageRef) {
        if (storageRef == null || storageRef.isBlank()) return false;
        String normalized = storageRef.strip().toLowerCase(Locale.ROOT);
        return !normalized.startsWith("http://") && !normalized.startsWith("https://")
                && !normalized.startsWith("//");
    }

    private void requireVersion(DiagnosticRequestJpaEntity request, long expectedVersion) {
        if (request.getVersion() != expectedVersion) {
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

    private String text(Map<String, Object> payload, String field) {
        Object value = payload.get(field);
        if (!(value instanceof String text)) throw dependencyUnavailable();
        return text;
    }

    private int number(Map<String, Object> payload, String field) {
        Object value = payload.get(field);
        if (value instanceof Number number) return number.intValue();
        throw dependencyUnavailable();
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
