package fu.fa26.sep490.g58.otcms_be.imaging.service.impl;

import fu.fa26.sep490.g58.otcms_be.imaging.config.ImagingProperties;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingStudyJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingStudyRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Delivers due study confirmations through diagnostic, clinical and visit owner services. */
@Component
@ConditionalOnProperty(prefix = "otcms.imaging", name = "enabled", havingValue = "true")
public class ImagingConfirmationDispatcher {
    private static final int MAX_EVENTS_PER_RUN = 50;
    private final ImagingStudyRepository studies;
    private final ImagingConfirmationDispatchTransactionService dispatchTransaction;
    private final ImagingConfirmationOutboxPersistenceService persistence;
    private final ImagingProperties settings;

    public ImagingConfirmationDispatcher(ImagingStudyRepository studies,
            ImagingConfirmationDispatchTransactionService dispatchTransaction,
            ImagingConfirmationOutboxPersistenceService persistence, ImagingProperties settings) {
        this.studies = studies;
        this.dispatchTransaction = dispatchTransaction;
        this.persistence = persistence;
        this.settings = settings;
    }

    @Scheduled(fixedDelayString = "${otcms.imaging.confirmation-poll-delay-ms:5000}")
    public void dispatchDue() {
        List<ImagingStudyJpaEntity> due = studies.findDueConfirmations(Instant.now(), MAX_EVENTS_PER_RUN);
        for (ImagingStudyJpaEntity study : due) dispatch(study);
    }

    private void dispatch(ImagingStudyJpaEntity study) {
        try {
            dispatchTransaction.dispatch(study.getId());
        } catch (ImagingOperationException rejected) {
            if (rejected.outcome() == ImagingOperationOutcome.EVIDENCE_MISMATCH
                    || rejected.outcome() == ImagingOperationOutcome.IDEMPOTENCY_CONFLICT
                    || rejected.outcome() == ImagingOperationOutcome.STATE_CONFLICT) {
                persistence.reconcile(study.getId(), "CONFIRMATION_REJECTED");
                return;
            }
            retryOrReconcile(study);
        } catch (RuntimeException unavailable) {
            retryOrReconcile(study);
        }
    }

    private void retryOrReconcile(ImagingStudyJpaEntity study) {
        if (study.getConfirmationAttemptCount() + 1 >= settings.getConfirmationMaxAttempts()) {
            persistence.reconcile(study.getId(), "CONFIRMATION_RETRY_EXHAUSTED");
            return;
        }
        long delay = Math.min(settings.getConfirmationMaxDelay().toSeconds(),
                5L << Math.min(study.getConfirmationAttemptCount(), 6));
        persistence.retry(study.getId(), Instant.now().plusSeconds(delay), "CONFIRMATION_UNAVAILABLE");
    }
}
