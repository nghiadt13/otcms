package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalExaminationStatus;
import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.ClinicalExaminationJpaEntity;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.ClinicalExaminationRepository;
import fu.fa26.sep490.g58.otcms_be.clinical.service.AfterVisitAdviceService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.VisitClinicalContextPort;
import fu.fa26.sep490.g58.otcms_be.clinical.service.impl.AfterVisitAdviceServiceImpl;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AfterVisitAdviceServiceImplTest {
    private final VisitClinicalContextPort visits = mock(VisitClinicalContextPort.class);
    private final ClinicalExaminationRepository examinations = mock(ClinicalExaminationRepository.class);
    private final UUID visitId = UUID.randomUUID();
    private final UUID doctorId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-11-04T10:00:00Z");
    private final AfterVisitAdviceServiceImpl service = new AfterVisitAdviceServiceImpl(visits, examinations,
            Clock.fixed(now, ZoneOffset.UTC));
    private ClinicalExaminationJpaEntity saved;

    @BeforeEach
    void setUp() {
        when(visits.getVisitContext(visitId)).thenReturn(new VisitClinicalContextPort.ClinicalVisitContext(
                visitId, UUID.randomUUID(), doctorId, true));
        when(examinations.save(any())).thenAnswer(invocation -> {
            saved = invocation.getArgument(0);
            return saved;
        });
    }

    @Test
    void responsibleDoctorRecordsAdviceOnCurrentExamination() {
        existingExamination(ClinicalExaminationStatus.DRAFT);
        var view = service.recordAdvice(command("Rest and ice", "Avoid impact", "Rehab exercise",
                "Seek care for worsening pain", "Review in two weeks"));
        assertEquals(visitId, view.visitId());
        assertEquals("Rest and ice", view.homeCareAdvice());
        assertEquals("Avoid impact", view.activityAdvice());
        assertEquals("Rehab exercise", view.rehabilitationAdvice());
        assertEquals("Seek care for worsening pain", view.warningSigns());
        assertEquals("Review in two weeks", view.followUpRecommendation());
        assertEquals(ClinicalExaminationStatus.DRAFT, view.examinationStatus());
        verify(examinations).save(any());
    }

    @Test
    void adviceWithoutExistingExaminationIsRejected() {
        when(examinations.findByVisitId(visitId)).thenReturn(Optional.empty());
        var error = assertThrows(ClinicalOperationException.class,
                () -> service.recordAdvice(command("Rest", null, null, null, null)));
        assertEquals("MSG-41", error.code());
        verify(examinations, never()).save(any());
    }

    @Test
    void unrelatedDoctorIsRejected() {
        var invalidActor = new AfterVisitAdviceService.AdviceCommand(visitId, UUID.randomUUID(),
                "Home care", null, null, null, null);
        assertThrows(ClinicalOperationException.class, () -> service.recordAdvice(invalidActor));
        verifyNoInteractions(examinations);
    }

    @Test
    void partialAdviceIsAcceptedWithoutInventingFieldRequiredness() {
        existingExamination(ClinicalExaminationStatus.DRAFT);
        var view = service.recordAdvice(command(null, "Avoid strenuous activity", null, null, null));
        assertNull(view.homeCareAdvice());
        assertEquals("Avoid strenuous activity", view.activityAdvice());
        assertNull(view.rehabilitationAdvice());
    }

    @Test
    void emptyAdviceIsRejected() {
        assertThrows(ClinicalOperationException.class,
                () -> service.recordAdvice(command(null, "  ", null, null, null)));
        verify(examinations, never()).save(any());
    }

    @Test
    void updatesSameVisitWithoutCreatingAnotherLogicalExamination() {
        existingExamination(ClinicalExaminationStatus.DRAFT);
        var first = service.recordAdvice(command("Use ice", null, null, null, null));
        var existing = saved;
        var existingId = saved.getId();
        when(examinations.findByVisitId(visitId)).thenReturn(Optional.of(saved));

        var updated = service.recordAdvice(command("Use ice", "Limit running", null, null, null));

        assertEquals(first.visitId(), updated.visitId());
        assertEquals(existingId, saved.getId());
        assertSame(existing, saved);
        assertEquals("Limit running", updated.activityAdvice());
        verify(examinations, times(2)).save(same(existing));
    }

    @Test
    void adviceUpdatePreservesCompletedExaminationState() {
        var completed = ClinicalExaminationJpaEntity.create(visitId, "Reason", "Symptoms", "Findings",
                "Knee", "LEFT", "Twist", ClinicalExaminationStatus.COMPLETED, now);
        when(examinations.findByVisitId(visitId)).thenReturn(Optional.of(completed));
        completed.updateAfterVisitAdvice("Old advice", null, null, null, null, now.minusSeconds(60));

        var view = service.recordAdvice(command("Rest", null, null, "Return for swelling", null));

        assertEquals(ClinicalExaminationStatus.COMPLETED, view.examinationStatus());
        assertEquals("Rest", view.homeCareAdvice());
        assertEquals("Return for swelling", view.warningSigns());
        assertSame(completed, saved);
        assertEquals("Reason", saved.getReasonForVisit());
        assertEquals("Symptoms", saved.getSymptoms());
        assertEquals("Findings", saved.getExaminationFindings());
        assertEquals("Knee", saved.getInjuryBodySite());
        assertEquals("LEFT", saved.getInjurySide());
        assertEquals("Twist", saved.getInjuryMechanism());
        assertEquals(now, view.updatedAt());
        verify(examinations).save(same(completed));
    }

    @Test
    void followUpRecommendationIsStoredAsAdviceOnly() {
        existingExamination(ClinicalExaminationStatus.DRAFT);
        var view = service.recordAdvice(command(null, null, null, null, "Review after two weeks"));
        assertEquals("Review after two weeks", view.followUpRecommendation());
        assertNull(view.homeCareAdvice());
    }

    @Test
    void missingVisitIsRejectedBeforeExaminationAccess() {
        when(visits.getVisitContext(visitId)).thenReturn(null);
        assertEquals("MSG-41", assertThrows(ClinicalOperationException.class,
                () -> service.recordAdvice(command("Rest", null, null, null, null))).code());
        verifyNoInteractions(examinations);
    }

    @Test
    void nullDoctorIsRejectedBeforeVisitAccess() {
        var invalid = new AfterVisitAdviceService.AdviceCommand(visitId, null, "Rest", null, null, null, null);
        assertEquals("MSG-40", assertThrows(ClinicalOperationException.class,
                () -> service.recordAdvice(invalid)).code());
        verifyNoInteractions(visits, examinations);
    }

    @Test
    void unassignedDoctorIsRejected() {
        when(visits.getVisitContext(visitId)).thenReturn(new VisitClinicalContextPort.ClinicalVisitContext(
                visitId, UUID.randomUUID(), null, false));
        assertEquals("MSG-40", assertThrows(ClinicalOperationException.class,
                () -> service.recordAdvice(command("Rest", null, null, null, null))).code());
        verifyNoInteractions(examinations);
    }

    @Test
    void allWhitespaceAdviceIsRejectedWithCurrentValidationMessage() {
        assertEquals("MSG-39", assertThrows(ClinicalOperationException.class,
                () -> service.recordAdvice(command("  ", "\t", "\n", "\r\n", " "))).code());
        verifyNoInteractions(examinations);
    }

    @Test
    void readExistingAdvicePreservesExaminationWithoutWrites() {
        var examination = ClinicalExaminationJpaEntity.create(visitId, "Reason", null, null, null, null,
                null, ClinicalExaminationStatus.COMPLETED, now);
        examination.updateAfterVisitAdvice("Rest", "Avoid impact", "Rehab", "Swelling", "Review", now);
        when(examinations.findByVisitId(visitId)).thenReturn(Optional.of(examination));
        var view = service.getAdvice(visitId, doctorId);
        assertEquals("Rest", view.homeCareAdvice());
        assertEquals("Avoid impact", view.activityAdvice());
        assertEquals("Rehab", view.rehabilitationAdvice());
        assertEquals("Swelling", view.warningSigns());
        assertEquals("Review", view.followUpRecommendation());
        assertEquals(ClinicalExaminationStatus.COMPLETED, view.examinationStatus());
        assertEquals(now, view.updatedAt());
        verify(examinations).findByVisitId(visitId);
        verifyNoMoreInteractions(examinations);
    }

    @Test
    void readExaminationWithoutAdviceReturnsEmptyFields() {
        existingExamination(ClinicalExaminationStatus.DRAFT);
        var view = service.getAdvice(visitId, doctorId);
        assertNull(view.homeCareAdvice());
        assertNull(view.followUpRecommendation());
        assertEquals(ClinicalExaminationStatus.DRAFT, view.examinationStatus());
        verify(examinations, never()).save(any());
    }

    @Test
    void readRequiresExistingExaminationAndVisit() {
        assertEquals("MSG-41", assertThrows(ClinicalOperationException.class,
                () -> service.getAdvice(visitId, doctorId)).code());
        when(visits.getVisitContext(visitId)).thenReturn(null);
        assertEquals("MSG-41", assertThrows(ClinicalOperationException.class,
                () -> service.getAdvice(visitId, doctorId)).code());
        verify(examinations, never()).save(any());
    }

    @Test
    void readRejectsWrongNullAndUnassignedDoctorsBeforePersistence() {
        assertEquals("MSG-40", assertThrows(ClinicalOperationException.class,
                () -> service.getAdvice(visitId, UUID.randomUUID())).code());
        assertEquals("MSG-40", assertThrows(ClinicalOperationException.class,
                () -> service.getAdvice(visitId, null)).code());
        when(visits.getVisitContext(visitId)).thenReturn(new VisitClinicalContextPort.ClinicalVisitContext(
                visitId, UUID.randomUUID(), null, false));
        assertEquals("MSG-40", assertThrows(ClinicalOperationException.class,
                () -> service.getAdvice(visitId, doctorId)).code());
        verifyNoInteractions(examinations);
    }

    @Test
    void readRejectsNullVisitAndIsDeclaredReadOnly() throws Exception {
        assertEquals("MSG-39", assertThrows(ClinicalOperationException.class,
                () -> service.getAdvice(null, doctorId)).code());
        verifyNoInteractions(visits, examinations);
        assertTrue(AfterVisitAdviceServiceImpl.class.getMethod("getAdvice", UUID.class, UUID.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class).readOnly());
    }

    @Test
    void writeRequiresVisitEligibilityButReadDoesNotGateOnWriteEligibility() {
        existingExamination(ClinicalExaminationStatus.COMPLETED);
        when(visits.getVisitContext(visitId)).thenReturn(new VisitClinicalContextPort.ClinicalVisitContext(
                visitId, UUID.randomUUID(), doctorId, false));
        assertEquals("MSG-42", assertThrows(ClinicalOperationException.class,
                () -> service.recordAdvice(command("Rest", null, null, null, null))).code());
        var view = service.getAdvice(visitId, doctorId);
        assertEquals(ClinicalExaminationStatus.COMPLETED, view.examinationStatus());
        verify(examinations, never()).save(any());
    }

    private AfterVisitAdviceService.AdviceCommand command(String home, String activity, String rehab,
            String warnings, String followUp) {
        return new AfterVisitAdviceService.AdviceCommand(visitId, doctorId, home, activity, rehab, warnings, followUp);
    }

    @Test void explicitFollowUpIndicationIsSavedReadAndPreservedWhenOmitted() {
        existingExamination(ClinicalExaminationStatus.COMPLETED);
        var view = service.recordAdvice(new AfterVisitAdviceService.AdviceCommand(visitId, doctorId,
                null, null, null, null, "Review in two weeks", true));
        assertEquals(true, view.followUpIndicated());
        assertEquals(true, service.getAdvice(visitId, doctorId).followUpIndicated());
        assertEquals(true, service.recordAdvice(command("Rest", null, null, null, null)).followUpIndicated());
        assertEquals(false, service.recordAdvice(new AfterVisitAdviceService.AdviceCommand(visitId, doctorId,
                "Rest", null, null, null, null, false)).followUpIndicated());
    }

    @Test void recommendationTextAloneDoesNotSetFollowUpBoolean() {
        existingExamination(ClinicalExaminationStatus.COMPLETED);
        assertNull(service.recordAdvice(command(null, null, null, null, "No follow-up required")).followUpIndicated());
    }

    private void existingExamination(ClinicalExaminationStatus status) {
        var examination = ClinicalExaminationJpaEntity.create(visitId, null, null, null,
                null, null, null, status, now);
        when(examinations.findByVisitId(visitId)).thenAnswer(invocation -> Optional.ofNullable(saved == null
                ? examination : saved));
    }
}
