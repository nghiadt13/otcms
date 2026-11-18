package fu.fa26.sep490.g58.otcms_be.visit.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Placeholder consumer: only logs. The result-sheet listener of phase 06 replaces it. */
@Component
public class VisitCompletedEventLogger {
    private static final Logger log = LoggerFactory.getLogger(VisitCompletedEventLogger.class);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVisitCompleted(VisitCompletedEvent event) {
        log.info("Visit {} completed by doctor {}", event.visitId(), event.doctorId());
    }
}
