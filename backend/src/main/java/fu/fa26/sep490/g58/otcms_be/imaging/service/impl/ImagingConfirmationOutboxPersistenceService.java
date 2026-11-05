package fu.fa26.sep490.g58.otcms_be.imaging.service.impl;

import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingConfirmationOutboxJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingIngestOperationJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationState;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingConfirmationOutboxRepository;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingIngestOperationRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Short transactions for durable confirmation delivery outcomes. */
@Service
public class ImagingConfirmationOutboxPersistenceService {
    private final ImagingConfirmationOutboxRepository outbox;
    private final ImagingIngestOperationRepository operations;
    private final ImagingReconciliationService reconciliation;
    private final ImagingAuditPersistenceService audit;

    public ImagingConfirmationOutboxPersistenceService(ImagingConfirmationOutboxRepository outbox,
            ImagingIngestOperationRepository operations, ImagingReconciliationService reconciliation,
            ImagingAuditPersistenceService audit) {
        this.outbox = outbox; this.operations = operations; this.reconciliation = reconciliation;
        this.audit = audit;
    }

    @Transactional
    public void acknowledge(UUID outboxId) {
        ImagingConfirmationOutboxJpaEntity event = outbox.findById(outboxId).orElseThrow();
        ImagingIngestOperationJpaEntity operation = operations.findById(event.getOperationId()).orElseThrow();
        event.markAcknowledged(Instant.now());
        outbox.save(event);
        int changed = operations.transitionStateIfVersionMatches(operation.getId(), operation.getVersion(),
                operation.getState(), ImagingOperationState.LINKED, null, Instant.now());
        if (changed != 1) throw new IllegalStateException("Confirmation operation changed concurrently");
        record(event, operation, "ACKNOWLEDGED", null);
    }

    @Transactional
    public void retry(UUID outboxId, Instant nextAt, String safeCode) {
        ImagingConfirmationOutboxJpaEntity event = outbox.findById(outboxId).orElseThrow();
        ImagingIngestOperationJpaEntity operation = operations.findById(event.getOperationId()).orElseThrow();
        event.scheduleRetry(nextAt, safeCode);
        outbox.save(event);
        record(event, operation, "RETRY_SCHEDULED", safeCode);
    }

    @Transactional
    public void reconcile(UUID outboxId, String safeCode) {
        ImagingConfirmationOutboxJpaEntity event = outbox.findById(outboxId).orElseThrow();
        ImagingIngestOperationJpaEntity operation = operations.findById(event.getOperationId()).orElseThrow();
        event.markRejected(safeCode);
        outbox.save(event);
        int changed = operations.transitionStateIfVersionMatches(operation.getId(), operation.getVersion(),
                operation.getState(), ImagingOperationState.RECONCILIATION_REQUIRED, safeCode, Instant.now());
        if (changed != 1) throw new IllegalStateException("Confirmation reconciliation changed concurrently");
        reconciliation.openOperation(operation.getId(), "CONFIRMATION", safeCode);
        record(event, operation, "RECONCILIATION_REQUIRED", safeCode);
    }

    private void record(ImagingConfirmationOutboxJpaEntity event, ImagingIngestOperationJpaEntity operation,
            String outcome, String safeReasonCode) {
        var record = operation.toModel();
        java.util.Map<String, Object> metadata = safeReasonCode == null
                ? java.util.Map.of("eventId", event.getEventId().toString())
                : java.util.Map.of("eventId", event.getEventId().toString(), "reasonCode", safeReasonCode);
        audit.recordService("FEAT_004", "IMAGING_CONFIRMATION", outcome, operation.getId(), event.getStudyRef(),
                event.getRequestId(), event.getAttemptId(), event.getVisitId(), event.getPatientId(),
                event.getEventId().toString(), metadata);
    }
}
