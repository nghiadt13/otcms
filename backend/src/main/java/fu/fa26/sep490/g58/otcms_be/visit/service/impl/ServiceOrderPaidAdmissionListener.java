package fu.fa26.sep490.g58.otcms_be.visit.service.impl;

import fu.fa26.sep490.g58.otcms_be.billing.event.ServiceOrderPaidEvent;
import fu.fa26.sep490.g58.otcms_be.visit.service.ServiceQueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Puts a paid service order into its room queue after the payment commits. A failure here must never undo or fail
 * the payment, so it is logged and reception can still dispatch or transfer the order by hand.
 */
@Component
public class ServiceOrderPaidAdmissionListener {
    private static final Logger log = LoggerFactory.getLogger(ServiceOrderPaidAdmissionListener.class);
    private final ServiceQueueService serviceQueue;

    public ServiceOrderPaidAdmissionListener(ServiceQueueService serviceQueue) { this.serviceQueue = serviceQueue; }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onServiceOrderPaid(ServiceOrderPaidEvent event) {
        try {
            serviceQueue.admitPaidOrder(event.serviceOrderId());
        } catch (RuntimeException failure) {
            log.warn("Room admission of paid service order {} failed", event.serviceOrderId(), failure);
        }
    }
}
