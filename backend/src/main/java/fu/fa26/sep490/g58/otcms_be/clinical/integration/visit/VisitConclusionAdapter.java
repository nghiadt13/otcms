package fu.fa26.sep490.g58.otcms_be.clinical.integration.visit;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.service.VisitConclusionPort;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitExamQueueService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Moves the Visit-owned exam queue entry. A queue state conflict reaches clinical callers as MSG-42 (409). */
@Component
public class VisitConclusionAdapter implements VisitConclusionPort {
    private final VisitExamQueueService examQueue;

    public VisitConclusionAdapter(VisitExamQueueService examQueue) {
        this.examQueue = examQueue;
    }

    @Override
    public void markAwaitingConclusion(UUID visitId, Instant now) {
        try {
            examQueue.markAwaitingConclusion(visitId, now);
        } catch (VisitOperationException conflict) {
            throw new ClinicalOperationException("MSG-42");
        }
    }
}
