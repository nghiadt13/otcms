package fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl;

import fu.fa26.sep490.g58.otcms_be.billing.service.BillingQueryService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort.ExecutionStatus;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditEntry;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditLogWriter;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticAcquisitionService;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticStatusService;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.service.ServiceQueueService;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiagnosticAcquisitionServiceImpl implements DiagnosticAcquisitionService {
    private final ClinicalServiceOrderExecutionPort executions;
    private final DiagnosticStatusService statuses;
    private final AuditLogWriter audit;
    private final BillingQueryService billing;
    private final ServiceQueueService serviceQueue;

    public DiagnosticAcquisitionServiceImpl(ClinicalServiceOrderExecutionPort executions,
            DiagnosticStatusService statuses, AuditLogWriter audit, BillingQueryService billing,
            ServiceQueueService serviceQueue) {
        this.executions = executions;
        this.statuses = statuses;
        this.audit = audit;
        this.billing = billing;
        this.serviceQueue = serviceQueue;
    }

    @Override
    @Transactional
    public UUID start(DiagnosticActor actor, UUID requestId) {
        if (actor == null || actor.kind() != DiagnosticActor.Kind.USER
                || actor.role() != AccountRole.IMAGING_TECHNICIAN) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD);
        }
        var execution = executions.findForUpdate(requestId)
                .orElseThrow(() -> new DiagnosticOperationException(DiagnosticOperationOutcome.RECORD_NOT_FOUND));
        statuses.getStatus(actor, requestId);
        if (execution.status() == ExecutionStatus.IN_PROGRESS && execution.attemptId() != null) {
            return execution.attemptId();
        }
        if (execution.status() != ExecutionStatus.PENDING || execution.attemptId() != null) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.STATE_CONFLICT);
        }
        // D7 (MSG-95): an order whose fee is not paid cannot be accepted by the technician.
        if (!billing.isServiceOrderPaid(requestId)) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.ORDER_NOT_PAID);
        }
        // UC-46: the patient must have been called from the room queue (MSG-42 otherwise).
        try {
            serviceQueue.markInProgress(requestId);
        } catch (VisitOperationException notCalled) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.PATIENT_NOT_CALLED);
        }
        UUID attemptId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();
        if (executions.recordFirstUse(requestId, execution.executionVersion(), attemptId, actor.accountId(),
                "ACQUISITION_STARTED", eventId, now, "TECHNICIAN_CONFIRMATION") != 1) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.STATE_CONFLICT);
        }
        audit.recordIfAbsent(AuditEntry.builder("diagnostic_audit_events", "DIAGNOSTIC",
                        "RECORD_FIRST_USE:ALLOWED", "DIAGNOSTIC_REQUEST")
                .entityId(requestId).recordLabel(requestId.toString()).actorAccountId(actor.accountId())
                .actorType("ACCOUNT").metadata(Map.of("attemptId", attemptId.toString(),
                        "evidenceType", "ACQUISITION_STARTED", "sourceId", "TECHNICIAN_CONFIRMATION"))
                .outcome("ALLOWED").occurredAt(now).dedupKey(eventId.toString()).build());
        return attemptId;
    }

    @Override
    @Transactional
    public void reportCannotPerform(DiagnosticActor actor, UUID requestId, String reason) {
        if (actor == null || actor.kind() != DiagnosticActor.Kind.USER || actor.role() != AccountRole.IMAGING_TECHNICIAN) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD);
        }
        if (reason == null || reason.isBlank() || reason.length() > 2000) {
            throw new IllegalArgumentException("A reason of at most 2000 characters is required");
        }
        var execution = executions.findForUpdate(requestId)
                .orElseThrow(() -> new DiagnosticOperationException(DiagnosticOperationOutcome.RECORD_NOT_FOUND));
        statuses.getStatus(actor, requestId);
        if (execution.status() != ExecutionStatus.PENDING) {
            throw new DiagnosticOperationException(DiagnosticOperationOutcome.STATE_CONFLICT);
        }
        serviceQueue.leave(requestId);
        audit.recordIfAbsent(AuditEntry.builder("diagnostic_audit_events", "DIAGNOSTIC",
                        "SERVICE_NOT_PERFORMED:ALLOWED", "DIAGNOSTIC_REQUEST")
                .entityId(requestId).recordLabel(requestId.toString()).actorAccountId(actor.accountId())
                .actorType("ACCOUNT").metadata(Map.of("reason", reason.trim())).outcome("ALLOWED")
                .occurredAt(Instant.now()).dedupKey("not-performed:" + requestId + ":" + UUID.randomUUID()).build());
    }
}
