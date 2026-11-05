package fu.fa26.sep490.g58.otcms_be.imaging.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticEvidenceService;
import fu.fa26.sep490.g58.otcms_be.imaging.config.ImagingProperties;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingConfirmationOutboxJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingConfirmationOutboxRepository;
import fu.fa26.sep490.g58.otcms_be.imaging.service.impl.ImagingConfirmationDispatcher;
import fu.fa26.sep490.g58.otcms_be.imaging.service.impl.ImagingConfirmationOutboxPersistenceService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class ImagingConfirmationDispatcherTest {
    @Mock ImagingConfirmationOutboxRepository outbox;
    @Mock DiagnosticEvidenceService diagnosticEvidence;
    @Mock ImagingConfirmationOutboxPersistenceService persistence;

    @Test
    void acceptedConfirmationIsDurablyAcknowledged() {
        ImagingConfirmationOutboxJpaEntity event = event(0);
        when(outbox.findAllByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAscCreatedAtAsc(
                eq("PENDING"), any())).thenReturn(List.of(event));

        dispatcher(3).dispatchDue();

        verify(diagnosticEvidence).linkConfirmedStudy(any(), any(), eq("imaging-confirmation-" + event.getEventId()));
        verify(persistence).acknowledge(event.getId());
    }

    @Test
    void unavailableOwnerSchedulesBoundedRetryWithStableEventIdentity() {
        ImagingConfirmationOutboxJpaEntity event = event(0);
        when(outbox.findAllByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAscCreatedAtAsc(
                eq("PENDING"), any())).thenReturn(List.of(event));
        doThrow(new IllegalStateException("unavailable"))
                .when(diagnosticEvidence).linkConfirmedStudy(any(), any(), any());

        Instant before = Instant.now();
        dispatcher(3).dispatchDue();

        ArgumentCaptor<Instant> nextAttempt = ArgumentCaptor.forClass(Instant.class);
        verify(persistence).retry(eq(event.getId()), nextAttempt.capture(), eq("CONFIRMATION_UNAVAILABLE"));
        assertTrue(!nextAttempt.getValue().isBefore(before.plusSeconds(4))
                && !nextAttempt.getValue().isAfter(Instant.now().plusSeconds(6)));
        verify(persistence, org.mockito.Mockito.never()).acknowledge(event.getId());
    }

    @Test
    void exhaustedAttemptsEnterReconciliationInsteadOfRetryingForever() {
        ImagingConfirmationOutboxJpaEntity event = event(2);
        when(outbox.findAllByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAscCreatedAtAsc(
                eq("PENDING"), any())).thenReturn(List.of(event));
        doThrow(new IllegalStateException("rejected"))
                .when(diagnosticEvidence).linkConfirmedStudy(any(), any(), any());

        dispatcher(3).dispatchDue();

        verify(persistence).reconcile(event.getId(), "CONFIRMATION_RETRY_EXHAUSTED");
        verify(persistence, org.mockito.Mockito.never()).retry(any(), any(), any());
    }

    private ImagingConfirmationDispatcher dispatcher(int maxAttempts) {
        ImagingProperties settings = new ImagingProperties();
        settings.setConfirmationMaxAttempts(maxAttempts);
        return new ImagingConfirmationDispatcher(outbox, diagnosticEvidence, persistence, settings,
                new ObjectMapper());
    }

    private static ImagingConfirmationOutboxJpaEntity event(int attemptCount) {
        Instant now = Instant.now();
        return new ImagingConfirmationOutboxJpaEntity(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "study-ref", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "CONFIRMED_MATCH", Map.of("evidenceCodes", List.of("PATIENT_ID_MATCH", "ACCESSION_MATCH")),
                "a".repeat(64), "PENDING", attemptCount, now, null, null, null);
    }
}
