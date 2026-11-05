package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import fu.fa26.sep490.g58.otcms_be.clinical.model.*;
import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.impl.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class ClinicalHistoryOwnerQueryTest {
    final UUID patient = UUID.randomUUID(), visit = UUID.randomUUID();
    @Test void examinationReadsOnlyCompletedAndBatchesDiagnosisWithAdviceOnSameAggregate() {
        var exams = mock(ClinicalExaminationRepository.class); var diagnoses = mock(ClinicalDiagnosisRepository.class);
        var exam = ClinicalExaminationJpaEntity.create(visit, "reason", "symptoms", "findings", "knee", "RIGHT", null,
                ClinicalExaminationStatus.COMPLETED, Instant.EPOCH);
        exam.updateAfterVisitAdvice("rest", "activity", "rehab", "warning", "followup", Instant.EPOCH);
        when(exams.findByVisitIdInAndStatus(List.of(visit), ClinicalExaminationStatus.COMPLETED)).thenReturn(List.of(exam));
        when(diagnoses.findByExaminationIdIn(List.of(exam.getId()))).thenReturn(List.of(
                new ClinicalDiagnosisJpaEntity(exam.getId(), "S83", DiagnosisType.SECONDARY),
                new ClinicalDiagnosisJpaEntity(exam.getId(), "M17", DiagnosisType.PRIMARY)));
        var result = new ClinicalExaminationHistoryQueryServiceImpl(exams, diagnoses).findCompletedByVisitIds(List.of(visit));
        assertEquals("M17", result.get(visit).examination().diagnoses().getFirst().code());
        assertEquals("rest", result.get(visit).advice().homeCareAdvice());
        verify(exams).findByVisitIdInAndStatus(List.of(visit), ClinicalExaminationStatus.COMPLETED);
        verify(diagnoses).findByExaminationIdIn(List.of(exam.getId()));
        verifyNoMoreInteractions(exams, diagnoses);
    }
    @Test void noCompletedExaminationDoesNotExposeDraftOrQueryDiagnosis() {
        var exams = mock(ClinicalExaminationRepository.class); var diagnoses = mock(ClinicalDiagnosisRepository.class);
        assertTrue(new ClinicalExaminationHistoryQueryServiceImpl(exams, diagnoses).findCompletedByVisitIds(List.of(visit)).isEmpty());
        verifyNoInteractions(diagnoses);
    }
    @Test void emptyOwnerScopesAvoidAllPersistence() {
        var exams = mock(ClinicalExaminationRepository.class); var diagnoses = mock(ClinicalDiagnosisRepository.class);
        var rx = mock(ClinicalPrescriptionRepository.class); var items = mock(ClinicalPrescriptionItemRepository.class);
        var docs = mock(ClinicalMedicalDocumentRepository.class);
        assertTrue(new ClinicalExaminationHistoryQueryServiceImpl(exams, diagnoses).findCompletedByVisitIds(List.of()).isEmpty());
        assertTrue(new ClinicalPrescriptionHistoryQueryServiceImpl(rx, items).findIssuedByPatientAndVisitIds(patient, List.of()).isEmpty());
        assertTrue(new ClinicalMedicalDocumentHistoryQueryServiceImpl(docs).findReleased(patient, List.of()).isEmpty());
        verifyNoInteractions(exams, diagnoses, rx, items, docs);
    }
    @Test void prescriptionsBatchOnlyIssuedHeadersAndOrderedMedicineLines() {
        var rx = mock(ClinicalPrescriptionRepository.class); var items = mock(ClinicalPrescriptionItemRepository.class);
        var header = mock(ClinicalPrescriptionJpaEntity.class); UUID id = UUID.randomUUID();
        when(header.getId()).thenReturn(id); when(header.getVisitId()).thenReturn(visit); when(header.getPatientProfileId()).thenReturn(patient);
        when(header.getIssuedAt()).thenReturn(Instant.EPOCH);
        when(rx.findByPatientProfileIdAndVisitIdInAndStatusOrderByIssuedAtDescIdDesc(patient, List.of(visit), ClinicalPrescriptionStatus.ISSUED)).thenReturn(List.of(header));
        var line = mock(ClinicalPrescriptionItemJpaEntity.class); when(line.getPrescriptionId()).thenReturn(id); when(line.getLineNumber()).thenReturn(1);
        when(line.getMedicine()).thenReturn("medicine");
        when(items.findByPrescriptionIdInOrderByPrescriptionIdAscLineNumberAsc(List.of(id))).thenReturn(List.of(line));
        var result = new ClinicalPrescriptionHistoryQueryServiceImpl(rx, items).findIssuedByPatientAndVisitIds(patient, List.of(visit));
        assertEquals("medicine", result.getFirst().items().getFirst().medicine());
        verify(items, never()).findByPrescriptionIdOrderByLineNumberAsc(any());
        assertThrows(UnsupportedOperationException.class, () -> result.clear());
    }
    @Test void documentsQueryReleasedMetadataWithoutPayloadDependency() {
        var docs = mock(ClinicalMedicalDocumentRepository.class); var doc = mock(ClinicalMedicalDocumentJpaEntity.class);
        when(doc.getId()).thenReturn(UUID.randomUUID()); when(doc.getVisitId()).thenReturn(visit); when(doc.getPatientProfileId()).thenReturn(patient);
        when(doc.getDocumentType()).thenReturn(ClinicalMedicalDocumentType.CLINIC_EXAMINATION_REPORT);
        when(doc.getTitle()).thenReturn("report"); when(doc.getReleasedAt()).thenReturn(Instant.EPOCH);
        when(docs.findByPatientProfileIdAndVisitIdInAndReleasedAtIsNotNullOrderByCreatedAtDescIdDesc(patient, List.of(visit))).thenReturn(List.of(doc));
        var result = new ClinicalMedicalDocumentHistoryQueryServiceImpl(docs).findReleased(patient, List.of(visit));
        assertEquals("report", result.getFirst().title()); assertTrue(result.getFirst().released());
        assertTrue(Arrays.stream(result.getFirst().getClass().getRecordComponents()).noneMatch(c -> c.getType() == byte[].class));
    }
}
