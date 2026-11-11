package fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl;

import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticEvidenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Delivers diagnostic owner events, including findings releases, from the durable outbox. */
@Component
public class DiagnosticOutboxDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(DiagnosticOutboxDispatcher.class);
    private static final int MAX_EVENTS_PER_RUN = 50;
    private static final DiagnosticEvidenceService.RetryPolicy RETRY_POLICY =
            new DiagnosticEvidenceService.RetryPolicy(8, 5, 300);

    private final DiagnosticEvidenceService evidence;

    public DiagnosticOutboxDispatcher(DiagnosticEvidenceService evidence) {
        this.evidence = evidence;
    }

    @Scheduled(fixedDelayString = "${otcms.diagnostic.outbox-poll-delay-ms:5000}")
    public void dispatchDue() {
        try {
            DiagnosticEvidenceService.DispatchSummary result =
                    evidence.dispatchDueOutbox(MAX_EVENTS_PER_RUN, RETRY_POLICY);
            if (result.considered() > 0) {
                LOGGER.info("Diagnostic outbox delivery: considered={}, delivered={}, retryScheduled={}, deadLettered={}",
                        result.considered(), result.delivered(), result.retryScheduled(), result.deadLettered());
            }
        } catch (RuntimeException failure) {
            LOGGER.warn("Diagnostic outbox dispatch could not run; it will retry on the next poll", failure);
        }
    }
}
