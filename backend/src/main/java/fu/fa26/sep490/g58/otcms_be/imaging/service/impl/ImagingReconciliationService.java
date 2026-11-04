package fu.fa26.sep490.g58.otcms_be.imaging.service.impl;

import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingReconciliationCaseJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingReconciliationCaseRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Opens one durable operator case per unresolved workflow source/category. */
@Service
public class ImagingReconciliationService {
    private final ImagingReconciliationCaseRepository cases;

    public ImagingReconciliationService(ImagingReconciliationCaseRepository cases) { this.cases = cases; }

    @Transactional
    public void openOperation(UUID operationId, String category, String safeReason) {
        if (cases.findAllByOperationIdAndStateNot(operationId, "RESOLVED").stream()
                .anyMatch(existing -> existing.getCategory().equals(category))) return;
        cases.saveAndFlush(new ImagingReconciliationCaseJpaEntity(UUID.randomUUID(), operationId, null,
                category, "OPEN", safeReason, "IMAGING_OPERATIONS", Instant.now(), null, null));
    }

    @Transactional
    public void openEvent(UUID eventId, String category, String safeReason) {
        if (cases.findAllByEventIdAndStateNot(eventId, "RESOLVED").stream()
                .anyMatch(existing -> existing.getCategory().equals(category))) return;
        cases.saveAndFlush(new ImagingReconciliationCaseJpaEntity(UUID.randomUUID(), null, eventId,
                category, "OPEN", safeReason, "IMAGING_OPERATIONS", Instant.now(), null, null));
    }
}
