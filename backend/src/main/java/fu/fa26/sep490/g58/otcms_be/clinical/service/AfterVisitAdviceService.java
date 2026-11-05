package fu.fa26.sep490.g58.otcms_be.clinical.service;

import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalExaminationStatus;
import java.time.Instant;
import java.util.UUID;

/** Records the visit-linked advice described by UC35 / BR-42. */
public interface AfterVisitAdviceService {
    record AdviceCommand(UUID visitId, UUID doctorId, String homeCareAdvice, String activityAdvice,
            String rehabilitationAdvice, String warningSigns, String followUpRecommendation, Boolean followUpIndicated) {
        public AdviceCommand(UUID visitId, UUID doctorId, String homeCareAdvice, String activityAdvice,
                String rehabilitationAdvice, String warningSigns, String followUpRecommendation) {
            this(visitId, doctorId, homeCareAdvice, activityAdvice, rehabilitationAdvice, warningSigns, followUpRecommendation, null);
        }
    }

    record AdviceView(UUID visitId, String homeCareAdvice, String activityAdvice,
            String rehabilitationAdvice, String warningSigns, String followUpRecommendation,
            ClinicalExaminationStatus examinationStatus, Instant updatedAt, Long version, Boolean followUpIndicated) {
        public AdviceView(UUID visitId, String homeCareAdvice, String activityAdvice, String rehabilitationAdvice,
                String warningSigns, String followUpRecommendation, ClinicalExaminationStatus examinationStatus,
                Instant updatedAt, Long version) {
            this(visitId, homeCareAdvice, activityAdvice, rehabilitationAdvice, warningSigns, followUpRecommendation,
                    examinationStatus, updatedAt, version, null);
        }
    }

    AdviceView recordAdvice(AdviceCommand command);
    AdviceView getAdvice(UUID visitId, UUID doctorId);
}
