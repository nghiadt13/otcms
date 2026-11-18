package fu.fa26.sep490.g58.otcms_be.visit.service.impl;

import fu.fa26.sep490.g58.otcms_be.visit.event.VisitCompletedEvent;
import fu.fa26.sep490.g58.otcms_be.visit.service.ServiceQueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Clears the patient from the room queues once the doctor closed the visit; never fails the closing. */
@Component
public class ServiceQueueVisitCompletedListener {
    private static final Logger log = LoggerFactory.getLogger(ServiceQueueVisitCompletedListener.class);
    private final ServiceQueueService serviceQueue;

    public ServiceQueueVisitCompletedListener(ServiceQueueService serviceQueue) { this.serviceQueue = serviceQueue; }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVisitCompleted(VisitCompletedEvent event) {
        try {
            serviceQueue.closeEntriesOfVisit(event.visitId());
        } catch (RuntimeException failure) {
            log.warn("Could not close the room queue entries of completed visit {}", event.visitId(), failure);
        }
    }
}
