package fu.fa26.sep490.g58.otcms_be.imaging.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fu.fa26.sep490.g58.otcms_be.imaging.config.ImagingProperties;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingOperationRecord;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingStudyJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationState;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationType;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingVerificationStatus;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingStudyRepository;
import fu.fa26.sep490.g58.otcms_be.imaging.service.impl.ImagingConfirmationDispatchTransactionService;
import fu.fa26.sep490.g58.otcms_be.imaging.service.impl.ImagingConfirmationDispatcher;
import fu.fa26.sep490.g58.otcms_be.imaging.service.impl.ImagingConfirmationOutboxPersistenceService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImagingConfirmationDispatcherTest {
    @Mock ImagingStudyRepository studies;
    @Mock ImagingConfirmationDispatchTransactionService dispatchTransaction;
    @Mock ImagingConfirmationOutboxPersistenceService persistence;

    @Test
    void acceptedConfirmationRunsTheAtomicDispatchTransaction() {
        ImagingStudyJpaEntity study = study(0);
        when(studies.findDueConfirmations(any(), eq(50))).thenReturn(List.of(study));
        dispatcher(3).dispatchDue();
        verify(dispatchTransaction).dispatch(study.getId());
        ArgumentCaptor<Instant> now = ArgumentCaptor.forClass(Instant.class);
        verify(studies).findDueConfirmations(now.capture(), eq(50));
    }

    @Test
    void unavailableOwnerSchedulesBoundedRetry() {
        ImagingStudyJpaEntity study = study(0);
        when(studies.findDueConfirmations(any(), eq(50))).thenReturn(List.of(study));
        doThrow(new IllegalStateException("unavailable")).when(dispatchTransaction).dispatch(study.getId());
        Instant before = Instant.now();
        dispatcher(3).dispatchDue();
        ArgumentCaptor<Instant> nextAttempt = ArgumentCaptor.forClass(Instant.class);
        verify(persistence).retry(eq(study.getId()), nextAttempt.capture(), eq("CONFIRMATION_UNAVAILABLE"));
        org.junit.jupiter.api.Assertions.assertTrue(!nextAttempt.getValue().isBefore(before.plusSeconds(4))
                && !nextAttempt.getValue().isAfter(Instant.now().plusSeconds(6)));
    }

    @Test
    void exhaustedAttemptsEnterReconciliation() {
        ImagingStudyJpaEntity study = study(2);
        when(studies.findDueConfirmations(any(), eq(50))).thenReturn(List.of(study));
        doThrow(new IllegalStateException("unavailable")).when(dispatchTransaction).dispatch(study.getId());
        dispatcher(3).dispatchDue();
        verify(persistence).reconcile(study.getId(), "CONFIRMATION_RETRY_EXHAUSTED");
    }

    @Test
    void permanentConflictEntersReconciliationWithoutRetry() {
        ImagingStudyJpaEntity study = study(0);
        when(studies.findDueConfirmations(any(), eq(50))).thenReturn(List.of(study));
        doThrow(new ImagingOperationException(ImagingOperationOutcome.EVIDENCE_MISMATCH))
                .when(dispatchTransaction).dispatch(study.getId());
        dispatcher(3).dispatchDue();
        verify(persistence).reconcile(study.getId(), "CONFIRMATION_REJECTED");
    }

    private ImagingConfirmationDispatcher dispatcher(int maxAttempts) {
        ImagingProperties settings = new ImagingProperties();
        settings.setConfirmationMaxAttempts(maxAttempts);
        return new ImagingConfirmationDispatcher(studies, dispatchTransaction, persistence, settings);
    }

    private static ImagingStudyJpaEntity study(int attemptCount) {
        Instant now = Instant.now();
        var record = new ImagingOperationRecord(UUID.randomUUID(), "study-ref", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), ImagingOperationType.UPLOAD,
                ImagingOperationState.CONFIRMATION_PENDING, "a".repeat(64), 123, "1.2.3", "CT", 1, 1,
                ImagingVerificationStatus.CONFIRMED_MATCH, List.of("PATIENT_ID_MATCH"), "archive-study", null,
                1, now, now);
        var study = new ImagingStudyJpaEntity(record, "upload-key");
        study.prepareConfirmation(UUID.randomUUID(), "send-key", "b".repeat(64), now);
        for (int attempt = 0; attempt < attemptCount; attempt++) {
            study.recordConfirmationRetry(now, "CONFIRMATION_UNAVAILABLE", now);
        }
        return study;
    }
}
