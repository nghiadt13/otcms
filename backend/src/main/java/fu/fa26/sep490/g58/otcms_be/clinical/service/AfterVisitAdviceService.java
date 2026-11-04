package fu.fa26.sep490.g58.otcms_be.clinical.service;

import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalExaminationStatus;
import java.time.Instant;
import java.util.UUID;

/** Records the visit-linked advice described by UC35 / BR-42. */
public interface AfterVisitAdviceService {
    record AdviceCommand(UUID visitId, UUID doctorId, String homeCareAdvice, String activityAdvice,
            String rehabilitationAdvice, String warningSigns, String followUpRecommendation) { }

    record AdviceView(UUID visitId, String homeCareAdvice, String activityAdvice,
            String rehabilitationAdvice, String warningSigns, String followUpRecommendation,
            ClinicalExaminationStatus examinationStatus, Instant updatedAt, Long version) { }

    AdviceView recordAdvice(AdviceCommand command);
}
