package fu.fa26.sep490.g58.otcms_be.imaging.service.impl;

import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingConfirmationOutboxJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingIngestOperationJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingReconciliationCaseJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationState;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingConfirmationOutboxRepository;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingIngestOperationRepository;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingReconciliationCaseRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Short transactions for durable confirmation delivery outcomes. */
@Service
public class ImagingConfirmationOutboxPersistenceService {
    private final ImagingConfirmationOutboxRepository outbox;
    private final ImagingIngestOperationRepository operations;
    private final ImagingReconciliationCaseRepository reconciliation;

    public ImagingConfirmationOutboxPersistenceService(ImagingConfirmationOutboxRepository outbox,
            ImagingIngestOperationRepository operations, ImagingReconciliationCaseRepository reconciliation) {
        this.outbox = outbox; this.operations = operations; this.reconciliation = reconciliation;
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
    }

    @Transactional
    public void retry(UUID outboxId, Instant nextAt, String safeCode) {
        ImagingConfirmationOutboxJpaEntity event = outbox.findById(outboxId).orElseThrow();
        event.scheduleRetry(nextAt, safeCode);
        outbox.save(event);
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
        if (reconciliation.findAllByOperationIdAndStateNot(operation.getId(), "RESOLVED").isEmpty()) {
            reconciliation.save(new ImagingReconciliationCaseJpaEntity(UUID.randomUUID(), operation.getId(),
                    null, "CONFIRMATION", "OPEN", safeCode, "IMAGING_OPERATIONS", Instant.now(), null, null));
        }
    }
}
