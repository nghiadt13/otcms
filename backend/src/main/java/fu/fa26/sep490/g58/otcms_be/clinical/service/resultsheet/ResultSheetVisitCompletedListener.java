package fu.fa26.sep490.g58.otcms_be.clinical.service.resultsheet;

import fu.fa26.sep490.g58.otcms_be.visit.event.VisitCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ResultSheetVisitCompletedListener {
    private static final Logger log = LoggerFactory.getLogger(ResultSheetVisitCompletedListener.class);
    private final ResultSheetIssuer issuer;

    public ResultSheetVisitCompletedListener(ResultSheetIssuer issuer) { this.issuer = issuer; }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCompleted(VisitCompletedEvent event) {
        try {
            issuer.issue(event.visitId(), event.doctorId());
        } catch (RuntimeException failure) {
            log.error("Could not issue result sheet for visit {} ({})", event.visitId(), failure.getClass().getSimpleName());
        }
    }
}
