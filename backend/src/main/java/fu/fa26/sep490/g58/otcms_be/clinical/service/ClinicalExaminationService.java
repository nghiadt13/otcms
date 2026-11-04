package fu.fa26.sep490.g58.otcms_be.clinical.service;
import fu.fa26.sep490.g58.otcms_be.clinical.model.*; import java.util.*;
public interface ClinicalExaminationService {
 record Diagnosis(String code, DiagnosisType type) { }
 record ExaminationCommand(UUID visitId, UUID doctorId, String reasonForVisit, String symptoms, String examinationFindings, String injuryBodySite, String injurySide, String injuryMechanism, List<Diagnosis> diagnoses) { }
 record ExaminationView(UUID id, UUID visitId, String reasonForVisit, String symptoms, String examinationFindings, String injuryBodySite, String injurySide, String injuryMechanism, ClinicalExaminationStatus status, List<Diagnosis> diagnoses, Long version) { }
 ExaminationView saveDraft(ExaminationCommand command); ExaminationView completeExamination(ExaminationCommand command); ExaminationView getExamination(UUID visitId, UUID doctorId);
}
