package fu.fa26.sep490.g58.otcms_be.visit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalExaminationStatus;
import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.ClinicalExaminationJpaEntity;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.ClinicalExaminationRepository;
import fu.fa26.sep490.g58.otcms_be.clinical.service.impl.ClinicalFollowUpEvidenceQueryServiceImpl;
import fu.fa26.sep490.g58.otcms_be.visit.exception.*;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitWorkflowRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitRoomCapacityService;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitScheduledCareService.ScheduledCareCommand;
import fu.fa26.sep490.g58.otcms_be.visit.service.impl.VisitWorkflowServiceImpl;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class VisitClinicalEvidenceRepairTest {
    final UUID visitId = UUID.randomUUID(), patientId = UUID.randomUUID(), doctorId = UUID.randomUUID(), appointmentId = UUID.randomUUID();
    final ClinicalExaminationRepository examinations = mock(ClinicalExaminationRepository.class);
    final ClinicalFollowUpEvidenceQueryServiceImpl clinical = new ClinicalFollowUpEvidenceQueryServiceImpl(examinations);
    final VisitWorkflowRepository repository = mock(VisitWorkflowRepository.class);
    final VisitWorkflowServiceImpl visits = new VisitWorkflowServiceImpl(repository, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
            mock(VisitRoomCapacityService.class), clinical);

    ClinicalExaminationJpaEntity examination(ClinicalExaminationStatus status, Boolean indication) {
        var exam = ClinicalExaminationJpaEntity.create(visitId, "reason", "symptoms", "findings", null, null, null, status, Instant.EPOCH);
        exam.updateAfterVisitAdvice(null, null, null, null, "Do not book follow-up unless needed", Instant.EPOCH);
        exam.setFollowUpIndicated(indication); when(examinations.findByVisitId(visitId)).thenReturn(Optional.of(exam)); return exam;
    }
    void assigned(boolean value) {
        // Legacy booleans intentionally false: only the real Clinical query can establish completion/intent.
        when(repository.findFollowUpEvidence(visitId, doctorId)).thenReturn(new VisitWorkflowRepository.FollowUpEvidenceRow(
                patientId, appointmentId, value, false, false));
    }

    @Test void completedRealClinicalExaminationAndExplicitRecommendationAreRecognized() {
        assigned(true); examination(ClinicalExaminationStatus.COMPLETED, true);
        var evidence = visits.requireFollowUpEvidence(visitId, doctorId);
        assertTrue(evidence.examinationCompleted()); assertTrue(evidence.followUpIndicated()); assertTrue(evidence.doctorAssignedToCare());
        assertEquals(patientId, evidence.patientProfileId()); assertEquals(appointmentId, evidence.originatingAppointmentId());
    }

    @Test void draftIsNotCompletedEvenWithExplicitRecommendation() {
        assigned(true); examination(ClinicalExaminationStatus.DRAFT, true);
        assertFalse(visits.requireFollowUpEvidence(visitId, doctorId).examinationCompleted());
    }

    @Test void freeTextDoesNotImplyFollowUpAndExplicitFalseRemainsFalse() {
        assigned(true); examination(ClinicalExaminationStatus.COMPLETED, null);
        assertFalse(visits.requireFollowUpEvidence(visitId, doctorId).followUpIndicated());
        examination(ClinicalExaminationStatus.COMPLETED, false);
        assertFalse(visits.requireFollowUpEvidence(visitId, doctorId).followUpIndicated());
    }

    @Test void wrongDoctorIsRejectedBeforeClinicalEvidenceRead() {
        assigned(false);
        assertThrows(VisitEvidenceException.class, () -> visits.requireFollowUpEvidence(visitId, doctorId));
        verifyNoInteractions(examinations);
    }

    @Test void noClinicalRecordReturnsAbsentEvidenceWithoutLegacyTable() {
        var evidence = clinical.getEvidence(visitId);
        assertFalse(evidence.examinationExists()); assertFalse(evidence.examinationCompleted()); assertFalse(evidence.followUpIndicated());
        assertThrows(fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException.class, () -> clinical.getEvidence(null));
    }

    ScheduledCareCommand cancellation() {
        return new ScheduledCareCommand(appointmentId, visitId, patientId, doctorId, Instant.EPOCH.plusSeconds(1800),
                Instant.EPOCH, UUID.randomUUID(), 30, "evidence-repair");
    }

    @Test void realClinicalDraftPreventsCancellationBeforeAnyCloseOrRelease() {
        when(repository.lockScheduledVisit(visitId, appointmentId, patientId)).thenReturn(true);
        examination(ClinicalExaminationStatus.DRAFT, null);
        assertEquals("VISIT_CANCELLATION_NOT_ALLOWED", assertThrows(VisitWorkflowException.class, () -> visits.cancelCheckedInVisit(cancellation())).code());
        verify(repository, never()).closeCancellableVisit(any()); verify(repository, never()).releaseReservedCare(any(), any());
        var order = inOrder(repository, examinations); order.verify(repository).lockScheduledVisit(visitId, appointmentId, patientId);
        order.verify(examinations).findByVisitId(visitId);
    }

    @Test void visitWithoutClinicalRecordStillUsesExistingCancellableStateGuard() {
        when(repository.lockScheduledVisit(visitId, appointmentId, patientId)).thenReturn(true);
        when(repository.closeCancellableVisit(any())).thenReturn(true);
        visits.cancelCheckedInVisit(cancellation()); verify(repository).releaseReservedCare(visitId, Instant.EPOCH);
    }
}
