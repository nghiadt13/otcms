package fu.fa26.sep490.g58.otcms_be.visit.integration.billing;

import fu.fa26.sep490.g58.otcms_be.billing.integration.VisitFeeSettlementPort;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.visit.model.VisitExamStatus;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitExamQueueService;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Admits a walk-in to the exam queue when its VISIT invoice is paid in full (plans/261111-0208-visit-flow-r3, B5). */
@Component
public class VisitFeeSettlementAdapter implements VisitFeeSettlementPort {
    private final CoordinationGuardService coordinationGuard;
    private final VisitExamQueueService examQueue;
    private final Clock clock;

    public VisitFeeSettlementAdapter(CoordinationGuardService coordinationGuard, VisitExamQueueService examQueue,
            Clock clock) {
        this.coordinationGuard = coordinationGuard;
        this.examQueue = examQueue;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void onVisitInvoicePaid(UUID visitId) {
        // The guard is taken before reading the row: admission assigns max+1 and must not race a cancel.
        coordinationGuard.acquire();
        var entry = examQueue.find(visitId);
        // Only a walk-in still waiting for its fee is admitted. Any other state is left alone and is not an error,
        // because throwing here would roll back the payment that already happened.
        if (entry.isEmpty() || entry.get().examStatus() != VisitExamStatus.PENDING_PAYMENT) {
            return;
        }
        examQueue.admitAfterPayment(visitId, clock.instant());
    }
}
