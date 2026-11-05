package fu.fa26.sep490.g58.otcms_be.imaging.integration.diagnostic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingConfirmationOutboxRepository;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingIngestOperationRepository;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingReleaseInboxRepository;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingStudyRepository;
import fu.fa26.sep490.g58.otcms_be.imaging.service.impl.ImagingAuditPersistenceService;
import java.util.Optional;
import java.util.UUID;
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

    @Test
    void acceptsLinkedStudyReleaseAndReturnsStableReplayAcknowledgement() {
        FindingsReleaseEvent event = event("a".repeat(64));
        ImagingStudyJpaEntity study = studyFor(event);
        ImagingIngestOperationJpaEntity operation = org.mockito.Mockito.mock(ImagingIngestOperationJpaEntity.class);
        when(operation.getState()).thenReturn(ImagingOperationState.LINKED);
        when(studies.findById(event.studyRef())).thenReturn(Optional.of(study));
        when(operations.findById(study.getIngestOperationId())).thenReturn(Optional.of(operation));
        when(releases.findByEventId(event.eventId())).thenReturn(Optional.empty());
        when(releases.saveAndFlush(any(ImagingReleaseInboxJpaEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        var adapter = adapter();

        var first = adapter.acceptFindingsRelease(event);
        var persisted = org.mockito.ArgumentCaptor.forClass(ImagingReleaseInboxJpaEntity.class);
        verify(releases).saveAndFlush(persisted.capture());
        when(releases.findByEventId(event.eventId())).thenReturn(Optional.of(persisted.getValue()));

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
        ImagingStudyJpaEntity study = studyFor(original);
        ImagingIngestOperationJpaEntity operation = org.mockito.Mockito.mock(ImagingIngestOperationJpaEntity.class);
        when(operation.getState()).thenReturn(ImagingOperationState.LINKED);
        when(studies.findById(original.studyRef())).thenReturn(Optional.of(study));
        when(operations.findById(study.getIngestOperationId())).thenReturn(Optional.of(operation));
        when(releases.findByEventId(original.eventId())).thenReturn(Optional.of(
                new ImagingReleaseInboxJpaEntity(UUID.randomUUID(), original.eventId(), original.requestId(),
                        original.visitId(), original.patientId(), original.studyRef(), original.findingsVersionId(),
                        original.reportId(), original.reportSha256(), "d".repeat(64), "ACCEPTED", null)));

        ImagingOperationException conflict = assertThrows(ImagingOperationException.class,
                () -> adapter().acceptFindingsRelease(changed));

        assertEquals(ImagingOperationOutcome.IDEMPOTENCY_CONFLICT, conflict.outcome());
        verify(releases, never()).saveAndFlush(any());
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
        verify(releases, never()).saveAndFlush(any());
    }

    private ImagingStudyContractAdapter adapter() {
        return new ImagingStudyContractAdapter(studies, operations, releases, audit, confirmations,
                viewerAuthorization);
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
