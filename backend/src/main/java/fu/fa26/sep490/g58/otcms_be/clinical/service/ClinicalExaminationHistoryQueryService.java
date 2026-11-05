package fu.fa26.sep490.g58.otcms_be.clinical.service;

import fu.fa26.sep490.g58.otcms_be.clinical.model.DiagnosisType;
import java.util.*;

/** Trusted completed Clinical records; no actor authorization or mutable persistence crosses this boundary. */
public interface ClinicalExaminationHistoryQueryService {
    Map<UUID, CompletedRecord> findCompletedByVisitIds(Collection<UUID> visitIds);
    record Diagnosis(String code, DiagnosisType type) { }
    record Examination(String reasonForVisit, String symptoms, String examinationFindings, String injuryBodySite,
            String injurySide, String injuryMechanism, List<Diagnosis> diagnoses) {
        public Examination { diagnoses = List.copyOf(diagnoses); }
        @Override public String toString() { return "Examination[redacted]"; }
    }
    record Advice(String homeCareAdvice, String activityAdvice, String rehabilitationAdvice, String warningSigns,
            String followUpRecommendation, Boolean followUpIndicated) {
        @Override public String toString() { return "Advice[redacted]"; }
    }
    record CompletedRecord(UUID visitId, Examination examination, Advice advice) { }
}
