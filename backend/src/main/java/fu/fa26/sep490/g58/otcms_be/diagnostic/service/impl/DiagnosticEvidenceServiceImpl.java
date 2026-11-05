package fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl;

import fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.StudyEvidence;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.StudyVerificationStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.UseEvidence;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.UseEvidenceReceipt;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.FindingsReleaseEvent;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ReleaseAcknowledgement;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.ClinicalImagingOrderPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.FirstUseEvidencePort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.ImagingBillingPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.ImagingStudyPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.VisitDiagnosticPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticAttempt;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticEvidenceContext;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticRequest;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticRequestView;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticAttemptJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticAuditEventJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticOutboxJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticFindingsVersionJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticReportJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticRequestJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticAction;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOutboxStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticRequestStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticAttemptJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticAuditEventJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticOutboxJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticFindingsVersionJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticReportJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticRequestJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticAuthorizationPolicy;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticEvidenceService;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiagnosticEvidenceServiceImpl implements DiagnosticEvidenceService {
    private static final String BILLING_EVENT = "FIRST_USE_BILLING_OBLIGATION";
    private static final String RESULT_READY_EVENT = "IMAGING_RESULT_READY";
    private static final String FINDINGS_RELEASE_EVENT = "FINDINGS_RELEASE";

    private final DiagnosticRequestJpaRepository requests;
    private final DiagnosticAttemptJpaRepository attempts;
    private final DiagnosticAuditEventJpaRepository auditEvents;
    private final DiagnosticOutboxJpaRepository outbox;
    private final DiagnosticFindingsVersionJpaRepository findings;
    private final DiagnosticReportJpaRepository reports;
    private final DiagnosticAuthorizationPolicy authorization;
    private final ObjectProvider<FirstUseEvidencePort> firstUsePorts;
    private final ObjectProvider<ImagingStudyPort> studyPorts;
    private final ObjectProvider<ImagingBillingPort> billingPorts;
    private final ObjectProvider<VisitDiagnosticPort> visitPorts;
    private final ObjectProvider<ClinicalImagingOrderPort> orderPorts;

    public DiagnosticEvidenceServiceImpl(DiagnosticRequestJpaRepository requests,
            DiagnosticAttemptJpaRepository attempts, DiagnosticAuditEventJpaRepository auditEvents,
            DiagnosticOutboxJpaRepository outbox, DiagnosticFindingsVersionJpaRepository findings,
            DiagnosticReportJpaRepository reports, DiagnosticAuthorizationPolicy authorization,
            ObjectProvider<FirstUseEvidencePort> firstUsePorts, ObjectProvider<ImagingStudyPort> studyPorts,
            ObjectProvider<ImagingBillingPort> billingPorts, ObjectProvider<VisitDiagnosticPort> visitPorts,
            ObjectProvider<ClinicalImagingOrderPort> orderPorts) {
        this.requests = Objects.requireNonNull(requests, "requests");
        this.attempts = Objects.requireNonNull(attempts, "attempts");
        this.auditEvents = Objects.requireNonNull(auditEvents, "auditEvents");
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.findings = Objects.requireNonNull(findings, "findings");
        this.reports = Objects.requireNonNull(reports, "reports");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.firstUsePorts = Objects.requireNonNull(firstUsePorts, "firstUsePorts");
        this.studyPorts = Objects.requireNonNull(studyPorts, "studyPorts");
        this.billingPorts = Objects.requireNonNull(billingPorts, "billingPorts");
        this.visitPorts = Objects.requireNonNull(visitPorts, "visitPorts");
        this.orderPorts = Objects.requireNonNull(orderPorts, "orderPorts");
    }

    @Override
    @Transactional
    public DiagnosticRequestView recordFirstUse(DiagnosticActor actor, UseEvidence evidence,
            String correlationId) {
        requireTrustedIntegration(actor);
        requireText(correlationId, "correlationId");
        requireUseEvidence(evidence);

        Optional<DiagnosticAttemptJpaEntity> existingEvent = attempts.findByFirstUseEventId(evidence.eventId());
        if (existingEvent.isPresent()) {
            DiagnosticAttemptJpaEntity existing = existingEvent.get();
            if (!matches(existing, evidence)) throw evidenceMismatch();
            DiagnosticRequestJpaEntity existingRequest = requests.findById(existing.getRequestId())
                    .orElseThrow(this::dependencyUnavailable);
            return toView(existingRequest);
        }
        Optional<DiagnosticAuditEventJpaEntity> recordedEvent = auditEvents.findByEventId(evidence.eventId());
        if (recordedEvent.isPresent()) {
            DiagnosticAuditEventJpaEntity event = recordedEvent.get();
            if (!("RECORD_SUBSEQUENT_USE_EVIDENCE".equals(event.getAction())
                    || "LATE_FIRST_USE_RECONCILIATION".equals(event.getAction()))
                    || !matchesUsePayload(event.getMetadata(), evidence)) throw evidenceMismatch();
            DiagnosticRequestJpaEntity existingRequest = requests.findById(event.getRequestId())
                    .orElseThrow(this::dependencyUnavailable);
            return toView(existingRequest);
        }

        DiagnosticRequestJpaEntity request = requests.findByIdForUpdate(evidence.requestId())
                .orElseThrow(() -> new DiagnosticOperationException(DiagnosticOperationOutcome.RECORD_NOT_FOUND));
        if (request.getRoomId() == null) throw new DiagnosticOperationException(DiagnosticOperationOutcome.ROOM_NOT_READY);

        Optional<DiagnosticAttemptJpaEntity> concurrentFirstUse = attempts.findByFirstUseEventId(evidence.eventId());
        if (concurrentFirstUse.isPresent()) {
            if (!matches(concurrentFirstUse.get(), evidence)) throw evidenceMismatch();
            return toView(request);
        }
        Optional<DiagnosticAuditEventJpaEntity> concurrentEvidence = auditEvents.findByEventId(evidence.eventId());
        if (concurrentEvidence.isPresent()) {
            DiagnosticAuditEventJpaEntity event = concurrentEvidence.get();
            if (!("RECORD_SUBSEQUENT_USE_EVIDENCE".equals(event.getAction())
                    || "LATE_FIRST_USE_RECONCILIATION".equals(event.getAction()))
                    || !matchesUsePayload(event.getMetadata(), evidence)) throw evidenceMismatch();
            return toView(request);
        }

        if (request.getStatus() == DiagnosticRequestStatus.CANCELLED) {
            verifyFirstUseSource(evidence);
            recordAudit(evidence.eventId(), request, "LATE_FIRST_USE_RECONCILIATION", evidence.actorId(),
                    evidence.sourceId(), normalizeTimestamp(evidence.occurredAt()), correlationId,
                    usePayload(evidence), DiagnosticOperationOutcome.STATE_CONFLICT);
            return toView(request);
        }

        if (request.getCurrentAttemptId() != null) {
            DiagnosticAttemptJpaEntity currentAttempt = attempts.findByRequestIdAndId(request.getId(),
                    request.getCurrentAttemptId()).orElseThrow(this::dependencyUnavailable);
            if (!currentAttempt.getId().equals(evidence.attemptId())) throw evidenceMismatch();
            verifyFirstUseSource(evidence);
            recordAudit(evidence.eventId(), request, "RECORD_SUBSEQUENT_USE_EVIDENCE", evidence.actorId(),
                    evidence.sourceId(), normalizeTimestamp(evidence.occurredAt()), correlationId,
                    usePayload(evidence), DiagnosticOperationOutcome.ALLOWED);
            return toView(request);
        }
        if (request.getStatus() != DiagnosticRequestStatus.PENDING) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.STATE_CONFLICT);
        }

        verifyFirstUseSource(evidence);

        DiagnosticEvidenceContext context = new DiagnosticEvidenceContext(request.getId(), request.getVisitId(),
                request.getPatientId(), null, evidence.eventId(), evidence.attemptId(), true,
                false, false, false, false);
        authorization.requireAllowed(toDomain(request, null), DiagnosticAction.RECORD_FIRST_USE, actor,
                context, request.getVersion());

        DiagnosticAttemptJpaEntity attempt = new DiagnosticAttemptJpaEntity(evidence.attemptId(), request.getId(),
                1, evidence.eventId(), evidence.type().name(), evidence.sourceId().trim(), evidence.actorId(),
                normalizeTimestamp(evidence.occurredAt()), null);
        attempts.saveAndFlush(attempt);
        request.setCurrentAttemptId(attempt.getId());
        request.setStatus(DiagnosticRequestStatus.IN_PROGRESS);
        requests.saveAndFlush(request);
        enqueue(evidence.eventId(), request, BILLING_EVENT, Map.of(
                "eventId", evidence.eventId().toString(),
                "requestId", request.getId().toString(),
                "serviceId", request.getServiceId().toString(),
                "attemptId", attempt.getId().toString(),
                "occurredAt", normalizeTimestamp(evidence.occurredAt()).toString()));
        recordAudit(evidence.eventId(), request, "RECORD_FIRST_USE", evidence.actorId(), evidence.sourceId(),
                normalizeTimestamp(evidence.occurredAt()), correlationId,
                Map.of("attemptId", attempt.getId().toString(), "evidenceType", evidence.type().name()),
                DiagnosticOperationOutcome.ALLOWED);
        return toView(request);
    }

    @Override
    @Transactional
    public DiagnosticRequestView linkConfirmedStudy(DiagnosticActor actor, StudyEvidence evidence,
            String correlationId) {
        requireTrustedIntegration(actor);
        requireText(correlationId, "correlationId");
        requireStudyEvidence(evidence);

        Optional<DiagnosticAuditEventJpaEntity> recordedEvent = auditEvents.findByEventId(evidence.eventId());
        if (recordedEvent.isPresent()) {
            DiagnosticAuditEventJpaEntity event = recordedEvent.get();
            if (!"LINK_CONFIRMED_STUDY".equals(event.getAction())
                    || !matchesStudyPayload(event.getMetadata(), evidence)) throw evidenceMismatch();
            DiagnosticRequestJpaEntity existingRequest = requests.findById(evidence.requestId())
                    .orElseThrow(this::dependencyUnavailable);
            return toView(existingRequest);
        }

        DiagnosticRequestJpaEntity request = requests.findByIdForUpdate(evidence.requestId())
                .orElseThrow(() -> new DiagnosticOperationException(DiagnosticOperationOutcome.RECORD_NOT_FOUND));
        Optional<DiagnosticAuditEventJpaEntity> concurrentEvent = auditEvents.findByEventId(evidence.eventId());
        if (concurrentEvent.isPresent()) {
            DiagnosticAuditEventJpaEntity event = concurrentEvent.get();
            if (!"LINK_CONFIRMED_STUDY".equals(event.getAction())
                    || !matchesStudyPayload(event.getMetadata(), evidence)) throw evidenceMismatch();
            return toView(request);
        }
        DiagnosticAttemptJpaEntity attempt = request.getCurrentAttemptId() == null ? null
                : attempts.findByRequestIdAndId(request.getId(), request.getCurrentAttemptId())
                        .orElseThrow(this::dependencyUnavailable);
        if (!request.getVisitId().equals(evidence.visitId()) || !request.getPatientId().equals(evidence.patientId())
                || attempt == null || !attempt.getId().equals(evidence.attemptId())) throw evidenceMismatch();

        ImagingStudyPort studyPort = studyPorts.getIfAvailable();
        if (studyPort == null) throw dependencyUnavailable();
        StudyEvidence confirmed = studyPort.confirmIngestedStudy(evidence.eventId(), evidence.studyRef(),
                evidence.requestId(), evidence.visitId(), evidence.patientId(), evidence.attemptId(),
                evidence.verificationEvidence());
        if (!isConfirmedMatch(confirmed, evidence)) throw evidenceMismatch();

        DiagnosticEvidenceContext context = new DiagnosticEvidenceContext(request.getId(), request.getVisitId(),
                request.getPatientId(), null, evidence.eventId(), evidence.attemptId(), false,
                true, false, false, false);
        authorization.requireAllowed(toDomain(request, attempt), DiagnosticAction.LINK_CONFIRMED_STUDY,
                actor, context, request.getVersion());

        attempt.setStudyRef(evidence.studyRef());
        attempts.saveAndFlush(attempt);
        request.setStatus(DiagnosticRequestStatus.RESULT_AVAILABLE);
        requests.saveAndFlush(request);
        // Keep the clinical order in step, so the visit's completion evidence sees the result.
        if (request.getServiceOrderId() != null) {
            ClinicalImagingOrderPort orderPort = orderPorts.getIfAvailable();
            if (orderPort == null) throw dependencyUnavailable();
            orderPort.markResultAvailable(request.getServiceOrderId());
        }
        Map<String, Object> payload = studyPayload(evidence);
        recordAudit(evidence.eventId(), request, "LINK_CONFIRMED_STUDY", null, "FEAT-004",
                Instant.now(), correlationId, payload, DiagnosticOperationOutcome.ALLOWED);
        enqueue(evidence.eventId(), request, RESULT_READY_EVENT, Map.of(
                "eventId", evidence.eventId().toString(),
                "requestId", request.getId().toString(),
                "visitId", request.getVisitId().toString()));
        return toView(request);
    }

    @Override
    @Transactional
    public DispatchSummary dispatchDueOutbox(int batchSize, RetryPolicy retryPolicy) {
        if (batchSize < 1 || batchSize > 100 || retryPolicy == null) {
            throw new IllegalArgumentException("batchSize must be 1..100 and retry policy is required");
        }
        List<DiagnosticOutboxJpaEntity> due = outbox.lockDueEvents(DiagnosticOutboxStatus.PENDING,
                Instant.now(), PageRequest.of(0, batchSize));
        int delivered = 0;
        int retryScheduled = 0;
        int deadLettered = 0;
        for (DiagnosticOutboxJpaEntity event : due) {
            try {
                deliver(event);
                event.setStatus(DiagnosticOutboxStatus.DELIVERED);
                event.setDeliveredAt(Instant.now());
                event.setLastError(null);
                delivered++;
            } catch (RuntimeException failure) {
                int nextAttempt = event.getAttemptCount() + 1;
                event.setAttemptCount(nextAttempt);
                event.setLastError(failure.getClass().getSimpleName());
                if (nextAttempt >= retryPolicy.maxAttempts()) {
                    event.setStatus(DiagnosticOutboxStatus.DEAD_LETTER);
                    deadLettered++;
                } else {
                    event.setNextAttemptAt(Instant.now().plusSeconds(backoffSeconds(nextAttempt, retryPolicy)));
                    retryScheduled++;
                }
            }
        }
        outbox.flush();
        return new DispatchSummary(due.size(), delivered, retryScheduled, deadLettered);
    }

    private void deliver(DiagnosticOutboxJpaEntity event) {
        Map<String, Object> payload = event.getPayload();
        if (BILLING_EVENT.equals(event.getEventType())) {
            ImagingBillingPort billing = billingPorts.getIfAvailable();
            if (billing == null) throw dependencyUnavailable();
            UUID obligationId = billing.registerFirstUseObligation(event.getEventId(), event.getRequestId(),
                    uuid(payload, "serviceId"), Instant.parse(text(payload, "occurredAt")));
            if (obligationId == null) throw dependencyUnavailable();
            return;
        }
        if (RESULT_READY_EVENT.equals(event.getEventType())) {
            VisitDiagnosticPort visit = visitPorts.getIfAvailable();
            if (visit == null) throw dependencyUnavailable();
            visit.publishResultReady(event.getRequestId(), event.getVisitId(), event.getEventId());
            return;
        }
        if (FINDINGS_RELEASE_EVENT.equals(event.getEventType())) {
            dispatchFindingsRelease(event);
            return;
        }
        throw new IllegalStateException("Unsupported diagnostic outbox event type");
    }

    private void dispatchFindingsRelease(DiagnosticOutboxJpaEntity outboxEvent) {
        Map<String, Object> payload = outboxEvent.getPayload();
        FindingsReleaseEvent event = new FindingsReleaseEvent(outboxEvent.getEventId(), outboxEvent.getRequestId(),
                outboxEvent.getVisitId(), uuid(payload, "patientId"), text(payload, "studyRef"),
                uuid(payload, "findingsVersionId"), uuid(payload, "reportId"), text(payload, "reportSha256"));
        String payloadSha256 = releasePayloadSha256(event);
        VisitDiagnosticPort visit = visitPorts.getIfAvailable();
        ImagingStudyPort study = studyPorts.getIfAvailable();
        if (visit == null || study == null) throw dependencyUnavailable();
        requireReleaseAck(visit.publishFindingsReleased(event), event, payloadSha256);
        requireReleaseAck(study.acceptFindingsRelease(event), event, payloadSha256);

        DiagnosticRequestJpaEntity request = requests.findByIdForUpdate(event.requestId())
                .orElseThrow(this::dependencyUnavailable);
        if (!request.getVisitId().equals(event.visitId()) || !request.getPatientId().equals(event.patientId())
                || !Objects.equals(currentStudyRef(request), event.studyRef())) throw evidenceMismatch();
        DiagnosticFindingsVersionJpaEntity version = findings.findByIdAndRequestId(
                event.findingsVersionId(), event.requestId()).orElseThrow(this::dependencyUnavailable);
        DiagnosticReportJpaEntity report = reports.findByIdAndRequestId(event.reportId(), event.requestId())
                .orElseThrow(this::dependencyUnavailable);
        if (!version.getStudyRef().equals(event.studyRef())
                || !report.getStudyRef().equals(event.studyRef()) || !report.getVisitId().equals(event.visitId())
                || !report.getContentSha256().equals(event.reportSha256())) throw evidenceMismatch();
        Instant releasedAt = Instant.now();
        report.setReleasedAt(releasedAt);
        reports.saveAndFlush(report);
        version.markReleased(report.getId(), releasedAt);
        findings.saveAndFlush(version);
    }

    private void requireReleaseAck(ReleaseAcknowledgement ack, FindingsReleaseEvent event, String payloadSha256) {
        if (ack == null || !ack.accepted() || !event.eventId().equals(ack.eventId())
                || !event.requestId().equals(ack.requestId()) || !payloadSha256.equals(ack.payloadSha256())) {
            throw dependencyUnavailable();
        }
    }

    private String releasePayloadSha256(FindingsReleaseEvent event) {
        String canonical = String.join("\n", event.eventId().toString(), event.requestId().toString(),
                event.visitId().toString(), event.patientId().toString(), event.studyRef(),
                event.findingsVersionId().toString(), event.reportId().toString(), event.reportSha256());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String currentStudyRef(DiagnosticRequestJpaEntity request) {
        if (request.getCurrentAttemptId() == null) return null;
        return attempts.findByRequestIdAndId(request.getId(), request.getCurrentAttemptId())
                .map(DiagnosticAttemptJpaEntity::getStudyRef).orElse(null);
    }

    private void enqueue(UUID eventId, DiagnosticRequestJpaEntity request, String eventType,
            Map<String, Object> payload) {
        outbox.save(new DiagnosticOutboxJpaEntity(UUID.randomUUID(), eventId, request.getId(), request.getVisitId(),
                eventType, payload, DiagnosticOutboxStatus.PENDING, 0, Instant.now(), null, null));
    }

    private void recordAudit(UUID eventId, DiagnosticRequestJpaEntity request, String action, UUID actorId,
            String sourceId, Instant occurredAt, String correlationId, Map<String, Object> metadata,
            DiagnosticOperationOutcome outcome) {
        auditEvents.save(new DiagnosticAuditEventJpaEntity(UUID.randomUUID(), eventId, actorId,
                actorId == null ? sourceId : null, request.getId(), request.getVisitId(), action,
                outcome.name(), occurredAt, correlationId, metadata));
    }

    private void verifyFirstUseSource(UseEvidence evidence) {
        FirstUseEvidencePort source = firstUsePorts.getIfAvailable();
        if (source == null) throw dependencyUnavailable();
        UseEvidenceReceipt receipt = source.recordFirstUseEvidence(evidence);
        if (receipt == null || !receipt.accepted() || !evidence.eventId().equals(receipt.eventId())
                || !evidence.requestId().equals(receipt.requestId())
                || !evidence.attemptId().equals(receipt.attemptId())) throw evidenceMismatch();
    }

    private DiagnosticRequestView toView(DiagnosticRequestJpaEntity request) {
        DiagnosticAttemptJpaEntity attempt = request.getCurrentAttemptId() == null ? null
                : attempts.findByRequestIdAndId(request.getId(), request.getCurrentAttemptId())
                        .orElseThrow(this::dependencyUnavailable);
        return new DiagnosticRequestView(request.getId(), request.getVisitId(), request.getPatientId(),
                request.getOrderingDoctorId(), request.getServiceId(), request.getRoomId(), request.getImagingType(),
                request.getBodyArea(), request.getClinicalReason(), request.getStatus(),
                request.getCancellationCause(), request.getCancellationReason(), attempt == null ? null : attempt.getStudyRef(),
                request.getVersion(), request.getCreatedAt());
    }

    private DiagnosticRequest toDomain(DiagnosticRequestJpaEntity request, DiagnosticAttemptJpaEntity attempt) {
        DiagnosticAttempt domainAttempt = attempt == null ? null : new DiagnosticAttempt(attempt.getId(),
                attempt.getRequestId(), attempt.getAttemptNumber(), attempt.getFirstUseEventId(),
                attempt.getOccurredAt(), attempt.getStudyRef());
        return new DiagnosticRequest(request.getId(), request.getVisitId(), request.getPatientId(),
                request.getOrderingDoctorId(), request.getServiceId(), request.getImagingType(),
                request.getStatus(), domainAttempt, request.getVersion(), request.getCreatedAt());
    }

    private boolean matches(DiagnosticAttemptJpaEntity attempt, UseEvidence evidence) {
        return attempt.getRequestId().equals(evidence.requestId())
                && attempt.getId().equals(evidence.attemptId())
                && attempt.getFirstUseType().equals(evidence.type().name())
                && attempt.getSourceId().equals(evidence.sourceId().trim())
                && Objects.equals(attempt.getActorAccountId(), evidence.actorId())
                && attempt.getOccurredAt().equals(normalizeTimestamp(evidence.occurredAt()));
    }

    private boolean isConfirmedMatch(StudyEvidence actual, StudyEvidence expected) {
        return actual != null && actual.eventId().equals(expected.eventId())
                && actual.studyRef().equals(expected.studyRef()) && actual.requestId().equals(expected.requestId())
                && actual.visitId().equals(expected.visitId()) && actual.patientId().equals(expected.patientId())
                && actual.attemptId().equals(expected.attemptId()) && actual.ingestComplete()
                && actual.verificationStatus() == StudyVerificationStatus.CONFIRMED_MATCH
                && actual.verificationEvidence().equals(expected.verificationEvidence());
    }

    private Map<String, Object> studyPayload(StudyEvidence evidence) {
        return Map.of("studyRef", evidence.studyRef(), "requestId", evidence.requestId().toString(),
                "visitId", evidence.visitId().toString(), "patientId", evidence.patientId().toString(),
                "attemptId", evidence.attemptId().toString(),
                "verificationEvidence", evidence.verificationEvidence());
    }

    private Map<String, Object> usePayload(UseEvidence evidence) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requestId", evidence.requestId().toString());
        payload.put("attemptId", evidence.attemptId().toString());
        payload.put("evidenceType", evidence.type().name());
        payload.put("sourceId", evidence.sourceId().trim());
        payload.put("actorId", evidence.actorId() == null ? null : evidence.actorId().toString());
        payload.put("occurredAt", normalizeTimestamp(evidence.occurredAt()).toString());
        return payload;
    }

    private boolean matchesUsePayload(Map<String, Object> metadata, UseEvidence evidence) {
        Object actorId = metadata.get("actorId");
        return text(metadata, "requestId").equals(evidence.requestId().toString())
                && text(metadata, "attemptId").equals(evidence.attemptId().toString())
                && text(metadata, "evidenceType").equals(evidence.type().name())
                && text(metadata, "sourceId").equals(evidence.sourceId().trim())
                && Objects.equals(actorId, evidence.actorId() == null ? null : evidence.actorId().toString())
                && text(metadata, "occurredAt").equals(normalizeTimestamp(evidence.occurredAt()).toString());
    }

    private boolean matchesStudyPayload(Map<String, Object> metadata, StudyEvidence evidence) {
        return text(metadata, "studyRef").equals(evidence.studyRef())
                && text(metadata, "requestId").equals(evidence.requestId().toString())
                && text(metadata, "visitId").equals(evidence.visitId().toString())
                && text(metadata, "patientId").equals(evidence.patientId().toString())
                && text(metadata, "attemptId").equals(evidence.attemptId().toString())
                && Objects.equals(metadata.get("verificationEvidence"), evidence.verificationEvidence());
    }

    private void requireUseEvidence(UseEvidence evidence) {
        if (evidence == null || evidence.eventId() == null || evidence.requestId() == null
                || evidence.attemptId() == null || evidence.type() == null || evidence.sourceId() == null
                || evidence.sourceId().isBlank() || evidence.sourceId().length() > 128
                || evidence.occurredAt() == null || evidence.receivedAt() == null
                || !evidence.sourceAuthenticated()) throw evidenceMismatch();
    }

    private void requireStudyEvidence(StudyEvidence evidence) {
        if (evidence == null || evidence.eventId() == null || evidence.studyRef() == null
                || evidence.studyRef().isBlank() || evidence.requestId() == null || evidence.visitId() == null
                || evidence.patientId() == null || evidence.attemptId() == null
                || evidence.verificationStatus() == null || !evidence.ingestComplete()
                || evidence.verificationStatus() != StudyVerificationStatus.CONFIRMED_MATCH) throw evidenceMismatch();
    }

    private void requireTrustedIntegration(DiagnosticActor actor) {
        if (actor == null || actor.kind() != DiagnosticActor.Kind.TRUSTED_INTEGRATION) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD);
        }
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new IllegalArgumentException(field + " is required and must be at most 128 characters");
        }
    }

    private UUID uuid(Map<String, Object> payload, String field) {
        return UUID.fromString(text(payload, field));
    }

    private String text(Map<String, Object> payload, String field) {
        Object value = payload == null ? null : payload.get(field);
        if (!(value instanceof String string)) throw dependencyUnavailable();
        return string;
    }

    private long backoffSeconds(int attempt, RetryPolicy retryPolicy) {
        long multiplier = 1L << Math.min(attempt - 1, 20);
        if (retryPolicy.initialDelaySeconds() > retryPolicy.maxDelaySeconds() / multiplier) {
            return retryPolicy.maxDelaySeconds();
        }
        return Math.min(retryPolicy.initialDelaySeconds() * multiplier, retryPolicy.maxDelaySeconds());
    }

    private Instant normalizeTimestamp(Instant value) {
        return Instant.ofEpochSecond(value.getEpochSecond(), (value.getNano() / 1_000) * 1_000L);
    }

    private DiagnosticOperationException evidenceMismatch() {
        return new DiagnosticOperationException(DiagnosticOperationOutcome.EVIDENCE_MISMATCH);
    }

    private DiagnosticOperationException dependencyUnavailable() {
        return new DiagnosticOperationException(DiagnosticOperationOutcome.DEPENDENCY_UNAVAILABLE);
    }
}
