package fu.fa26.sep490.g58.otcms_be.diagnostic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort.ExecutionStatus;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort.ImagingExecution;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticReportJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticFindingsVersionState;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticReportValidationStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticReportJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticVisitCompletionEvidenceService;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl.DiagnosticVisitCompletionEvidenceServiceImpl;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DiagnosticVisitCompletionEvidenceServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-05T08:00:00Z");
    private final UUID visit = UUID.randomUUID();
    private final UUID patient = UUID.randomUUID();
    private final ClinicalServiceOrderExecutionPort executions = mock(ClinicalServiceOrderExecutionPort.class);
    private final DiagnosticReportJpaRepository reports = mock(DiagnosticReportJpaRepository.class);
    private final DiagnosticVisitCompletionEvidenceService service =
            new DiagnosticVisitCompletionEvidenceServiceImpl(executions, reports);

    private ImagingExecution execution(UUID orderId, ExecutionStatus status, UUID attemptId, String studyRef) {
        return new ImagingExecution(orderId, visit, patient, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "MRI", "head", "reason", status, null, null, attemptId, UUID.randomUUID(), "ACQUISITION_STARTED",
                UUID.randomUUID(), NOW, "source", studyRef, null, null, null, null, null, null, 1, NOW, NOW);
    }

    private DiagnosticReportJpaEntity report(UUID orderId, UUID reportId, int version, String studyRef) {
        var report = new DiagnosticReportJpaEntity(reportId, orderId, visit, studyRef, version,
                "private findings", "a".repeat(64), DiagnosticReportValidationStatus.VALIDATED,
                UUID.randomUUID(), NOW, NOW);
        report.markCompleted(reportId, NOW);
        return report;
    }

    private void given(UUID orderId, ImagingExecution execution, DiagnosticReportJpaEntity... reportRows) {
        when(executions.findAllByOrderIds(Set.of(orderId))).thenReturn(List.of(execution));
        when(reports.findAllByRequestIdInAndState(List.of(orderId), DiagnosticFindingsVersionState.COMPLETED))
                .thenReturn(List.of(reportRows));
    }

    private DiagnosticVisitCompletionEvidenceService.ImagingCompletionEvidence evidence(UUID... orderIds) {
        return service.getCompletionEvidence(visit, patient, List.of(orderIds));
    }

    @Test void emptyRequiredSetIsCompleteWithoutPersistence() {
        assertTrue(evidence().allComplete());
        assertTrue(service.findCompletedRequestIds(List.of()).isEmpty());
        verifyNoInteractions(executions, reports);
    }

    @Test void reviewedExecutionWithValidatedReleasedReportCompletesOrder() {
        UUID orderId = UUID.randomUUID();
        given(orderId, execution(orderId, ExecutionStatus.REVIEWED, UUID.randomUUID(), "study"),
                report(orderId, UUID.randomUUID(), 1, "study"));
        var result = evidence(orderId);
        assertTrue(result.allComplete());
        assertEquals(Set.of(orderId), result.completedServiceOrderIds());
        assertEquals(visit, result.visitId());
        assertEquals(patient, result.patientId());
    }

    @Test void statusAttemptAndStudyAreRequiredBeforeReadingReports() {
        UUID orderId = UUID.randomUUID();
        when(executions.findAllByOrderIds(Set.of(orderId))).thenReturn(List.of(
                execution(orderId, ExecutionStatus.RESULT_AVAILABLE, UUID.randomUUID(), "study")));
        assertFalse(evidence(orderId).allComplete());
        verifyNoInteractions(reports);

        when(executions.findAllByOrderIds(Set.of(orderId))).thenReturn(List.of(
                execution(orderId, ExecutionStatus.REVIEWED, null, "study")));
        assertFalse(evidence(orderId).allComplete());
        when(executions.findAllByOrderIds(Set.of(orderId))).thenReturn(List.of(
                execution(orderId, ExecutionStatus.REVIEWED, UUID.randomUUID(), " ")));
        assertFalse(evidence(orderId).allComplete());
        verifyNoInteractions(reports);
    }

    @Test void wrongVisitOrPatientAndDuplicateExecutionRowsFailClosed() {
        UUID orderId = UUID.randomUUID();
        var wrongVisit = execution(orderId, ExecutionStatus.REVIEWED, UUID.randomUUID(), "study");
        wrongVisit = new ImagingExecution(wrongVisit.orderId(), UUID.randomUUID(), wrongVisit.patientId(),
                wrongVisit.orderingDoctorAccountId(), wrongVisit.serviceId(), wrongVisit.roomId(), wrongVisit.imagingType(),
                wrongVisit.bodyArea(), wrongVisit.clinicalReason(), wrongVisit.status(), wrongVisit.cancellationCause(),
                wrongVisit.cancellationReason(), wrongVisit.attemptId(), wrongVisit.technicianAccountId(), wrongVisit.firstUseType(),
                wrongVisit.firstUseEventId(), wrongVisit.firstUseAt(), wrongVisit.firstUseSourceId(), wrongVisit.studyRef(),
                wrongVisit.reviewIdempotencyKey(), wrongVisit.reviewFingerprint(), wrongVisit.cancelIdempotencyKey(),
                wrongVisit.cancelFingerprint(), wrongVisit.transferIdempotencyKey(), wrongVisit.transferFingerprint(),
                wrongVisit.executionVersion(), wrongVisit.createdAt(), wrongVisit.updatedAt());
        when(executions.findAllByOrderIds(Set.of(orderId))).thenReturn(List.of(wrongVisit, wrongVisit));
        assertFalse(evidence(orderId).allComplete());
        verifyNoInteractions(reports);
    }

    @Test void missingOrUnreleasedInvalidReportFailsClosed() {
        UUID orderId = UUID.randomUUID();
        var execution = execution(orderId, ExecutionStatus.REVIEWED, UUID.randomUUID(), "study");
        when(executions.findAllByOrderIds(Set.of(orderId))).thenReturn(List.of(execution));
        when(reports.findAllByRequestIdInAndState(List.of(orderId), DiagnosticFindingsVersionState.COMPLETED))
                .thenReturn(List.of());
        assertFalse(evidence(orderId).allComplete());

        var report = report(orderId, UUID.randomUUID(), 1, "study");
        report.setReleasedAt(null);
        when(reports.findAllByRequestIdInAndState(List.of(orderId), DiagnosticFindingsVersionState.COMPLETED))
                .thenReturn(List.of(report));
        assertFalse(evidence(orderId).allComplete());
        report.setReleasedAt(NOW);
        report.setValidationStatus(DiagnosticReportValidationStatus.INVALID);
        assertFalse(evidence(orderId).allComplete());
        report.setValidationStatus(DiagnosticReportValidationStatus.VALIDATED);
        report.setValidatedAt(null);
        assertFalse(evidence(orderId).allComplete());
    }

    @Test void mismatchedReportVisitOrStudyIsRejected() {
        UUID orderId = UUID.randomUUID();
        var execution = execution(orderId, ExecutionStatus.REVIEWED, UUID.randomUUID(), "study");
        var wrongVisit = report(orderId, UUID.randomUUID(), 1, "study");
        ReflectionTestUtils.setField(wrongVisit, "visitId", UUID.randomUUID());
        given(orderId, execution, wrongVisit);
        assertFalse(evidence(orderId).allComplete());

        var wrongStudy = report(orderId, UUID.randomUUID(), 1, "other-study");
        given(orderId, execution, wrongStudy);
        assertFalse(evidence(orderId).allComplete());
    }

    @Test void newestCompletedVersionMustBeReleasedAndValid() {
        UUID orderId = UUID.randomUUID();
        var execution = execution(orderId, ExecutionStatus.REVIEWED, UUID.randomUUID(), "study");
        var releasedV1 = report(orderId, UUID.randomUUID(), 1, "study");
        var unreleasedV2 = report(orderId, UUID.randomUUID(), 2, "study");
        unreleasedV2.setReleasedAt(null);
        given(orderId, execution, releasedV1, unreleasedV2);
        assertFalse(evidence(orderId).allComplete());
    }

    @Test void repositoryFailuresPropagateAndCompletedRequestIdsAreOrderIds() {
        UUID orderId = UUID.randomUUID();
        when(executions.findAllByOrderIds(Set.of(orderId))).thenThrow(new IllegalStateException("unavailable"));
        assertThrows(IllegalStateException.class, () -> evidence(orderId));

        reset(executions);
        var execution = execution(orderId, ExecutionStatus.REVIEWED, UUID.randomUUID(), "study");
        when(executions.findAllByOrderIds(Set.of(orderId))).thenReturn(List.of(execution));
        when(reports.findAllByRequestIdInAndState(List.of(orderId), DiagnosticFindingsVersionState.COMPLETED))
                .thenReturn(List.of(report(orderId, UUID.randomUUID(), 1, "study")));
        assertEquals(Set.of(orderId), service.findCompletedRequestIds(List.of(orderId)));
    }

    @Test void allRequiredOrdersMustBeCompleteAndResultSetsAreImmutable() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        var firstExecution = execution(first, ExecutionStatus.REVIEWED, UUID.randomUUID(), "study-1");
        var secondExecution = execution(second, ExecutionStatus.REVIEWED, UUID.randomUUID(), "study-2");
        when(executions.findAllByOrderIds(Set.of(first, second))).thenReturn(List.of(firstExecution, secondExecution));
        when(reports.findAllByRequestIdInAndState(anyCollection(), eq(DiagnosticFindingsVersionState.COMPLETED)))
                .thenReturn(List.of(report(first, UUID.randomUUID(), 1, "study-1")));
        var result = evidence(first, second);
        assertFalse(result.allComplete());
        assertEquals(Set.of(first), result.completedServiceOrderIds());
        assertThrows(UnsupportedOperationException.class, () -> result.completedServiceOrderIds().clear());
    }

    @Test void nullInputsAreRejectedBeforePersistence() {
        assertThrows(NullPointerException.class, () -> service.getCompletionEvidence(null, patient, List.of()));
        assertThrows(NullPointerException.class, () -> service.getCompletionEvidence(visit, null, List.of()));
        assertThrows(NullPointerException.class, () -> service.getCompletionEvidence(visit, patient, null));
        assertThrows(NullPointerException.class, () -> service.findCompletedRequestIds(Arrays.asList((UUID) null)));
        verifyNoInteractions(executions, reports);
    }
}
