package fu.fa26.sep490.g58.otcms_be.imaging.service.impl;

import fu.fa26.sep490.g58.otcms_be.imaging.service.ImagingReconciliationService;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Scans a bounded, rotating slice of open cases and closes only cases with durable linked evidence. */
@Component
@ConditionalOnProperty(prefix = "otcms.imaging", name = "enabled", havingValue = "true")
public class ImagingReconciliationWorker {
    private static final UUID START = new UUID(0L, 0L);
    private final ImagingReconciliationService reconciliation;
    private UUID cursor = START;

    public ImagingReconciliationWorker(ImagingReconciliationService reconciliation) {
        this.reconciliation = reconciliation;
    }

    @Scheduled(fixedDelayString = "${otcms.imaging.reconciliation-poll-delay-ms:15000}")
    public void reconcileDueCases() {
        UUID next = reconciliation.reconcileOpenBatch(cursor);
        if (next == null && !START.equals(cursor)) {
            cursor = START;
            next = reconciliation.reconcileOpenBatch(cursor);
        }
        cursor = next == null ? START : next;
    }
}
