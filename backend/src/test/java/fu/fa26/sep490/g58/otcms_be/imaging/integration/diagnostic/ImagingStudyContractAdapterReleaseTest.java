package fu.fa26.sep490.g58.otcms_be.imaging.integration.diagnostic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.FindingsReleaseEvent;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticViewerAuthorizationService;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingIngestOperationJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingReleaseInboxJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingStudyJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationState;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingReconciliationClassification;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingConfirmationOutboxRepository;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingIngestOperationRepository;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingReleaseInboxRepository;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingStudyRepository;
import fu.fa26.sep490.g58.otcms_be.imaging.service.impl.ImagingAuditPersistenceService;
import fu.fa26.sep490.g58.otcms_be.imaging.service.ImagingReconciliationService;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImagingStudyContractAdapterReleaseTest {
    @Mock ImagingStudyRepository studies;
    @Mock ImagingIngestOperationRepository operations;
    @Mock ImagingReleaseInboxRepository releases;
    @Mock ImagingAuditPersistenceService audit;
    @Mock ImagingConfirmationOutboxRepository confirmations;
    @Mock DiagnosticViewerAuthorizationService viewerAuthorization;
    @Mock ImagingReconciliationService reconciliation;

    @Test
    void acceptsLinkedStudyReleaseAndReturnsStableReplayAcknowledgement() {
        FindingsReleaseEvent event = event("a".repeat(64));
        ImagingStudyJpaEntity study = studyFor(event);
        ImagingIngestOperationJpaEntity operation = org.mockito.Mockito.mock(ImagingIngestOperationJpaEntity.class);
        when(operation.getState()).thenReturn(ImagingOperationState.LINKED);
        when(studies.findById(event.studyRef())).thenReturn(Optional.of(study));
        when(operations.findById(study.getIngestOperationId())).thenReturn(Optional.of(operation));
        when(releases.findByEventId(event.eventId())).thenReturn(Optional.empty());
        when(releases.insertAcceptedIfAbsent(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        var adapter = adapter();

        var first = adapter.acceptFindingsRelease(event);
        when(releases.findByEventId(event.eventId())).thenReturn(Optional.of(
                new ImagingReleaseInboxJpaEntity(UUID.randomUUID(), event.eventId(), event.requestId(),
                        event.visitId(), event.patientId(), event.studyRef(), event.findingsVersionId(),
                        event.reportId(), event.reportSha256(), first.payloadSha256(), "ACCEPTED", null)));

        var replay = adapter.acceptFindingsRelease(event);

        assertEquals(event.eventId(), first.eventId());
        assertEquals(first.payloadSha256(), replay.payloadSha256());
        assertEquals(true, replay.accepted());
        verify(audit).recordService(org.mockito.ArgumentMatchers.eq("FEAT_003"),
                org.mockito.ArgumentMatchers.eq("FINDINGS_RELEASE"), org.mockito.ArgumentMatchers.eq("ACCEPTED"),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq(event.studyRef()),
                org.mockito.ArgumentMatchers.eq(event.requestId()), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(event.visitId()), org.mockito.ArgumentMatchers.eq(event.patientId()),
                org.mockito.ArgumentMatchers.eq(event.eventId().toString()), any());
    }

    @Test
    void changedPayloadForPreviouslyAcceptedEventIsRejected() {
        FindingsReleaseEvent original = event("b".repeat(64));
        FindingsReleaseEvent changed = new FindingsReleaseEvent(original.eventId(), original.requestId(),
                original.visitId(), original.patientId(), original.studyRef(), original.findingsVersionId(),
                original.reportId(), "c".repeat(64));
        when(releases.findByEventId(original.eventId())).thenReturn(Optional.of(
                new ImagingReleaseInboxJpaEntity(UUID.randomUUID(), original.eventId(), original.requestId(),
                        original.visitId(), original.patientId(), original.studyRef(), original.findingsVersionId(),
                        original.reportId(), original.reportSha256(), "d".repeat(64), "ACCEPTED", null)));

        ImagingOperationException conflict = assertThrows(ImagingOperationException.class,
                () -> adapter().acceptFindingsRelease(changed));

        assertEquals(ImagingOperationOutcome.IDEMPOTENCY_CONFLICT, conflict.outcome());
        verify(releases, never()).insertAcceptedIfAbsent(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(reconciliation).openEvent(eq(original.eventId()), eq("RELEASE"),
                eq(ImagingReconciliationClassification.CONFLICT), any(String.class),
                eq("RELEASE_EVENT_CONFLICT"));
    }

    @Test
    void concurrentIdenticalReleaseInsertReturnsIdempotentReplay() {
        FindingsReleaseEvent event = event("f".repeat(64));
        ImagingStudyJpaEntity study = studyFor(event);
        ImagingIngestOperationJpaEntity operation = org.mockito.Mockito.mock(ImagingIngestOperationJpaEntity.class);
        when(operation.getState()).thenReturn(ImagingOperationState.LINKED);
        when(studies.findById(event.studyRef())).thenReturn(Optional.of(study));
        when(operations.findById(study.getIngestOperationId())).thenReturn(Optional.of(operation));
        AtomicReference<ImagingReleaseInboxJpaEntity> raced = new AtomicReference<>();
        when(releases.findByEventId(event.eventId())).thenAnswer(invocation -> Optional.ofNullable(raced.get()));
        when(releases.insertAcceptedIfAbsent(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    raced.set(new ImagingReleaseInboxJpaEntity(invocation.getArgument(0), invocation.getArgument(1),
                            invocation.getArgument(2), invocation.getArgument(3), invocation.getArgument(4),
                            invocation.getArgument(5), invocation.getArgument(6), invocation.getArgument(7),
                            invocation.getArgument(8), invocation.getArgument(9), "ACCEPTED", null));
                    return 0;
                });

        var replay = adapter().acceptFindingsRelease(event);

        assertEquals(true, replay.accepted());
        verify(audit).recordService(eq("FEAT_003"), eq("FINDINGS_RELEASE"), eq("IDEMPOTENT_REPLAY"),
                org.mockito.ArgumentMatchers.isNull(), eq(event.studyRef()), eq(event.requestId()),
                org.mockito.ArgumentMatchers.isNull(), eq(event.visitId()), eq(event.patientId()),
                eq(event.eventId().toString()), any());
    }

    @Test
    void releaseIsRejectedUntilTheStudyIsLinked() {
        FindingsReleaseEvent event = event("e".repeat(64));
        ImagingStudyJpaEntity study = studyFor(event);
        ImagingIngestOperationJpaEntity operation = org.mockito.Mockito.mock(ImagingIngestOperationJpaEntity.class);
        when(operation.getState()).thenReturn(ImagingOperationState.CONFIRMATION_PENDING);
        when(studies.findById(event.studyRef())).thenReturn(Optional.of(study));
        when(operations.findById(study.getIngestOperationId())).thenReturn(Optional.of(operation));

        ImagingOperationException rejected = assertThrows(ImagingOperationException.class,
                () -> adapter().acceptFindingsRelease(event));

        assertEquals(ImagingOperationOutcome.EVIDENCE_MISMATCH, rejected.outcome());
        verify(releases, never()).insertAcceptedIfAbsent(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(reconciliation).openEvent(eq(event.eventId()), eq("RELEASE"),
                eq(ImagingReconciliationClassification.RETRYABLE), any(String.class),
                eq("RELEASE_BEFORE_LINKED"));
    }

    private ImagingStudyContractAdapter adapter() {
        return new ImagingStudyContractAdapter(studies, operations, releases, audit, confirmations,
                viewerAuthorization, reconciliation);
    }

    private static FindingsReleaseEvent event(String reportHash) {
        return new FindingsReleaseEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "study-release", UUID.randomUUID(), UUID.randomUUID(), reportHash);
    }

    private static ImagingStudyJpaEntity studyFor(FindingsReleaseEvent event) {
        ImagingStudyJpaEntity study = org.mockito.Mockito.mock(ImagingStudyJpaEntity.class);
        when(study.getIngestOperationId()).thenReturn(UUID.randomUUID());
        when(study.getRequestId()).thenReturn(event.requestId());
        when(study.getVisitId()).thenReturn(event.visitId());
        when(study.getPatientId()).thenReturn(event.patientId());
        return study;
    }
}
