package fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl;

import fu.fa26.sep490.g58.otcms_be.common.audit.AuditEntry;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditLogWriter;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort.ExecutionStatus;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort.ImagingExecution;
import fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.StudyEvidence;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.StudyVerificationStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.UseEvidence;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.UseEvidenceReceipt;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.ClinicalImagingOrderPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.FirstUseEvidencePort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.ImagingStudyPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.VisitDiagnosticPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticAttempt;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticEvidenceContext;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticRequest;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticRequestView;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticAction;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticRequestStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticAuthorizationPolicy;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticEvidenceService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiagnosticEvidenceServiceImpl implements DiagnosticEvidenceService {
    private final ClinicalServiceOrderExecutionPort executions;
    private final AuditLogWriter auditWriter;
    private final DiagnosticAuthorizationPolicy authorization;
    private final ObjectProvider<FirstUseEvidencePort> firstUsePorts;
    private final ObjectProvider<ImagingStudyPort> studyPorts;
    private final ObjectProvider<VisitDiagnosticPort> visitPorts;
    private final ObjectProvider<ClinicalImagingOrderPort> orderPorts;

    public DiagnosticEvidenceServiceImpl(ClinicalServiceOrderExecutionPort executions, AuditLogWriter auditWriter,
            DiagnosticAuthorizationPolicy authorization,
            ObjectProvider<FirstUseEvidencePort> firstUsePorts, ObjectProvider<ImagingStudyPort> studyPorts,
            ObjectProvider<VisitDiagnosticPort> visitPorts,
            ObjectProvider<ClinicalImagingOrderPort> orderPorts) {
        this.executions = Objects.requireNonNull(executions, "executions");
        this.auditWriter = Objects.requireNonNull(auditWriter, "auditWriter");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.firstUsePorts = Objects.requireNonNull(firstUsePorts, "firstUsePorts");
        this.studyPorts = Objects.requireNonNull(studyPorts, "studyPorts");
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

        Optional<ImagingExecution> existingEvent = executions.findByFirstUseEventId(evidence.eventId());
        if (existingEvent.isPresent()) {
            ImagingExecution existing = existingEvent.get();
            if (!matches(existing, evidence)) throw evidenceMismatch();
            return toView(existing);
        }

        ImagingExecution request = executions.findForUpdate(evidence.requestId())
                .orElseThrow(() -> new DiagnosticOperationException(DiagnosticOperationOutcome.RECORD_NOT_FOUND));
        if (request.roomId() == null) throw new DiagnosticOperationException(DiagnosticOperationOutcome.ROOM_NOT_READY);

        Optional<ImagingExecution> concurrentFirstUse = executions.findByFirstUseEventId(evidence.eventId());
        if (concurrentFirstUse.isPresent()) {
            if (!matches(concurrentFirstUse.get(), evidence)) throw evidenceMismatch();
            return toView(request);
        }

        if (request.status() == ExecutionStatus.CANCELLED) {
            verifyFirstUseSource(evidence);
            recordAudit(evidence.eventId(), request, "LATE_FIRST_USE_RECONCILIATION", evidence.actorId(),
                    evidence.sourceId(), normalizeTimestamp(evidence.occurredAt()), correlationId,
                    usePayload(evidence), DiagnosticOperationOutcome.STATE_CONFLICT);
            return toView(request);
        }

        if (request.attemptId() != null) {
            if (!request.attemptId().equals(evidence.attemptId())) throw evidenceMismatch();
            verifyFirstUseSource(evidence);
            recordAudit(evidence.eventId(), request, "RECORD_SUBSEQUENT_USE_EVIDENCE", evidence.actorId(),
                    evidence.sourceId(), normalizeTimestamp(evidence.occurredAt()), correlationId,
                    usePayload(evidence), DiagnosticOperationOutcome.ALLOWED);
            return toView(request);
        }
        if (request.status() != ExecutionStatus.PENDING) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.STATE_CONFLICT);
        }

        verifyFirstUseSource(evidence);

        DiagnosticEvidenceContext context = new DiagnosticEvidenceContext(request.orderId(), request.visitId(),
                request.patientId(), null, evidence.eventId(), evidence.attemptId(), true,
                false, false, false, false);
        authorization.requireAllowed(toDomain(request), DiagnosticAction.RECORD_FIRST_USE, actor,
                context, request.executionVersion());

        Instant occurredAt = normalizeTimestamp(evidence.occurredAt());
        int changed = executions.recordFirstUse(request.orderId(), request.executionVersion(), evidence.attemptId(),
                evidence.actorId(), evidence.type().name(), evidence.eventId(), occurredAt,
                evidence.sourceId().trim());
        if (changed == 0) {
            ImagingExecution current = executions.find(request.orderId()).orElseThrow(this::dependencyUnavailable);
            if (matches(current, evidence)) return toView(current);
            if (current.executionVersion() != request.executionVersion()) {
                throw new DiagnosticOperationException(DiagnosticOperationOutcome.VERSION_CONFLICT);
            }
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.STATE_CONFLICT);
        }
        ImagingExecution updated = executions.find(request.orderId()).orElseThrow(this::dependencyUnavailable);
        recordAudit(evidence.eventId(), updated, "RECORD_FIRST_USE", evidence.actorId(), evidence.sourceId(),
                normalizeTimestamp(evidence.occurredAt()), correlationId,
                Map.of("attemptId", evidence.attemptId().toString(), "evidenceType", evidence.type().name()),
                DiagnosticOperationOutcome.ALLOWED);
        return toView(updated);
    }

    @Override
    @Transactional
    public DiagnosticRequestView linkConfirmedStudy(DiagnosticActor actor, StudyEvidence evidence,
            String correlationId) {
        requireTrustedIntegration(actor);
        requireText(correlationId, "correlationId");
        requireStudyEvidence(evidence);

        ImagingExecution request = executions.findForUpdate(evidence.requestId())
                .orElseThrow(() -> new DiagnosticOperationException(DiagnosticOperationOutcome.RECORD_NOT_FOUND));
        if (!request.visitId().equals(evidence.visitId()) || !request.patientId().equals(evidence.patientId())
                || request.attemptId() == null || !request.attemptId().equals(evidence.attemptId())) {
            throw evidenceMismatch();
        }

        ImagingStudyPort studyPort = studyPorts.getIfAvailable();
        if (studyPort == null) throw dependencyUnavailable();
        StudyEvidence confirmed = studyPort.confirmIngestedStudy(evidence.eventId(), evidence.studyRef(),
                evidence.requestId(), evidence.visitId(), evidence.patientId(), evidence.attemptId(),
                evidence.verificationEvidence());
        if (!isConfirmedMatch(confirmed, evidence)) throw evidenceMismatch();

        if ((request.status() == ExecutionStatus.RESULT_AVAILABLE || request.status() == ExecutionStatus.REVIEWED)
                && evidence.studyRef().equals(request.studyRef())) {
            recordAudit(evidence.eventId(), request, "LINK_CONFIRMED_STUDY", null, "FEAT-004",
                    Instant.now(), correlationId, studyPayload(evidence), DiagnosticOperationOutcome.ALLOWED);
            return toView(request);
        }
        if (request.status() != ExecutionStatus.IN_PROGRESS) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.STATE_CONFLICT);
        }

        DiagnosticEvidenceContext context = new DiagnosticEvidenceContext(request.orderId(), request.visitId(),
                request.patientId(), null, evidence.eventId(), evidence.attemptId(), false,
                true, false, false, false);
        authorization.requireAllowed(toDomain(request), DiagnosticAction.LINK_CONFIRMED_STUDY,
                actor, context, request.executionVersion());

        int changed = executions.linkStudy(request.orderId(), request.executionVersion(), evidence.attemptId(),
                evidence.studyRef());
        if (changed == 0) {
            ImagingExecution current = executions.find(request.orderId()).orElseThrow(this::dependencyUnavailable);
            if (current.status() == ExecutionStatus.RESULT_AVAILABLE
                    && evidence.attemptId().equals(current.attemptId())
                    && evidence.studyRef().equals(current.studyRef())) return toView(current);
            if (current.executionVersion() != request.executionVersion()) {
                throw new DiagnosticOperationException(DiagnosticOperationOutcome.VERSION_CONFLICT);
            }
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.STATE_CONFLICT);
        }
        ImagingExecution updated = executions.find(request.orderId()).orElseThrow(this::dependencyUnavailable);
        // Keep the clinical order in step, so the visit's completion evidence sees the result.
        ClinicalImagingOrderPort orderPort = orderPorts.getIfAvailable();
        if (orderPort == null) throw dependencyUnavailable();
        orderPort.markResultAvailable(updated.orderId());
        Map<String, Object> payload = studyPayload(evidence);
        VisitDiagnosticPort visitPort = visitPorts.getIfAvailable();
        if (visitPort == null) throw dependencyUnavailable();
        visitPort.publishResultReady(updated.orderId(), updated.visitId(), evidence.eventId());
        recordAudit(evidence.eventId(), updated, "LINK_CONFIRMED_STUDY", null, "FEAT-004",
                Instant.now(), correlationId, payload, DiagnosticOperationOutcome.ALLOWED);
        return toView(updated);
    }

    private void recordAudit(UUID eventId, ImagingExecution request, String action, UUID actorId,
            String sourceId, Instant occurredAt, String correlationId, Map<String, Object> metadata,
            DiagnosticOperationOutcome outcome) {
        Map<String, Object> auditMetadata = new LinkedHashMap<>(metadata);
        if (eventId != null) auditMetadata.put("eventId", eventId.toString());
        if (sourceId != null) auditMetadata.put("sourceId", sourceId);
        if (correlationId != null) auditMetadata.put("correlationRef", correlationId);
        String dedupKey = eventId == null ? null : "diagnostic-execution:" + action + ":" + eventId;
        auditWriter.recordIfAbsent(AuditEntry.builder("diagnostic_audit_events", "DIAGNOSTIC",
                        action + ":" + outcome.name(), "DIAGNOSTIC_REQUEST")
                .entityId(request.orderId()).recordLabel(request.orderId().toString())
                .actorAccountId(actorId).actorType(actorId == null ? "SYSTEM" : "ACCOUNT")
                .metadata(auditMetadata).outcome(outcome.name()).occurredAt(occurredAt)
                .dedupKey(dedupKey).build());
    }

    private void verifyFirstUseSource(UseEvidence evidence) {
        FirstUseEvidencePort source = firstUsePorts.getIfAvailable();
        if (source == null) throw dependencyUnavailable();
        UseEvidenceReceipt receipt = source.recordFirstUseEvidence(evidence);
        if (receipt == null || !receipt.accepted() || !evidence.eventId().equals(receipt.eventId())
                || !evidence.requestId().equals(receipt.requestId())
                || !evidence.attemptId().equals(receipt.attemptId())) throw evidenceMismatch();
    }

    private DiagnosticRequestView toView(ImagingExecution request) {
        return new DiagnosticRequestView(request.orderId(), request.visitId(), request.patientId(),
                request.orderingDoctorAccountId(), request.serviceId(), request.roomId(), request.imagingType(),
                request.bodyArea(), request.clinicalReason(), DiagnosticRequestStatus.valueOf(request.status().name()),
                request.cancellationCause(), request.cancellationReason(), request.studyRef(),
                request.executionVersion(), request.createdAt());
    }

    private DiagnosticRequest toDomain(ImagingExecution request) {
        DiagnosticAttempt attempt = request.attemptId() == null ? null : new DiagnosticAttempt(request.attemptId(),
                request.orderId(), 1, request.firstUseEventId(), request.firstUseAt(), request.studyRef());
        return new DiagnosticRequest(request.orderId(), request.visitId(), request.patientId(),
                request.orderingDoctorAccountId(), request.serviceId(), request.imagingType(),
                DiagnosticRequestStatus.valueOf(request.status().name()), attempt, request.executionVersion(),
                request.createdAt());
    }

    private boolean matches(ImagingExecution execution, UseEvidence evidence) {
        return execution.orderId().equals(evidence.requestId())
                && Objects.equals(execution.attemptId(), evidence.attemptId())
                && Objects.equals(execution.firstUseEventId(), evidence.eventId())
                && Objects.equals(execution.firstUseType(), evidence.type().name())
                && Objects.equals(execution.firstUseSourceId(), evidence.sourceId().trim())
                && Objects.equals(execution.technicianAccountId(), evidence.actorId())
                && Objects.equals(execution.firstUseAt(), normalizeTimestamp(evidence.occurredAt()));
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
