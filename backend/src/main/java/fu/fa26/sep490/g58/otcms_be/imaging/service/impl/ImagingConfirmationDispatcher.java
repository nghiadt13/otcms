package fu.fa26.sep490.g58.otcms_be.imaging.service.impl;

import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.StudyEvidence;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.StudyVerificationStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticEvidenceService;
import fu.fa26.sep490.g58.otcms_be.imaging.config.ImagingProperties;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingConfirmationOutboxJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingConfirmationOutboxRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import tools.jackson.databind.ObjectMapper;

/** Delivers due durable ingest confirmations through FEAT-003's public application service. */
@Component
@ConditionalOnProperty(prefix = "otcms.imaging", name = "enabled", havingValue = "true")
public class ImagingConfirmationDispatcher {
    private static final int MAX_EVENTS_PER_RUN = 50;

    private final ImagingConfirmationOutboxRepository outbox;
    private final DiagnosticEvidenceService diagnosticEvidence;
    private final ImagingConfirmationOutboxPersistenceService persistence;
    private final ImagingProperties settings;
    private final ObjectMapper objectMapper;

    public ImagingConfirmationDispatcher(ImagingConfirmationOutboxRepository outbox,
            DiagnosticEvidenceService diagnosticEvidence,
            ImagingConfirmationOutboxPersistenceService persistence, ImagingProperties settings,
            ObjectMapper objectMapper) {
        this.outbox = outbox; this.diagnosticEvidence = diagnosticEvidence;
        this.persistence = persistence; this.settings = settings; this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${otcms.imaging.confirmation-poll-delay-ms:5000}")
    public void dispatchDue() {
        List<ImagingConfirmationOutboxJpaEntity> due = outbox
                .findAllByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAscCreatedAtAscIdAsc(
                        "PENDING", Instant.now(), PageRequest.of(0, MAX_EVENTS_PER_RUN));
        for (ImagingConfirmationOutboxJpaEntity event : due) dispatch(event);
    }

    private void dispatch(ImagingConfirmationOutboxJpaEntity event) {
        try {
            List<String> evidence = objectMapper.convertValue(event.getVerificationEvidence().get("evidenceCodes"),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            diagnosticEvidence.linkConfirmedStudy(DiagnosticActor.trustedIntegration(),
                    new StudyEvidence(event.getEventId(), event.getStudyRef(), event.getRequestId(),
                            event.getVisitId(), event.getPatientId(), event.getAttemptId(),
                            StudyVerificationStatus.CONFIRMED_MATCH, true, evidence),
                    "imaging-confirmation-" + event.getEventId());
            persistence.acknowledge(event.getId());
        } catch (ImagingOperationException rejection) {
            if (rejection.outcome() == ImagingOperationOutcome.EVIDENCE_MISMATCH
                    || rejection.outcome() == ImagingOperationOutcome.IDEMPOTENCY_CONFLICT
                    || rejection.outcome() == ImagingOperationOutcome.STATE_CONFLICT) {
                persistence.reconcile(event.getId(), "CONFIRMATION_REJECTED");
                return;
            }
            retryOrReconcile(event);
        } catch (RuntimeException unavailable) {
            retryOrReconcile(event);
        }
    }

    private void retryOrReconcile(ImagingConfirmationOutboxJpaEntity event) {
            if (event.getAttemptCount() + 1 >= settings.getConfirmationMaxAttempts()) {
                persistence.reconcile(event.getId(), "CONFIRMATION_RETRY_EXHAUSTED");
                return;
            }
            long delay = Math.min(settings.getConfirmationMaxDelay().toSeconds(),
                    5L << Math.min(event.getAttemptCount(), 6));
            persistence.retry(event.getId(), Instant.now().plusSeconds(delay), "CONFIRMATION_UNAVAILABLE");
    }
}
