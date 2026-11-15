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
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingOperationRecord;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingStudyJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationState;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationType;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingVerificationStatus;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingStudyRepository;
import fu.fa26.sep490.g58.otcms_be.imaging.service.impl.ImagingAuditPersistenceService;
import fu.fa26.sep490.g58.otcms_be.imaging.service.ImagingReconciliationService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImagingStudyContractAdapterReleaseTest {
    @Mock ImagingStudyRepository studies;
    @Mock ImagingAuditPersistenceService audit;
    @Mock DiagnosticViewerAuthorizationService viewerAuthorization;
    @Mock ImagingReconciliationService reconciliation;

    @Test
    void acceptsLinkedStudyReleaseAndReturnsStableReplayAcknowledgement() {
        FindingsReleaseEvent event = event("a".repeat(64));
        ImagingStudyJpaEntity study = linkedStudyFor(event);
        when(studies.findByStudyRef(event.studyRef())).thenReturn(Optional.of(study));
        when(studies.findByIdForUpdate(study.getId())).thenReturn(Optional.of(study));
        when(studies.saveAndFlush(study)).thenReturn(study);
        var first = adapter().acceptFindingsRelease(event);
        var replay = adapter().acceptFindingsRelease(event);
        assertEquals(event.eventId(), first.eventId());
        assertEquals(first.payloadSha256(), replay.payloadSha256());
        assertEquals(true, replay.accepted());
        verify(studies, org.mockito.Mockito.times(1)).saveAndFlush(study);
        verify(audit).recordService(eq("FEAT_003"), eq("FINDINGS_RELEASE"), eq("ACCEPTED"),
                eq(null), eq(event.studyRef()), eq(event.requestId()), eq(null), eq(event.visitId()),
                eq(event.patientId()), eq(event.eventId().toString()), any());
        verify(audit).recordService(eq("FEAT_003"), eq("FINDINGS_RELEASE"), eq("IDEMPOTENT_REPLAY"),
                eq(null), eq(event.studyRef()), eq(event.requestId()), eq(null), eq(event.visitId()),
                eq(event.patientId()), eq(event.eventId().toString()), any());
    }

    @Test
    void changedPayloadForPreviouslyAcceptedEventIsRejected() {
        FindingsReleaseEvent original = event("b".repeat(64));
        ImagingStudyJpaEntity study = linkedStudyFor(original);
        when(studies.findByStudyRef(original.studyRef())).thenReturn(Optional.of(study));
        when(studies.findByIdForUpdate(study.getId())).thenReturn(Optional.of(study));
        adapter().acceptFindingsRelease(original);
        FindingsReleaseEvent changed = new FindingsReleaseEvent(original.eventId(), original.requestId(),
                original.visitId(), original.patientId(), original.studyRef(), original.findingsVersionId(),
                original.reportId(), "c".repeat(64));
        ImagingOperationException conflict = assertThrows(ImagingOperationException.class,
                () -> adapter().acceptFindingsRelease(changed));
        assertEquals(ImagingOperationOutcome.IDEMPOTENCY_CONFLICT, conflict.outcome());
        verify(studies, org.mockito.Mockito.times(1)).saveAndFlush(study);
        verify(reconciliation).openEvent(eq(original.eventId()), eq("RELEASE"),
                eq(fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingReconciliationClassification.CONFLICT),
                any(String.class), eq("RELEASE_EVENT_CONFLICT"));
    }

    @Test
    void releaseIsRejectedUntilTheStudyIsLinked() {
        FindingsReleaseEvent event = event("e".repeat(64));
        ImagingStudyJpaEntity study = studyFor(event, ImagingOperationState.CONFIRMATION_PENDING);
        when(studies.findByStudyRef(event.studyRef())).thenReturn(Optional.of(study));
        when(studies.findByIdForUpdate(study.getId())).thenReturn(Optional.of(study));
        ImagingOperationException rejected = assertThrows(ImagingOperationException.class,
                () -> adapter().acceptFindingsRelease(event));
        assertEquals(ImagingOperationOutcome.EVIDENCE_MISMATCH, rejected.outcome());
        verify(studies, never()).saveAndFlush(study);
        verify(reconciliation).openEvent(eq(event.eventId()), eq("RELEASE"),
                eq(fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingReconciliationClassification.RETRYABLE),
                any(String.class), eq("RELEASE_BEFORE_LINKED"));
    }

    private ImagingStudyContractAdapter adapter() {
        return new ImagingStudyContractAdapter(studies, audit, viewerAuthorization, reconciliation);
    }

    private static FindingsReleaseEvent event(String reportHash) {
        return new FindingsReleaseEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "study-release", UUID.randomUUID(), UUID.randomUUID(), reportHash);
    }

    private static ImagingStudyJpaEntity linkedStudyFor(FindingsReleaseEvent event) {
        return studyFor(event, ImagingOperationState.LINKED);
    }

    private static ImagingStudyJpaEntity studyFor(FindingsReleaseEvent event, ImagingOperationState state) {
        return new ImagingStudyJpaEntity(new ImagingOperationRecord(UUID.randomUUID(), event.studyRef(),
                event.requestId(), UUID.randomUUID(), event.visitId(), event.patientId(), UUID.randomUUID(),
                ImagingOperationType.UPLOAD, state, "a".repeat(64), 1, "1.2.3", "CT", 1, 1,
                ImagingVerificationStatus.CONFIRMED_MATCH, List.of("PATIENT_ID_MATCH"), "archive-study", null,
                3, Instant.now(), Instant.now()), "upload-key");
    }
}
