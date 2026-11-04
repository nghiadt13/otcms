package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.model.*;
import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.impl.ClinicalExaminationServiceImpl;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClinicalExaminationServiceImplTest {
    private final VisitClinicalContextPort visits = mock(VisitClinicalContextPort.class);
    private final ClinicalExaminationRepository examinations = mock(ClinicalExaminationRepository.class);
    private final ClinicalDiagnosisRepository diagnoses = mock(ClinicalDiagnosisRepository.class);
    private final UUID visitId = UUID.randomUUID();
    private final UUID doctorId = UUID.randomUUID();
    private ClinicalExaminationJpaEntity savedExamination;
    private final ClinicalExaminationServiceImpl service = new ClinicalExaminationServiceImpl(
            visits, examinations, diagnoses, Clock.fixed(Instant.parse("2026-11-04T10:00:00Z"), ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        when(visits.getVisitContext(visitId)).thenReturn(new VisitClinicalContextPort.ClinicalVisitContext(
                visitId, UUID.randomUUID(), doctorId, ClinicalVisitStatus.IN_EXAMINATION));
        when(examinations.save(any())).thenAnswer(invocation -> {
            savedExamination = invocation.getArgument(0);
            return savedExamination;
        });
    }

    @Test
    void createsIncompleteDraftAndAllowsResponsibleDoctor() {
        var result = service.saveDraft(new ClinicalExaminationService.ExaminationCommand(
                visitId, doctorId, null, null, null, null, null, null, List.of()));
        assertEquals(ClinicalExaminationStatus.DRAFT, result.status());
        assertNull(result.reasonForVisit());
        verify(examinations).save(any());
    }

    @Test
    void updatingDraftUsesOneLogicalExaminationForVisit() {
        var first = service.saveDraft(command(null, List.of()));
        when(examinations.findByVisitId(visitId)).thenReturn(Optional.of(savedExamination));

        var second = service.saveDraft(new ClinicalExaminationService.ExaminationCommand(
                visitId, doctorId, "Pain", null, null, null, null, null, List.of()));

        assertEquals(first.id(), second.id());
        assertEquals("Pain", second.reasonForVisit());
        verify(examinations, times(2)).save(any());
    }

    @Test
    void unrelatedDoctorIsRejected() {
        var command = new ClinicalExaminationService.ExaminationCommand(
                visitId, UUID.randomUUID(), null, null, null, null, null, null, List.of());
        assertThrows(ClinicalOperationException.class, () -> service.saveDraft(command));
        verifyNoInteractions(examinations, diagnoses);
    }

    @Test
    void acceptsMAndSCodeAndMultipleSecondaryDiagnoses() {
        var result = service.completeExamination(command("M25.5", List.of(
                new ClinicalExaminationService.Diagnosis("M25.5", DiagnosisType.PRIMARY),
                new ClinicalExaminationService.Diagnosis("S83.5", DiagnosisType.SECONDARY),
                new ClinicalExaminationService.Diagnosis("M17", DiagnosisType.SECONDARY))));
        assertEquals(ClinicalExaminationStatus.COMPLETED, result.status());
        assertEquals(3, result.diagnoses().size());
        verify(diagnoses, times(3)).save(any());
    }

    @Test
    void rejectsNonMSCode() {
        assertThrows(ClinicalOperationException.class, () -> service.completeExamination(command("A01.1", List.of(
                new ClinicalExaminationService.Diagnosis("A01.1", DiagnosisType.PRIMARY)))));
    }

    @Test
    void completedExaminationRequiresExactlyOnePrimaryDiagnosis() {
        assertThrows(ClinicalOperationException.class, () -> service.completeExamination(command("", List.of())));
        assertThrows(ClinicalOperationException.class, () -> service.completeExamination(command("M25", List.of(
                new ClinicalExaminationService.Diagnosis("M25", DiagnosisType.PRIMARY),
                new ClinicalExaminationService.Diagnosis("S83", DiagnosisType.PRIMARY)))));
        assertThrows(ClinicalOperationException.class, () -> service.completeExamination(command("M25", List.of(
                new ClinicalExaminationService.Diagnosis("M25", DiagnosisType.SECONDARY)))));
    }

    @Test
    void completedExaminationRequiresClinicalFields() {
        var invalid = new ClinicalExaminationService.ExaminationCommand(
                visitId, doctorId, "Reason", "Symptoms", " ", null, null, null,
                List.of(new ClinicalExaminationService.Diagnosis("M25", DiagnosisType.PRIMARY)));
        assertThrows(ClinicalOperationException.class, () -> service.completeExamination(invalid));
    }

    @Test
    void draftAcceptsMissingDiagnosisAndClinicalFields() {
        var result = service.saveDraft(command(null, null));
        assertEquals(ClinicalExaminationStatus.DRAFT, result.status());
        assertTrue(result.diagnoses().isEmpty());
    }

    private ClinicalExaminationService.ExaminationCommand command(
            String reason, List<ClinicalExaminationService.Diagnosis> dx) {
        return new ClinicalExaminationService.ExaminationCommand(visitId, doctorId,
                reason == null ? "Reason" : reason, "Symptoms", "Findings", "Knee", "LEFT", "Twist", dx);
    }

}
